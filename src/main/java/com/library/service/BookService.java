package com.library.service;

import com.library.model.Book;

import java.util.List;

/**
 * Service for managing books.
 */
public interface BookService {

    /**
     * Adds a new book to the catalog.
     *
     * @param book the book to add
     * @return the added book
     */
    Book addBook(Book book);

    /**
     * Adds a new copy for a book.
     *
     * @param bookCopy the book copy to add
     * @return the added book copy
     */
    com.library.model.BookCopy addBookCopy(com.library.model.BookCopy bookCopy);

    /**
     * Finds a book by ID.
     *
     * @param id the book ID
     * @return the book
     */
    Book findById(Long id);

    /**
     * Searches for books by title, author, and genre.
     *
     * @param title  the title filter
     * @param author the author filter
     * @param genre  the genre filter
     * @param offset the offset for pagination
     * @param limit  the limit for pagination
     * @return list of matching books
     */
    List<Book> searchBooks(String title, String author, String genre, int offset, int limit);

    /**
     * Counts the total number of books matching the search criteria.
     *
     * @param title  the title filter
     * @param author the author filter
     * @param genre  the genre filter
     * @return total count of matching books
     */
    long countBooks(String title, String author, String genre);

    /**
     * Updates an existing book.
     *
     * @param book the book to update
     */
    void updateBook(Book book);

    /**
     * Deletes a book by ID.
     *
     * @param id the book ID
     */
    void deleteBook(Long id);
}
