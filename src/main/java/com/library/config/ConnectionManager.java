package com.library.config;

import com.library.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages a single JDBC {@link Connection} per thread using {@link ThreadLocal}.
 *
 * <h3>Purpose</h3>
 * <p>Ensures that within a single request-handling thread all DAO calls share
 * the same {@link Connection}, which is required for manual JDBC transaction
 * management at the Service layer. The standard usage pattern is:</p>
 * <pre>{@code
 *   connectionManager.beginTransaction();
 *   try {
 *       userDao.save(user);           // uses the same connection
 *       borrowDao.create(record);     // uses the same connection
 *       connectionManager.commit();
 *   } catch (Exception e) {
 *       connectionManager.rollback();
 *       throw e;
 *   } finally {
 *       connectionManager.releaseConnection();  // MUST be in finally
 *   }
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>{@link ThreadLocal} guarantees isolation between concurrent threads:
 * each HTTP request thread owns its own connection and cannot interfere
 * with another thread's transaction.</p>
 *
 * <h3>DAO Layer Contract</h3>
 * <p>DAO implementations call {@link #getConnection()} to obtain the current
 * thread's connection. DAOs must <em>never</em> call {@link #releaseConnection()}
 * — that responsibility belongs exclusively to the Service layer to preserve
 * transactional integrity.</p>
 *
 * <h3>How connection return works</h3>
 * <p>{@link #releaseConnection()} calls {@code connection.close()}, which triggers
 * {@link ConnectionPool.ProxyConnection#close()} — that method resets
 * connection state and moves the proxy back to the pool's {@code availableConnections}
 * queue. No direct call to any pool method is made here.</p>
 */
@Component
public class ConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private final ConnectionPool pool;

    /** Stores one {@link ConnectionPool.ProxyConnection} per thread. */
    private final ThreadLocal<Connection> threadLocalConnection = new ThreadLocal<>();

    public ConnectionManager(ConnectionPool pool) {
        this.pool = pool;
    }

    // -------------------------------------------------------------------------
    // Connection management
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link Connection} bound to the current thread.
     * If no connection is bound yet, acquires one from {@link CustomConnectionPool}
     * and stores it in the {@link ThreadLocal}.
     *
     * @return the thread-local {@link CustomConnectionPool.ProxyConnection} (never {@code null}).
     * @throws DatabaseException if the pool cannot supply a connection (exhausted or closed).
     */
    public Connection getConnection() {
        Connection connection = threadLocalConnection.get();
        if (connection == null) {
            connection = pool.getConnection();   // returns a ProxyConnection
            threadLocalConnection.set(connection);
            log.debug("Connection bound to thread [{}].", Thread.currentThread().getName());
        }
        return connection;
    }

    /**
     * Releases the current thread's connection back to the pool and unbinds it
     * from the {@link ThreadLocal}.
     *
     * <p>Calling {@code connection.close()} delegates to
     * {@link CustomConnectionPool.ProxyConnection#close()}, which resets connection
     * state (autoCommit, readOnly) and returns the proxy to the pool's available queue.</p>
     *
     * <p>Safe to call even if no connection is currently bound (no-op in that case).
     * <b>Always call this in a {@code finally} block</b> to prevent connection leaks.</p>
     */
    public void releaseConnection() {
        Connection connection = threadLocalConnection.get();
        if (connection != null) {
            try {
                // ProxyConnection.close() → resets state, returns to availableConnections
                connection.close();
                log.debug("Connection released from thread [{}].",
                        Thread.currentThread().getName());
            } catch (SQLException e) {
                log.error("Error while releasing connection on thread [{}].",
                        Thread.currentThread().getName(), e);
            } finally {
                // Always unbind — even if close() threw, we don't want a stale reference
                threadLocalConnection.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Transaction helpers
    // -------------------------------------------------------------------------

    /**
     * Starts a manual JDBC transaction on the current thread's connection by
     * disabling auto-commit mode.
     *
     * <p>If acquiring a connection succeeds but {@code setAutoCommit(false)} fails,
     * the connection is immediately released back to the pool to prevent leaks.</p>
     *
     * @throws DatabaseException if a connection cannot be obtained, or if
     *                           switching to manual commit mode fails.
     */
    public void beginTransaction() {
        try {
            getConnection().setAutoCommit(false);
            log.debug("Transaction begun on thread [{}].", Thread.currentThread().getName());
        } catch (SQLException e) {
            // Connection was acquired by getConnection() but transaction setup failed.
            // Release it immediately to avoid orphaning the connection in the ThreadLocal.
            releaseConnection();
            throw new DatabaseException("Failed to begin transaction.", e);
        }
    }

    /**
     * Commits the current thread's transaction.
     *
     * @throws DatabaseException if there is no active connection, or the commit fails.
     */
    public void commit() {
        Connection connection = threadLocalConnection.get();
        if (connection == null) {
            throw new DatabaseException(
                    "Cannot commit: no active connection on thread ["
                            + Thread.currentThread().getName() + "].");
        }
        try {
            connection.commit();
            log.debug("Transaction committed on thread [{}].", Thread.currentThread().getName());
        } catch (SQLException e) {
            throw new DatabaseException("Failed to commit transaction.", e);
        }
    }

    /**
     * Rolls back the current thread's transaction.
     *
     * <p>Safe to call even if no active connection exists — logs a warning
     * and returns without throwing.</p>
     *
     * @throws DatabaseException if the rollback itself fails at the JDBC level.
     */
    public void rollback() {
        Connection connection = threadLocalConnection.get();
        if (connection == null) {
            log.warn("Rollback requested but no active connection on thread [{}].",
                    Thread.currentThread().getName());
            return;
        }
        try {
            connection.rollback();
            log.debug("Transaction rolled back on thread [{}].",
                    Thread.currentThread().getName());
        } catch (SQLException e) {
            log.error("Failed to rollback transaction on thread [{}].",
                    Thread.currentThread().getName(), e);
            throw new DatabaseException("Failed to rollback transaction.", e);
        }
    }
}
