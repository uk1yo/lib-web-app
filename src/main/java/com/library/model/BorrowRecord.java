package com.library.model;

import com.library.model.enums.BorrowStatus;
import com.library.model.enums.LendingType;

import java.time.LocalDateTime;
import java.util.Objects;

public class BorrowRecord {

    private Long id;
    private Long userId;
    private Long bookCopyId;
    private BorrowStatus status;
    private LendingType lendingType;
    private LocalDateTime dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime borrowedAt;
    private LocalDateTime returnedAt;

    public BorrowRecord() {
    }

    public BorrowRecord(Long id, Long userId, Long bookCopyId, BorrowStatus status, LendingType lendingType, LocalDateTime dueDate, LocalDateTime createdAt, LocalDateTime borrowedAt, LocalDateTime returnedAt) {
        this.id = id;
        this.userId = userId;
        this.bookCopyId = bookCopyId;
        this.status = status;
        this.lendingType = lendingType;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.borrowedAt = borrowedAt;
        this.returnedAt = returnedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getBookCopyId() {
        return bookCopyId;
    }

    public void setBookCopyId(Long bookCopyId) {
        this.bookCopyId = bookCopyId;
    }

    public BorrowStatus getStatus() {
        return status;
    }

    public void setStatus(BorrowStatus status) {
        this.status = status;
    }

    public LendingType getLendingType() {
        return lendingType;
    }

    public void setLendingType(LendingType lendingType) {
        this.lendingType = lendingType;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getBorrowedAt() {
        return borrowedAt;
    }

    public void setBorrowedAt(LocalDateTime borrowedAt) {
        this.borrowedAt = borrowedAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BorrowRecord that = (BorrowRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "BorrowRecord{" +
                "id=" + id +
                ", userId=" + userId +
                ", bookCopyId=" + bookCopyId +
                ", status=" + status +
                ", lendingType=" + lendingType +
                '}';
    }
}
