package com.library.service.impl;

import com.library.annotation.JdbcTransactional;
import com.library.dao.BookCopyDao;
import com.library.dao.BorrowRecordDao;
import com.library.dao.UserDao;
import com.library.exception.BusinessLogicException;
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

    public BorrowServiceImpl(BorrowRecordDao borrowRecordDao, BookCopyDao bookCopyDao, UserDao userDao) {
        this.borrowRecordDao = borrowRecordDao;
        this.bookCopyDao = bookCopyDao;
        this.userDao = userDao;
    }

    @Override
    @JdbcTransactional
    public BorrowRecord createBorrowRequest(Long userId, Long bookId, LendingType type) {
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

        return borrowRecordDao.save(record);
    }

    @Override
    @JdbcTransactional
    public void approveBorrow(Long recordId) {
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
    }

    @Override
    @JdbcTransactional
    public void returnBook(Long recordId) {
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
    }

    @Override
    @JdbcTransactional
    public void rejectBorrow(Long recordId) {
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
    }

    @Override
    @JdbcTransactional
    public void cancelBorrow(Long recordId) {
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
    }

    @Override
    @JdbcTransactional
    public List<BorrowRecord> findUserBorrows(Long userId) {
        return borrowRecordDao.findByUserId(userId);
    }

    @Override
    @JdbcTransactional
    public List<BorrowRecord> findPendingBorrows(int offset, int limit) {
        return borrowRecordDao.findByStatus(BorrowStatus.PENDING_APPROVE, offset, limit);
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
