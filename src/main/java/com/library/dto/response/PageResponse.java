package com.library.dto.response;

import java.util.List;

public class PageResponse<T> {
    private List<T> items;
    private int totalPages;
    private long totalElements;
    private int currentPage;

    public PageResponse() {
    }

    public PageResponse(List<T> items, int totalPages, long totalElements, int currentPage) {
        this.items = items;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.currentPage = currentPage;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }
}
