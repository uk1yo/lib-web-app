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
 * management at the Service layer:</p>
 * <pre>{@code
 *   ConnectionManager.beginTransaction();
 *   try {
 *       userDao.save(user);          // uses the same connection
 *       borrowDao.create(record);    // uses the same connection
 *       ConnectionManager.commit();
 *   } catch (Exception e) {
 *       ConnectionManager.rollback();
 *       throw e;
 *   } finally {
 *       ConnectionManager.releaseConnection();
 *   }
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>{@link ThreadLocal} guarantees isolation between threads: each HTTP request
 * (thread) gets its own connection and cannot interfere with another thread's
 * transaction.</p>
 *
 * <h3>Usage in DAO layer</h3>
 * <p>DAO implementations should call {@link #getConnection()} to obtain the
 * current thread's connection. They must <em>never</em> call
 * {@link #releaseConnection()} themselves — that responsibility belongs exclusively
 * to the Service layer to preserve transactional integrity.</p>
 */
@Component
public class ConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private final CustomConnectionPool pool;

    /** Stores one connection per thread. */
    private final ThreadLocal<Connection> threadLocalConnection = new ThreadLocal<>();

    public ConnectionManager(CustomConnectionPool pool) {
        this.pool = pool;
    }

    // -------------------------------------------------------------------------
    // Connection management
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link Connection} bound to the current thread.
     * If no connection is bound yet, acquires one from {@link CustomConnectionPool}
     * and binds it to the current thread.
     *
     * @return the thread-local connection (never {@code null}).
     * @throws DatabaseException if the pool cannot supply a connection.
     */
    public Connection getConnection() {
        Connection connection = threadLocalConnection.get();
        if (connection == null) {
            connection = pool.getConnection();
            threadLocalConnection.set(connection);
            log.debug("New connection bound to thread [{}]",
                    Thread.currentThread().getName());
        }
        return connection;
    }

    /**
     * Releases the current thread's connection back to the pool and unbinds it
     * from the {@link ThreadLocal}.
     *
     * <p>Safe to call even if no connection is currently bound (no-op in that case).
     * <b>Must always be called in a {@code finally} block</b> by the Service layer
     * to prevent connection leaks.</p>
     */
    public void releaseConnection() {
        Connection connection = threadLocalConnection.get();
        if (connection != null) {
            try {
                // Delegate to ProxyConnection.close() → returns to pool
                connection.close();
            } catch (SQLException e) {
                log.error("Error releasing connection from thread [{}]",
                        Thread.currentThread().getName(), e);
            } finally {
                threadLocalConnection.remove();
                log.debug("Connection unbound from thread [{}]",
                        Thread.currentThread().getName());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Transaction helpers
    // -------------------------------------------------------------------------

    /**
     * Starts a manual JDBC transaction for the current thread's connection
     * by disabling auto-commit mode.
     *
     * @throws DatabaseException if setting auto-commit fails.
     */
    public void beginTransaction() {
        try {
            getConnection().setAutoCommit(false);
            log.debug("Transaction begun on thread [{}]", Thread.currentThread().getName());
        } catch (SQLException e) {
            throw new DatabaseException("Failed to begin transaction.", e);
        }
    }

    /**
     * Commits the current thread's transaction.
     *
     * @throws DatabaseException if the commit fails.
     */
    public void commit() {
        Connection connection = threadLocalConnection.get();
        if (connection == null) {
            throw new DatabaseException(
                    "Cannot commit: no active connection on thread ["
                            + Thread.currentThread().getName() + "]");
        }
        try {
            connection.commit();
            log.debug("Transaction committed on thread [{}]", Thread.currentThread().getName());
        } catch (SQLException e) {
            throw new DatabaseException("Failed to commit transaction.", e);
        }
    }

    /**
     * Rolls back the current thread's transaction.
     * Safe to call even if no transaction was started — logs a warning in that case.
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
            log.debug("Transaction rolled back on thread [{}]",
                    Thread.currentThread().getName());
        } catch (SQLException e) {
            log.error("Failed to rollback transaction on thread [{}]",
                    Thread.currentThread().getName(), e);
            throw new DatabaseException("Failed to rollback transaction.", e);
        }
    }
}
