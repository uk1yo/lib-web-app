package com.library.service.impl;

import com.library.config.ConnectionManager;
import com.library.dao.BookCopyDao;
import com.library.dao.BorrowRecordDao;
import com.library.dao.UserDao;
import com.library.exception.BusinessLogicException;
import com.library.exception.DatabaseException;
import com.library.exception.ResourceNotFoundException;
import com.library.model.BookCopy;
import com.library.model.BorrowRecord;
import com.library.model.User;
import com.library.model.enums.BorrowStatus;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LendingType;
import com.library.service.BorrowService;
import com.library.service.strategy.DueDateStrategy;
import com.library.service.strategy.HomeLendingStrategy;
import com.library.service.strategy.ReadingRoomStrategy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BorrowServiceImpl implements BorrowService {

    private final BorrowRecordDao borrowRecordDao;
    private final BookCopyDao bookCopyDao;
    private final UserDao userDao;
    private final ConnectionManager connectionManager;

    public BorrowServiceImpl(BorrowRecordDao borrowRecordDao, BookCopyDao bookCopyDao, UserDao userDao, ConnectionManager connectionManager) {
        this.borrowRecordDao = borrowRecordDao;
        this.bookCopyDao = bookCopyDao;
        this.userDao = userDao;
        this.connectionManager = connectionManager;
    }

    @Override
    public BorrowRecord createBorrowRequest(Long userId, Long bookId, LendingType type) {
        connectionManager.beginTransaction();
        try {
            User user = userDao.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

            if (Boolean.TRUE.equals(user.getIsLocked())) {
                throw new BusinessLogicException("Cannot borrow book: User is locked.");
            }

            List<BookCopy> availableCopies = bookCopyDao.findAvailableByBookId(bookId);
            if (availableCopies.isEmpty()) {
                throw new BusinessLogicException("No available copies for book id: " + bookId);
            }

            BookCopy copy = availableCopies.get(0);
            copy.setStatus(CopyStatus.RESERVED);
            bookCopyDao.update(copy);

            BorrowRecord record = new BorrowRecord();
            record.setUserId(userId);
            record.setBookCopyId(copy.getId());
            record.setStatus(BorrowStatus.PENDING_APPROVE);
            record.setLendingType(type);

            BorrowRecord savedRecord = borrowRecordDao.save(record);
            connectionManager.commit();
            return savedRecord;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to create borrow request", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void approveBorrow(Long recordId) {
        connectionManager.beginTransaction();
        try {
            BorrowRecord record = borrowRecordDao.findById(recordId)
                    .orElseThrow(() -> new ResourceNotFoundException("Borrow record not found with id: " + recordId));

            if (record.getStatus() != BorrowStatus.PENDING_APPROVE) {
                throw new BusinessLogicException("Cannot approve record in status: " + record.getStatus());
            }

            BookCopy copy = bookCopyDao.findById(record.getBookCopyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Book copy not found with id: " + record.getBookCopyId()));

            DueDateStrategy strategy = getStrategy(record.getLendingType());
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dueDate = strategy.calculateDueDate(now);

            record.setStatus(BorrowStatus.BORROWED);
            record.setBorrowedAt(now);
            record.setDueDate(dueDate);
            borrowRecordDao.update(record);

            copy.setStatus(CopyStatus.ISSUED);
            bookCopyDao.update(copy);

            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to approve borrow request", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void returnBook(Long recordId) {
        connectionManager.beginTransaction();
        try {
            BorrowRecord record = borrowRecordDao.findById(recordId)
                    .orElseThrow(() -> new ResourceNotFoundException("Borrow record not found with id: " + recordId));

            if (record.getStatus() != BorrowStatus.BORROWED && record.getStatus() != BorrowStatus.PENDING_RETURN) {
                throw new BusinessLogicException("Cannot return record in status: " + record.getStatus());
            }

            BookCopy copy = bookCopyDao.findById(record.getBookCopyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Book copy not found with id: " + record.getBookCopyId()));

            record.setStatus(BorrowStatus.RETURNED);
            record.setReturnedAt(LocalDateTime.now());
            borrowRecordDao.update(record);

            copy.setStatus(CopyStatus.AVAILABLE);
            bookCopyDao.update(copy);

            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to return book", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void rejectBorrow(Long recordId) {
        connectionManager.beginTransaction();
        try {
            BorrowRecord record = borrowRecordDao.findById(recordId)
                    .orElseThrow(() -> new ResourceNotFoundException("Borrow record not found with id: " + recordId));

            if (record.getStatus() != BorrowStatus.PENDING_APPROVE) {
                throw new BusinessLogicException("Cannot reject record in status: " + record.getStatus());
            }

            BookCopy copy = bookCopyDao.findById(record.getBookCopyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Book copy not found with id: " + record.getBookCopyId()));

            record.setStatus(BorrowStatus.REJECTED);
            borrowRecordDao.update(record);

            copy.setStatus(CopyStatus.AVAILABLE);
            bookCopyDao.update(copy);

            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to reject borrow request", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void cancelBorrow(Long recordId) {
        connectionManager.beginTransaction();
        try {
            BorrowRecord record = borrowRecordDao.findById(recordId)
                    .orElseThrow(() -> new ResourceNotFoundException("Borrow record not found with id: " + recordId));

            if (record.getStatus() != BorrowStatus.PENDING_APPROVE) {
                throw new BusinessLogicException("Cannot cancel record in status: " + record.getStatus());
            }

            BookCopy copy = bookCopyDao.findById(record.getBookCopyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Book copy not found with id: " + record.getBookCopyId()));

            record.setStatus(BorrowStatus.CANCELLED);
            borrowRecordDao.update(record);

            copy.setStatus(CopyStatus.AVAILABLE);
            bookCopyDao.update(copy);

            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to cancel borrow request", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public java.util.List<com.library.model.BorrowRecord> findUserBorrows(Long userId) {
        connectionManager.beginTransaction();
        try {
            java.util.List<com.library.model.BorrowRecord> records = borrowRecordDao.findByUserId(userId);
            connectionManager.commit();
            return records;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to fetch user borrows", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public java.util.List<com.library.model.BorrowRecord> findPendingBorrows(int offset, int limit) {
        connectionManager.beginTransaction();
        try {
            java.util.List<com.library.model.BorrowRecord> records = borrowRecordDao.findByStatus(com.library.model.enums.BorrowStatus.PENDING_APPROVE, offset, limit);
            connectionManager.commit();
            return records;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to fetch pending borrows", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    private DueDateStrategy getStrategy(LendingType type) {
        if (type == LendingType.HOME) {
            return new HomeLendingStrategy();
        } else if (type == LendingType.READING_ROOM) {
            return new ReadingRoomStrategy();
        }
        throw new BusinessLogicException("Unknown lending type: " + type);
    }
}
