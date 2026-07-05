package com.library.service;

import com.library.model.BorrowRecord;
import com.library.model.enums.LendingType;

/**
 * Service for managing borrow records.
 */
public interface BorrowService {

    /**
     * Creates a new borrow request.
     *
     * @param userId the user ID
     * @param bookId the book ID
     * @param type   the lending type
     * @return the created borrow record
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
