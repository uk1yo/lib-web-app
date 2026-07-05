package com.library.service.impl;

import com.library.config.ConnectionManager;
import com.library.dao.BookDao;
import com.library.exception.BusinessLogicException;
import com.library.exception.DatabaseException;
import com.library.exception.ResourceNotFoundException;
import com.library.model.Book;
import com.library.service.BookService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookServiceImpl implements BookService {

    private final BookDao bookDao;
    private final ConnectionManager connectionManager;

    public BookServiceImpl(BookDao bookDao, ConnectionManager connectionManager) {
        this.bookDao = bookDao;
        this.connectionManager = connectionManager;
    }

    @Override
    public Book addBook(Book book) {
        connectionManager.beginTransaction();
        try {
            Optional<Book> existingBook = bookDao.findByIsbn(book.getIsbn());
            if (existingBook.isPresent()) {
                throw new BusinessLogicException("Book with ISBN " + book.getIsbn() + " already exists");
            }

            Book savedBook = bookDao.save(book);
            connectionManager.commit();
            return savedBook;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to add book", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }
    @Override
    public com.library.model.BookCopy addBookCopy(com.library.model.BookCopy bookCopy) {
        connectionManager.beginTransaction();
        try {
            com.library.dao.BookCopyDao bookCopyDao = new com.library.dao.impl.BookCopyDaoImpl(connectionManager);
            com.library.model.BookCopy savedCopy = bookCopyDao.save(bookCopy);
            connectionManager.commit();
            return savedCopy;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new com.library.exception.DatabaseException("Failed to add book copy", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public Book findById(Long id) {
        connectionManager.beginTransaction();
        try {
            Book book = bookDao.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));
            connectionManager.commit();
            return book;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to find book by id", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public List<Book> searchBooks(String title, String author, String genre, int offset, int limit) {
        connectionManager.beginTransaction();
        try {
            List<Book> books = bookDao.searchBooks(title, author, genre, offset, limit);
            connectionManager.commit();
            return books;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to search books", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public long countBooks(String title, String author, String genre) {
        connectionManager.beginTransaction();
        try {
            long count = bookDao.countBooks(title, author, genre);
            connectionManager.commit();
            return count;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to count books", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void updateBook(Book book) {
        connectionManager.beginTransaction();
        try {
            bookDao.findById(book.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + book.getId()));

            bookDao.update(book);
            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to update book", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void deleteBook(Long id) {
        connectionManager.beginTransaction();
        try {
            bookDao.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));

            bookDao.delete(id);
            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to delete book", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }
}
