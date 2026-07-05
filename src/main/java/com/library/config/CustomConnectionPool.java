package com.library.config;

import com.library.exception.DatabaseException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
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
 * <p>Uses an {@link ArrayBlockingQueue} as the backing store for pooled connections.
 * The pool is initialized via {@link #init()} on application startup and destroyed
 * via {@link #destroy()} on shutdown.</p>
 *
 * <h3>Proxy Pattern</h3>
 * <p>Each physical {@link Connection} is wrapped in a {@link ProxyConnection} before
 * being placed in the pool. When {@code ProxyConnection.close()} is called, instead of
 * physically closing the underlying connection, it returns the proxy back to the pool.
 * All other {@link Connection} methods are transparently delegated to the real connection.</p>
 *
 * <h3>Configuration</h3>
 * <p>Reads the following properties (injected by Spring via {@code @Value}):</p>
 * <ul>
 *   <li>{@code db.url}      — JDBC URL</li>
 *   <li>{@code db.username} — database user</li>
 *   <li>{@code db.password} — database password</li>
 *   <li>{@code db.driver}   — driver class name</li>
 *   <li>{@code db.pool.size}— number of connections (default: 10)</li>
 *   <li>{@code db.pool.timeout.ms} — max wait time in ms (default: 5000)</li>
 * </ul>
 *
 * <p><b>STRICT PROHIBITION:</b> No HikariCP, c3p0, Tomcat JDBC or any external pool
 * library is used anywhere in this class.</p>
 */
@Component
public class CustomConnectionPool {

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

    /** The pool — holds available (idle) ProxyConnection instances. */
    private BlockingQueue<Connection> pool;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialises the pool after Spring injects all {@code @Value} properties.
     * Loads the JDBC driver, creates {@code poolSize} physical connections, wraps
     * each in a {@link ProxyConnection}, and enqueues them.
     *
     * @throws DatabaseException if the driver cannot be loaded or a connection fails.
     */
    @PostConstruct
    public void init() {
        log.info("Initialising CustomConnectionPool: size={}, url={}", poolSize, url);
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new DatabaseException("JDBC driver not found: " + driverClassName, e);
        }

        pool = new ArrayBlockingQueue<>(poolSize);

        for (int i = 0; i < poolSize; i++) {
            try {
                Connection realConnection = DriverManager.getConnection(url, username, password);
                pool.offer(new ProxyConnection(realConnection));
                log.debug("Created physical connection #{}", i + 1);
            } catch (SQLException e) {
                throw new DatabaseException(
                        "Failed to create connection #" + (i + 1) + " during pool init", e);
            }
        }
        log.info("CustomConnectionPool initialised successfully with {} connections.", poolSize);
    }

    /**
     * Physically closes all connections in the pool when the application shuts down.
     * Must only be called once — after all active transactions have completed.
     */
    @PreDestroy
    public void destroy() {
        log.info("Shutting down CustomConnectionPool — closing {} connections.", pool.size());
        for (Connection connection : pool) {
            if (connection instanceof ProxyConnection proxy) {
                try {
                    proxy.realClose(); // close the real underlying connection
                    log.debug("Physical connection closed successfully.");
                } catch (SQLException e) {
                    log.warn("Error closing physical connection during shutdown.", e);
                }
            }
        }
        pool.clear();
        log.info("CustomConnectionPool shut down.");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Retrieves a {@link Connection} (actually a {@link ProxyConnection}) from the pool.
     * Blocks for at most {@code db.pool.timeout.ms} milliseconds.
     *
     * @return a pooled proxy connection ready for use.
     * @throws DatabaseException if no connection becomes available within the timeout.
     */
    public Connection getConnection() {
        try {
            Connection connection = pool.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (connection == null) {
                throw new DatabaseException(
                        "Connection pool exhausted: no connection available within "
                                + timeoutMs + " ms. Pool size=" + poolSize);
            }
            log.debug("Connection acquired from pool. Remaining idle: {}", pool.size());
            return connection;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("Thread interrupted while waiting for a connection.", e);
        }
    }

    /**
     * Returns a {@link Connection} back to the pool.
     * Called automatically by {@link ProxyConnection#close()}.
     *
     * @param connection the proxy connection to return.
     */
    void releaseConnection(Connection connection) {
        if (connection != null) {
            boolean offered = pool.offer(connection);
            if (offered) {
                log.debug("Connection returned to pool. Idle connections: {}", pool.size());
            } else {
                // Pool is full — this should never happen in normal operation.
                log.warn("Pool is full — could not return connection. Closing it physically.");
                if (connection instanceof ProxyConnection proxy) {
                    try {
                        proxy.realClose();
                    } catch (SQLException e) {
                        log.error("Failed to close surplus connection.", e);
                    }
                }
            }
        }
    }

    /**
     * Returns the number of currently idle connections in the pool.
     * Useful for health checks and monitoring.
     */
    public int getIdleConnectionCount() {
        return pool.size();
    }

    /**
     * Returns the total capacity (maximum size) of this pool.
     */
    public int getPoolSize() {
        return poolSize;
    }

    // =========================================================================
    // INNER CLASS — ProxyConnection (Proxy Pattern)
    // =========================================================================

    /**
     * A decorator / proxy around a real {@link Connection}.
     *
     * <p>The only overridden behaviour is {@link #close()}: instead of physically
     * closing the underlying connection, it returns {@code this} proxy back to
     * {@link CustomConnectionPool#releaseConnection(Connection)}.</p>
     *
     * <p>Every other method is a transparent delegation to the wrapped real connection.</p>
     */
    final class ProxyConnection implements Connection {

        /** The actual physical database connection. */
        private final Connection realConnection;

        ProxyConnection(Connection realConnection) {
            this.realConnection = realConnection;
        }

        /**
         * Returns this proxy to the pool instead of physically closing the connection.
         * This is the core of the Proxy Pattern applied here.
         */
        @Override
        public void close() {
            // Reset state before returning to pool to avoid polluting next borrower.
            try {
                if (!realConnection.getAutoCommit()) {
                    realConnection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                log.warn("Could not reset autoCommit before returning connection to pool.", e);
            }
            releaseConnection(this);
        }

        /**
         * Physically closes the underlying connection.
         * Used exclusively by {@link CustomConnectionPool#destroy()}.
         */
        void realClose() throws SQLException {
            realConnection.close();
        }

        // ------------------------------------------------------------------
        // Delegating methods — all forward to realConnection
        // ------------------------------------------------------------------

        @Override
        public Statement createStatement() throws SQLException {
            return realConnection.createStatement();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return realConnection.prepareStatement(sql);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            return realConnection.prepareCall(sql);
        }

        @Override
        public String nativeSQL(String sql) throws SQLException {
            return realConnection.nativeSQL(sql);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            realConnection.setAutoCommit(autoCommit);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            return realConnection.getAutoCommit();
        }

        @Override
        public void commit() throws SQLException {
            realConnection.commit();
        }

        @Override
        public void rollback() throws SQLException {
            realConnection.rollback();
        }

        @Override
        public boolean isClosed() throws SQLException {
            return realConnection.isClosed();
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            return realConnection.getMetaData();
        }

        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            realConnection.setReadOnly(readOnly);
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            return realConnection.isReadOnly();
        }

        @Override
        public void setCatalog(String catalog) throws SQLException {
            realConnection.setCatalog(catalog);
        }

        @Override
        public String getCatalog() throws SQLException {
            return realConnection.getCatalog();
        }

        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            realConnection.setTransactionIsolation(level);
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            return realConnection.getTransactionIsolation();
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            return realConnection.getWarnings();
        }

        @Override
        public void clearWarnings() throws SQLException {
            realConnection.clearWarnings();
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency)
                throws SQLException {
            return realConnection.createStatement(resultSetType, resultSetConcurrency);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType,
                int resultSetConcurrency) throws SQLException {
            return realConnection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType,
                int resultSetConcurrency) throws SQLException {
            return realConnection.prepareCall(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            return realConnection.getTypeMap();
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
            realConnection.setTypeMap(map);
        }

        @Override
        public void setHoldability(int holdability) throws SQLException {
            realConnection.setHoldability(holdability);
        }

        @Override
        public int getHoldability() throws SQLException {
            return realConnection.getHoldability();
        }

        @Override
        public Savepoint setSavepoint() throws SQLException {
            return realConnection.setSavepoint();
        }

        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            return realConnection.setSavepoint(name);
        }

        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            realConnection.rollback(savepoint);
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            realConnection.releaseSavepoint(savepoint);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency,
                int resultSetHoldability) throws SQLException {
            return realConnection.createStatement(
                    resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType,
                int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return realConnection.prepareStatement(
                    sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType,
                int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return realConnection.prepareCall(
                    sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
                throws SQLException {
            return realConnection.prepareStatement(sql, autoGeneratedKeys);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
                throws SQLException {
            return realConnection.prepareStatement(sql, columnIndexes);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames)
                throws SQLException {
            return realConnection.prepareStatement(sql, columnNames);
        }

        @Override
        public Clob createClob() throws SQLException {
            return realConnection.createClob();
        }

        @Override
        public Blob createBlob() throws SQLException {
            return realConnection.createBlob();
        }

        @Override
        public NClob createNClob() throws SQLException {
            return realConnection.createNClob();
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            return realConnection.createSQLXML();
        }

        @Override
        public boolean isValid(int timeout) throws SQLException {
            return realConnection.isValid(timeout);
        }

        @Override
        public void setClientInfo(String name, String value) throws SQLClientInfoException {
            try {
                realConnection.setClientInfo(name, value);
            } catch (SQLClientInfoException e) {
                throw e;
            }
        }

        @Override
        public void setClientInfo(Properties properties) throws SQLClientInfoException {
            try {
                realConnection.setClientInfo(properties);
            } catch (SQLClientInfoException e) {
                throw e;
            }
        }

        @Override
        public String getClientInfo(String name) throws SQLException {
            return realConnection.getClientInfo(name);
        }

        @Override
        public Properties getClientInfo() throws SQLException {
            return realConnection.getClientInfo();
        }

        @Override
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            return realConnection.createArrayOf(typeName, elements);
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            return realConnection.createStruct(typeName, attributes);
        }

        @Override
        public void setSchema(String schema) throws SQLException {
            realConnection.setSchema(schema);
        }

        @Override
        public String getSchema() throws SQLException {
            return realConnection.getSchema();
        }

        @Override
        public void abort(Executor executor) throws SQLException {
            realConnection.abort(executor);
        }

        @Override
        public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
            realConnection.setNetworkTimeout(executor, milliseconds);
        }

        @Override
        public int getNetworkTimeout() throws SQLException {
            return realConnection.getNetworkTimeout();
        }

        // Wrapper interface
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isAssignableFrom(getClass())) {
                return iface.cast(this);
            }
            return realConnection.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isAssignableFrom(getClass()) || realConnection.isWrapperFor(iface);
        }
    }
}
