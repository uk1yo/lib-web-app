package com.library.dao;

import com.library.model.Book;

import java.util.List;
import java.util.Optional;

public interface BookDao {
    Book save(Book book);

    Optional<Book> findById(Long id);

    Optional<Book> findByIsbn(String isbn);

    List<Book> searchBooks(String title, String author, String genre, int offset, int limit);

    long countBooks(String title, String author, String genre);

    void update(Book book);

    void delete(Long id);
}
