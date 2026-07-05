package com.library.dto;

import jakarta.validation.constraints.NotBlank;

public class BookCopyCreateRequest {

    private Long bookId;

    @NotBlank(message = "Inventory number is required")
    private String inventoryNumber;

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
}
