package com.library.service;

import com.library.model.BorrowRecord;
import com.library.model.enums.LendingType;

/**
 * Service for managing borrow records.
 */
public interface BorrowService {

    /**
     * Creates a new borrow request for a specific book by a user.
     *
     * @param userId the ID of the user requesting the book
     * @param bookId the ID of the book being requested
     * @param type   the lending type (e.g., IN_HALL, HOME)
     * @return the created {@link BorrowRecord} containing request details
     * @throws com.library.exception.BusinessLogicException if the user has too many unreturned books, has unpaid fines, or the book is out of stock
     * @throws com.library.exception.ResourceNotFoundException if the user or the book does not exist
     */
    BorrowRecord createBorrowRequest(Long userId, Long bookId, LendingType type);

    /**
     * Approves a borrow request and issues the book.
     *
     * @param recordId the record ID
     */
    void approveBorrow(Long recordId);

    /**
     * Returns a borrowed book.
     *
     * @param recordId the record ID
     */
    void returnBook(Long recordId);

    /**
     * Rejects a pending borrow request.
     *
     * @param recordId the record ID
     */
    void rejectBorrow(Long recordId);

    /**
     * Cancels a pending borrow request.
     *
     * @param recordId the record ID
     */
    void cancelBorrow(Long recordId);

    java.util.List<BorrowRecord> findUserBorrows(Long userId);

    java.util.List<BorrowRecord> findPendingBorrows(int offset, int limit);
}
