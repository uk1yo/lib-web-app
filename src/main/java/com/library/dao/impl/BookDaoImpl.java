package com.library.dao.impl;

import com.library.config.ConnectionManager;
import com.library.dao.BookDao;
import com.library.dao.builder.BookSearchQueryBuilder;
import com.library.exception.DatabaseException;
import com.library.model.Author;
import com.library.model.Book;
import com.library.model.Genre;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class BookDaoImpl implements BookDao {

    private final ConnectionManager connectionManager;

    public BookDaoImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Book save(Book book, Connection connection) {
        String insertBookSql = "INSERT INTO books (title, description, publication_year, isbn) " +
                "VALUES (?, ?, ?, ?) RETURNING id";
        try (PreparedStatement stmt = connection.prepareStatement(insertBookSql)) {
            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getDescription());
            if (book.getPublicationYear() != null) {
                stmt.setInt(3, book.getPublicationYear());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setString(4, book.getIsbn());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    book.setId(rs.getLong("id"));
                }
            }

            if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                String insertAuthorSql = "INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)";
                try (PreparedStatement authorStmt = connection.prepareStatement(insertAuthorSql)) {
                    for (Author author : book.getAuthors()) {
                        authorStmt.setLong(1, book.getId());
                        authorStmt.setLong(2, author.getId());
                        authorStmt.addBatch();
                    }
                    authorStmt.executeBatch();
                }
            }

            if (book.getGenres() != null && !book.getGenres().isEmpty()) {
                String insertGenreSql = "INSERT INTO book_genres (book_id, genre_id) VALUES (?, ?)";
                try (PreparedStatement genreStmt = connection.prepareStatement(insertGenreSql)) {
                    for (Genre genre : book.getGenres()) {
                        genreStmt.setLong(1, book.getId());
                        genreStmt.setLong(2, genre.getId());
                        genreStmt.addBatch();
                    }
                    genreStmt.executeBatch();
                }
            }
            return book;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save book and relations", e);
        }
    }

    @Override
    public Optional<Book> findById(Long id) {
        String sql = "SELECT " +
                "b.id AS book_id, b.title AS book_title, b.description AS book_desc, " +
                "b.publication_year AS book_year, b.isbn AS book_isbn, " +
                "a.id AS author_id, a.first_name AS author_fname, a.last_name AS author_lname, " +
                "g.id AS genre_id, g.name AS genre_name " +
                "FROM books b " +
                "LEFT JOIN book_authors ba ON b.id = ba.book_id " +
                "LEFT JOIN authors a ON ba.author_id = a.id " +
                "LEFT JOIN book_genres bg ON b.id = bg.book_id " +
                "LEFT JOIN genres g ON bg.genre_id = g.id " +
                "WHERE b.id = ?";

        Book book = null;
        Set<Long> processedAuthors = new HashSet<>();
        Set<Long> processedGenres = new HashSet<>();

        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (book == null) {
                        book = new Book();
                        book.setId(rs.getLong("book_id"));
                        book.setTitle(rs.getString("book_title"));
                        book.setDescription(rs.getString("book_desc"));
                        int year = rs.getInt("book_year");
                        if (!rs.wasNull()) {
                            book.setPublicationYear(year);
                        }
                        book.setIsbn(rs.getString("book_isbn"));
                        book.setAuthors(new ArrayList<>());
                        book.setGenres(new ArrayList<>());
                    }

                    long authorId = rs.getLong("author_id");
                    if (!rs.wasNull() && processedAuthors.add(authorId)) {
                        Author author = new Author();
                        author.setId(authorId);
                        author.setFirstName(rs.getString("author_fname"));
                        author.setLastName(rs.getString("author_lname"));
                        book.getAuthors().add(author);
                    }

                    long genreId = rs.getLong("genre_id");
                    if (!rs.wasNull() && processedGenres.add(genreId)) {
                        Genre genre = new Genre();
                        genre.setId(genreId);
                        genre.setName(rs.getString("genre_name"));
                        book.getGenres().add(genre);
                    }
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find book by id with relations", e);
        }

        return Optional.ofNullable(book);
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        String sql = "SELECT id FROM books WHERE isbn = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, isbn);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return findById(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find book by isbn", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Book> searchBooks(String title, String authorName, String genreName, int offset, int limit) {
        BookSearchQueryBuilder builder = new BookSearchQueryBuilder()
                .withTitle(title)
                .withAuthor(authorName)
                .withGenre(genreName)
                .withPagination(offset, limit);

        String sql = builder.buildQuery();
        List<Object> params = builder.getParameters();
        List<Long> bookIds = new ArrayList<>();

        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bookIds.add(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to search books", e);
        }

        List<Book> books = new ArrayList<>();
        for (Long id : bookIds) {
            findById(id).ifPresent(books::add);
        }
        return books;
    }

    @Override
    public void update(Book book) {
        String sql = "UPDATE books SET title = ?, description = ?, publication_year = ?, isbn = ? WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getDescription());
            if (book.getPublicationYear() != null) {
                stmt.setInt(3, book.getPublicationYear());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setString(4, book.getIsbn());
            stmt.setLong(5, book.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update book", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM books WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete book", e);
        }
    }
}
