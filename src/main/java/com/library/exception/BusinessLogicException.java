package com.library.exception;

/**
 * Thrown when a business rule is violated (e.g. attempting to borrow a book
 * that has no available copies, or a locked user trying to perform an action).
 * Controllers should map this to an HTTP 409 / 400 response.
 */
public class BusinessLogicException extends RuntimeException {

    public BusinessLogicException(String message) {
        super(message);
    }

    public BusinessLogicException(String message, Throwable cause) {
        super(message, cause);
    }
}
