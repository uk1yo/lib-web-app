package com.library.config;

import com.library.exception.DatabaseException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe custom JDBC connection pool implemented from scratch.
 *
 * <h3>Architecture</h3>
 * <p>Uses two {@link ArrayBlockingQueue} instances:</p>
 * <ul>
 *   <li>{@code availableConnections} — idle connections ready to be borrowed.</li>
 *   <li>{@code usedConnections}      — actively borrowed connections;
 *       enables leak detection and graceful shutdown of in-flight transactions.</li>
 * </ul>
 *
 * <h3>Proxy Pattern</h3>
 * <p>Every physical {@link Connection} is wrapped in a {@link ProxyConnection}.
 * Calling {@link ProxyConnection#close()} does <em>not</em> physically close the
 * connection — instead it resets connection state and returns the proxy to
 * {@code availableConnections}. Physical closing happens only in {@link #destroy()}.</p>
 *
 * <h3>Configuration (via {@code db.properties})</h3>
 * <ul>
 *   <li>{@code db.url}              — JDBC URL</li>
 *   <li>{@code db.username}         — database user</li>
 *   <li>{@code db.password}         — database password</li>
 *   <li>{@code db.driver}           — fully-qualified driver class name</li>
 *   <li>{@code db.pool.size}        — pool capacity (default: 10, minimum: 1)</li>
 *   <li>{@code db.pool.timeout.ms}  — max wait in ms before {@link DatabaseException} (default: 5000)</li>
 * </ul>
 *
 * <p><b>STRICT PROHIBITION:</b> No HikariCP, c3p0, Tomcat JDBC or any third-party
 * connection pool is used. This implementation is built entirely from scratch
 * per project constraints.</p>
 */
@Component
public class CustomConnectionPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CustomConnectionPool.class);

    @Value("${db.url}")
    private String url;

    @Value("${db.username}")
    private String username;

    @Value("${db.password}")
    private String password;

    @Value("${db.driver}")
    private String driverClassName;

    @Value("${db.pool.size:10}")
    private int poolSize;

    @Value("${db.pool.timeout.ms:5000}")
    private long timeoutMs;

    /** Connections ready to be borrowed. */
    private BlockingQueue<Connection> availableConnections;

    /**
     * Connections currently in use.
     * Tracked separately to enable leak detection and safe shutdown of in-flight work.
     */
    private BlockingQueue<Connection> usedConnections;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Initialises the pool after Spring has injected all {@code @Value} properties.
     * Validates configuration, loads the JDBC driver, creates {@code poolSize}
     * physical connections, wraps each in a {@link ProxyConnection}, and enqueues them.
     *
     * @throws DatabaseException if poolSize &lt; 1, the driver is missing,
     *                           or any physical connection cannot be established.
     */
    @PostConstruct
    public void init() {
        if (poolSize < 1) {
            throw new DatabaseException("db.pool.size must be >= 1, got: " + poolSize);
        }

        log.info("Initialising CustomConnectionPool — size={}, url={}", poolSize, url);

        try {
            Class.forName(driverClassName);
            log.debug("JDBC driver loaded: {}", driverClassName);
        } catch (ClassNotFoundException e) {
            throw new DatabaseException("JDBC driver not found: " + driverClassName, e);
        }

        availableConnections = new ArrayBlockingQueue<>(poolSize);
        usedConnections      = new ArrayBlockingQueue<>(poolSize);

        for (int i = 0; i < poolSize; i++) {
            try {
                Connection physical = DriverManager.getConnection(url, username, password);
                availableConnections.add(new ProxyConnection(physical));
                log.debug("Physical connection #{} created.", i + 1);
            } catch (SQLException e) {
                throw new DatabaseException(
                        "Failed to open connection #" + (i + 1) + " during pool init.", e);
            }
        }
        log.info("CustomConnectionPool ready — {} connections available.", poolSize);
    }

    /**
     * Gracefully shuts down the pool on application exit.
     *
     * <p>Processes both queues:</p>
     * <ol>
     *   <li>Rolls back any uncommitted transaction in the <em>used</em> queue
     *       (leaked connections) before closing physically — avoids silently committing
     *       partial work.</li>
     *   <li>Closes all idle connections in the <em>available</em> queue.</li>
     * </ol>
     */
    @Override
    @PreDestroy
    public synchronized void close() {
        log.info("Shutting down CustomConnectionPool — draining used={}, available={}.",
                usedConnections.size(), availableConnections.size());
        drainAndClose(usedConnections,   "used (leaked)");
        drainAndClose(availableConnections, "available");
        log.info("CustomConnectionPool shut down.");
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Borrows a {@link Connection} (actually a {@link ProxyConnection}) from the pool.
     * Blocks for at most {@code db.pool.timeout.ms} milliseconds.
     *
     * @return a pooled proxy connection ready for use.
     * @throws DatabaseException if no connection becomes available within the timeout,
     *                           or the waiting thread is interrupted.
     */
    public Connection getConnection() {
        try {
            Connection proxy = availableConnections.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (proxy == null) {
                throw new DatabaseException(
                        "Connection pool exhausted — no connection available within "
                                + timeoutMs + " ms. Pool size=" + poolSize
                                + ", in-use=" + usedConnections.size());
            }
            usedConnections.add(proxy);
            log.debug("Connection acquired — available={}, used={}.",
                    availableConnections.size(), usedConnections.size());
            return proxy;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException(
                    "Thread interrupted while waiting for a pool connection.", e);
        }
    }

    /**
     * Returns the number of currently idle connections.
     * Useful for health checks and monitoring endpoints.
     */
    public int getAvailableCount() {
        return availableConnections.size();
    }

    /**
     * Returns the number of connections currently borrowed by application threads.
     */
    public int getUsedCount() {
        return usedConnections.size();
    }

    /**
     * Returns the total pool capacity.
     */
    public int getPoolSize() {
        return poolSize;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Physically closes all connections in the given queue.
     * For each connection, rolls back any uncommitted transaction before closing.
     *
     * @param queue the queue to drain.
     * @param label a human-readable label used in log messages.
     */
    private void drainAndClose(BlockingQueue<Connection> queue, String label) {
        Connection conn;
        while ((conn = queue.poll()) != null) {
            if (conn instanceof ProxyConnection proxy) {
                try {
                    Connection inner = proxy.getInnerConnection();
                    // Safety: rollback any uncommitted work before physical close.
                    if (!inner.isClosed() && !inner.getAutoCommit()) {
                        inner.rollback();
                        log.debug("Rolled back uncommitted transaction in {} connection.", label);
                    }
                    inner.close();
                    log.debug("Physically closed {} connection.", label);
                } catch (SQLException e) {
                    log.warn("Error while closing {} connection during shutdown.", label, e);
                }
            }
        }
    }

    // =========================================================================
    // INNER CLASS — ProxyConnection (Proxy / Wrapper Pattern)
    // =========================================================================

    /**
     * A full decorator around a physical {@link Connection}.
     *
     * <h3>Key Behaviour</h3>
     * <ul>
     *   <li>{@link #close()} — does <b>not</b> close the physical connection;
     *       instead resets connection state and returns this proxy to the pool.</li>
     *   <li>All other methods — transparently delegate to the wrapped connection.</li>
     *   <li>{@link #getInnerConnection()} — exposes the physical connection
     *       exclusively for use by {@link CustomConnectionPool#drainAndClose}.</li>
     * </ul>
     */
    final class ProxyConnection implements Connection {

        private final Connection innerConnection;

        /**
         * Wraps a physical connection and sets a clean initial state:
         * {@code autoCommit=true} so the connection is immediately usable
         * without an explicit {@code BEGIN} transaction.
         */
        ProxyConnection(Connection connection) throws SQLException {
            this.innerConnection = connection;
            innerConnection.setAutoCommit(true);
        }

        /**
         * Provides access to the underlying physical connection.
         * <b>Must only be called by {@link CustomConnectionPool#drainAndClose}.</b>
         */
        Connection getInnerConnection() {
            return innerConnection;
        }

        /**
         * Returns this proxy to the pool instead of physically closing the connection.
         *
         * <p>Before returning, the following state is reset to prevent pollution
         * of the next borrower's session:</p>
         * <ol>
         *   <li>Verifies the connection is not already closed.</li>
         *   <li>Resets {@code readOnly} to {@code false}.</li>
         *   <li>Resets {@code autoCommit} to {@code true}.</li>
         *   <li>Removes from {@code usedConnections} (leak-detection check).</li>
         *   <li>Offers back to {@code availableConnections}.</li>
         * </ol>
         *
         * @throws SQLException if the connection is already closed, or if queue
         *                       operations fail (which would indicate a programming error).
         */
        @Override
        public synchronized void close() throws SQLException {
            if (innerConnection.isClosed()) {
                throw new SQLException(
                        "Attempted to return an already-closed connection to the pool.");
            }

            // Reset read-only mode
            if (innerConnection.isReadOnly()) {
                innerConnection.setReadOnly(false);
            }

            // Reset auto-commit (caller may have started a manual transaction)
            if (!innerConnection.getAutoCommit()) {
                innerConnection.setAutoCommit(true);
            }

            // Leak-detection: connection must be in the usedConnections queue
            if (!usedConnections.remove(this)) {
                throw new SQLException(
                        "Cannot return connection to pool: it was not found in usedConnections. "
                        + "Possible double-close or connection leak.");
            }

            if (!availableConnections.offer(this)) {
                throw new SQLException(
                        "Cannot return connection to pool: availableConnections queue is full. "
                        + "This should never happen — pool invariant violated.");
            }

            log.debug("Connection returned to pool — available={}, used={}.",
                    availableConnections.size(), usedConnections.size());
        }

        // ------------------------------------------------------------------
        // Delegating methods — all forward to innerConnection
        // ------------------------------------------------------------------

        @Override public Statement createStatement() throws SQLException {
            return innerConnection.createStatement();
        }
        @Override public PreparedStatement prepareStatement(String sql) throws SQLException {
            return innerConnection.prepareStatement(sql);
        }
        @Override public CallableStatement prepareCall(String sql) throws SQLException {
            return innerConnection.prepareCall(sql);
        }
        @Override public String nativeSQL(String sql) throws SQLException {
            return innerConnection.nativeSQL(sql);
        }
        @Override public void setAutoCommit(boolean autoCommit) throws SQLException {
            innerConnection.setAutoCommit(autoCommit);
        }
        @Override public boolean getAutoCommit() throws SQLException {
            return innerConnection.getAutoCommit();
        }
        @Override public void commit() throws SQLException {
            innerConnection.commit();
        }
        @Override public void rollback() throws SQLException {
            innerConnection.rollback();
        }
        @Override public boolean isClosed() throws SQLException {
            return innerConnection.isClosed();
        }
        @Override public DatabaseMetaData getMetaData() throws SQLException {
            return innerConnection.getMetaData();
        }
        @Override public void setReadOnly(boolean readOnly) throws SQLException {
            innerConnection.setReadOnly(readOnly);
        }
        @Override public boolean isReadOnly() throws SQLException {
            return innerConnection.isReadOnly();
        }
        @Override public void setCatalog(String catalog) throws SQLException {
            innerConnection.setCatalog(catalog);
        }
        @Override public String getCatalog() throws SQLException {
            return innerConnection.getCatalog();
        }
        @Override public void setTransactionIsolation(int level) throws SQLException {
            innerConnection.setTransactionIsolation(level);
        }
        @Override public int getTransactionIsolation() throws SQLException {
            return innerConnection.getTransactionIsolation();
        }
        @Override public SQLWarning getWarnings() throws SQLException {
            return innerConnection.getWarnings();
        }
        @Override public void clearWarnings() throws SQLException {
            innerConnection.clearWarnings();
        }
        @Override public Statement createStatement(int resultSetType, int resultSetConcurrency)
                throws SQLException {
            return innerConnection.createStatement(resultSetType, resultSetConcurrency);
        }
        @Override public PreparedStatement prepareStatement(String sql, int resultSetType,
                int resultSetConcurrency) throws SQLException {
            return innerConnection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }
        @Override public CallableStatement prepareCall(String sql, int resultSetType,
                int resultSetConcurrency) throws SQLException {
            return innerConnection.prepareCall(sql, resultSetType, resultSetConcurrency);
        }
        @Override public Map<String, Class<?>> getTypeMap() throws SQLException {
            return innerConnection.getTypeMap();
        }
        @Override public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
            innerConnection.setTypeMap(map);
        }
        @Override public void setHoldability(int holdability) throws SQLException {
            innerConnection.setHoldability(holdability);
        }
        @Override public int getHoldability() throws SQLException {
            return innerConnection.getHoldability();
        }
        @Override public Savepoint setSavepoint() throws SQLException {
            return innerConnection.setSavepoint();
        }
        @Override public Savepoint setSavepoint(String name) throws SQLException {
            return innerConnection.setSavepoint(name);
        }
        @Override public void rollback(Savepoint savepoint) throws SQLException {
            innerConnection.rollback(savepoint);
        }
        @Override public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            innerConnection.releaseSavepoint(savepoint);
        }
        @Override public Statement createStatement(int resultSetType, int resultSetConcurrency,
                int resultSetHoldability) throws SQLException {
            return innerConnection.createStatement(
                    resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        @Override public PreparedStatement prepareStatement(String sql, int resultSetType,
                int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return innerConnection.prepareStatement(
                    sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        @Override public CallableStatement prepareCall(String sql, int resultSetType,
                int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return innerConnection.prepareCall(
                    sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        @Override public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
                throws SQLException {
            return innerConnection.prepareStatement(sql, autoGeneratedKeys);
        }
        @Override public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
                throws SQLException {
            return innerConnection.prepareStatement(sql, columnIndexes);
        }
        @Override public PreparedStatement prepareStatement(String sql, String[] columnNames)
                throws SQLException {
            return innerConnection.prepareStatement(sql, columnNames);
        }
        @Override public Clob createClob() throws SQLException {
            return innerConnection.createClob();
        }
        @Override public Blob createBlob() throws SQLException {
            return innerConnection.createBlob();
        }
        @Override public NClob createNClob() throws SQLException {
            return innerConnection.createNClob();
        }
        @Override public SQLXML createSQLXML() throws SQLException {
            return innerConnection.createSQLXML();
        }
        @Override public boolean isValid(int timeout) throws SQLException {
            return innerConnection.isValid(timeout);
        }
        @Override public void setClientInfo(String name, String value)
                throws SQLClientInfoException {
            innerConnection.setClientInfo(name, value);
        }
        @Override public void setClientInfo(Properties properties)
                throws SQLClientInfoException {
            innerConnection.setClientInfo(properties);
        }
        @Override public String getClientInfo(String name) throws SQLException {
            return innerConnection.getClientInfo(name);
        }
        @Override public Properties getClientInfo() throws SQLException {
            return innerConnection.getClientInfo();
        }
        @Override public Array createArrayOf(String typeName, Object[] elements)
                throws SQLException {
            return innerConnection.createArrayOf(typeName, elements);
        }
        @Override public Struct createStruct(String typeName, Object[] attributes)
                throws SQLException {
            return innerConnection.createStruct(typeName, attributes);
        }
        @Override public void setSchema(String schema) throws SQLException {
            innerConnection.setSchema(schema);
        }
        @Override public String getSchema() throws SQLException {
            return innerConnection.getSchema();
        }
        @Override public void abort(Executor executor) throws SQLException {
            innerConnection.abort(executor);
        }
        @Override public void setNetworkTimeout(Executor executor, int milliseconds)
                throws SQLException {
            innerConnection.setNetworkTimeout(executor, milliseconds);
        }
        @Override public int getNetworkTimeout() throws SQLException {
            return innerConnection.getNetworkTimeout();
        }

        // Wrapper interface
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isAssignableFrom(getClass())) {
                return iface.cast(this);
            }
            return innerConnection.unwrap(iface);
        }
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isAssignableFrom(getClass()) || innerConnection.isWrapperFor(iface);
        }
    }
}
