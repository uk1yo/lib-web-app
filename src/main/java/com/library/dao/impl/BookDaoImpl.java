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
    public Book save(Book book) {
        Connection connection = connectionManager.getConnection();
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
                        book = mapBook(rs);
                    }
                    mapAuthorToBook(rs, book, processedAuthors);
                    mapGenreToBook(rs, book, processedGenres);
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
        // Step 1: Find matching Book IDs with pagination using the Builder
        BookSearchQueryBuilder builder = new BookSearchQueryBuilder()
                .withTitle(title)
                .withAuthor(authorName)
                .withGenre(genreName)
                .withPagination(offset, limit);

        String idSql = builder.buildQuery();
        List<Object> params = builder.getParameters();
        List<Long> bookIds = new ArrayList<>();

        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(idSql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bookIds.add(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to search book IDs", e);
        }

        if (bookIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: Fetch all matching books with their authors and genres in ONE query (fixing N+1)
        String inClause = String.join(",", Collections.nCopies(bookIds.size(), "?"));
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
                "WHERE b.id IN (" + inClause + ") " +
                "ORDER BY b.id DESC"; // Maintain correct order

        Map<Long, Book> bookMap = new LinkedHashMap<>(); // LinkedHashMap preserves insertion order (which we want to match the DESC ordering)
        Map<Long, Set<Long>> processedAuthorsMap = new HashMap<>();
        Map<Long, Set<Long>> processedGenresMap = new HashMap<>();

        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < bookIds.size(); i++) {
                stmt.setLong(i + 1, bookIds.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long bookId = rs.getLong("book_id");
                    Book book = bookMap.get(bookId);
                    if (book == null) {
                        book = mapBook(rs);
                        bookMap.put(bookId, book);
                        processedAuthorsMap.put(bookId, new HashSet<>());
                        processedGenresMap.put(bookId, new HashSet<>());
                    }
                    mapAuthorToBook(rs, book, processedAuthorsMap.get(bookId));
                    mapGenreToBook(rs, book, processedGenresMap.get(bookId));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch books with relations", e);
        }

        return new ArrayList<>(bookMap.values());
    }

    @Override
    public long countBooks(String title, String authorName, String genreName) {
        BookSearchQueryBuilder builder = new BookSearchQueryBuilder()
                .withTitle(title)
                .withAuthor(authorName)
                .withGenre(genreName);

        String sql = builder.buildCountQuery();
        List<Object> params = builder.getParameters(); // Get parameters before offset/limit are added (since we didn't call withPagination)

        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count books", e);
        }
        return 0;
    }

    @Override
    public void update(Book book) {
        Connection connection = connectionManager.getConnection();
        String sql = "UPDATE books SET title = ?, description = ?, publication_year = ?, isbn = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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

            try (PreparedStatement delAuthorStmt = connection.prepareStatement("DELETE FROM book_authors WHERE book_id = ?")) {
                delAuthorStmt.setLong(1, book.getId());
                delAuthorStmt.executeUpdate();
            }
            try (PreparedStatement delGenreStmt = connection.prepareStatement("DELETE FROM book_genres WHERE book_id = ?")) {
                delGenreStmt.setLong(1, book.getId());
                delGenreStmt.executeUpdate();
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
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update book and relations", e);
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

    private Book mapBook(ResultSet rs) throws SQLException {
        Book book = new Book();
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
        return book;
    }

    private void mapAuthorToBook(ResultSet rs, Book book, Set<Long> processedAuthors) throws SQLException {
        long authorId = rs.getLong("author_id");
        if (!rs.wasNull() && processedAuthors.add(authorId)) {
            Author author = new Author();
            author.setId(authorId);
            author.setFirstName(rs.getString("author_fname"));
            author.setLastName(rs.getString("author_lname"));
            book.getAuthors().add(author);
        }
    }

    private void mapGenreToBook(ResultSet rs, Book book, Set<Long> processedGenres) throws SQLException {
        long genreId = rs.getLong("genre_id");
        if (!rs.wasNull() && processedGenres.add(genreId)) {
            Genre genre = new Genre();
            genre.setId(genreId);
            genre.setName(rs.getString("genre_name"));
            book.getGenres().add(genre);
        }
    }
}
