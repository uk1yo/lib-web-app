package com.library.dto.request;

import java.util.List;

public class BookCreateRequest {

    private String title;
    private String description;
    private Integer publicationYear;
    private String isbn;
    private List<Long> authorIds;
    private List<Long> genreIds;

    public BookCreateRequest() {
    }

    public BookCreateRequest(String title, String description, Integer publicationYear, String isbn, List<Long> authorIds, List<Long> genreIds) {
        this.title = title;
        this.description = description;
        this.publicationYear = publicationYear;
        this.isbn = isbn;
        this.authorIds = authorIds;
        this.genreIds = genreIds;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public void setPublicationYear(Integer publicationYear) {
        this.publicationYear = publicationYear;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public List<Long> getAuthorIds() {
        return authorIds;
    }

    public void setAuthorIds(List<Long> authorIds) {
        this.authorIds = authorIds;
    }

    public List<Long> getGenreIds() {
        return genreIds;
    }

    public void setGenreIds(List<Long> genreIds) {
        this.genreIds = genreIds;
    }
}
