package com.library.service.impl;

import com.library.annotation.JdbcTransactional;
import com.library.dao.BookCopyDao;
import com.library.dao.BookDao;
import com.library.exception.BusinessLogicException;
import com.library.exception.ResourceNotFoundException;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.service.BookService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookServiceImpl implements BookService {

    private final BookDao bookDao;
    private final BookCopyDao bookCopyDao;

    public BookServiceImpl(BookDao bookDao, BookCopyDao bookCopyDao) {
        this.bookDao = bookDao;
        this.bookCopyDao = bookCopyDao;
    }

    @Override
    @JdbcTransactional
    public Book addBook(Book book) {
        Optional<Book> existingBook = bookDao.findByIsbn(book.getIsbn());
        if (existingBook.isPresent()) {
            throw new BusinessLogicException("Book with ISBN " + book.getIsbn() + " already exists");
        }
        return bookDao.save(book);
    }

    @Override
    @JdbcTransactional
    public BookCopy addBookCopy(BookCopy bookCopy) {
        return bookCopyDao.save(bookCopy);
    }

    @Override
    @JdbcTransactional
    public Book findById(Long id) {
        return bookDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));
    }

    @Override
    @JdbcTransactional
    public List<Book> searchBooks(String title, String author, String genre, int offset, int limit) {
        return bookDao.searchBooks(title, author, genre, offset, limit);
    }

    @Override
    @JdbcTransactional
    public long countBooks(String title, String author, String genre) {
        return bookDao.countBooks(title, author, genre);
    }

    @Override
    @JdbcTransactional
    public void updateBook(Book book) {
        bookDao.findById(book.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + book.getId()));
        bookDao.update(book);
    }

    @Override
    @JdbcTransactional
    public void deleteBook(Long id) {
        bookDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));
        bookDao.delete(id);
    }
}
