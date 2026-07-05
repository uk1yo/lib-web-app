package com.library.exception;

/**
 * Thrown when a requested resource (Book, User, BorrowRecord, etc.) is not found
 * in the database. Controllers should map this to an HTTP 404 response.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, long id) {
        super(resourceName + " with id=" + id + " not found.");
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
