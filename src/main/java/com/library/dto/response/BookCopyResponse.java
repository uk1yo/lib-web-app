package com.library.dto.response;

import com.library.model.enums.CopyStatus;

public class BookCopyResponse {

    private Long id;
    private Long bookId;
    private String inventoryNumber;
    private CopyStatus status;

    public BookCopyResponse() {
    }

    public BookCopyResponse(Long id, Long bookId, String inventoryNumber, CopyStatus status) {
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
}
