package com.library.model;

import com.library.model.enums.CopyStatus;

import java.util.Objects;

public class BookCopy {

    private Long id;
    private Long bookId;
    private String inventoryNumber;
    private CopyStatus status;

    public BookCopy() {
    }

    public BookCopy(Long id, Long bookId, String inventoryNumber, CopyStatus status) {
        this.id = id;
        this.bookId = bookId;
        this.inventoryNumber = inventoryNumber;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public void setInventoryNumber(String inventoryNumber) {
        this.inventoryNumber = inventoryNumber;
    }

    public CopyStatus getStatus() {
        return status;
    }

    public void setStatus(CopyStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookCopy bookCopy = (BookCopy) o;
        return Objects.equals(id, bookCopy.id) && Objects.equals(inventoryNumber, bookCopy.inventoryNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, inventoryNumber);
    }

    @Override
    public String toString() {
        return "BookCopy{" +
                "id=" + id +
                ", bookId=" + bookId +
                ", inventoryNumber='" + inventoryNumber + '\'' +
                ", status=" + status +
                '}';
    }
}
