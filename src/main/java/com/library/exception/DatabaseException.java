package com.library.exception;

/**
 * Thrown when a JDBC operation fails or the connection pool is exhausted.
 * Wraps low-level {@link java.sql.SQLException} into an unchecked exception
 * so that DAO methods do not pollute their signatures with checked exceptions.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseException(Throwable cause) {
        super(cause);
    }
}
