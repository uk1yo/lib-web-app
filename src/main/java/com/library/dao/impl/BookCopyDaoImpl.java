package com.library.dao.impl;

import com.library.config.ConnectionManager;
import com.library.dao.BookCopyDao;
import com.library.exception.DatabaseException;
import com.library.model.BookCopy;
import com.library.model.enums.CopyStatus;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class BookCopyDaoImpl implements BookCopyDao {

    private final ConnectionManager connectionManager;

    public BookCopyDaoImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    private BookCopy mapRow(ResultSet rs) throws SQLException {
        BookCopy copy = new BookCopy();
        copy.setId(rs.getLong("id"));
        copy.setBookId(rs.getLong("book_id"));
        copy.setInventoryNumber(rs.getString("inventory_number"));
        copy.setStatus(CopyStatus.valueOf(rs.getString("status")));
        return copy;
    }

    @Override
    public BookCopy save(BookCopy bookCopy) {
        String sql = "INSERT INTO book_copies (book_id, inventory_number, status) VALUES (?, ?, ?) RETURNING id";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, bookCopy.getBookId());
            stmt.setString(2, bookCopy.getInventoryNumber());
            stmt.setString(3, bookCopy.getStatus().name());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    bookCopy.setId(rs.getLong("id"));
                }
                return bookCopy;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save book copy", e);
        }
    }

    @Override
    public Optional<BookCopy> findById(Long id) {
        String sql = "SELECT * FROM book_copies WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find book copy by id", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<BookCopy> findByInventoryNumber(String inventoryNumber) {
        String sql = "SELECT * FROM book_copies WHERE inventory_number = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, inventoryNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find book copy by inventory number", e);
        }
        return Optional.empty();
    }

    @Override
    public List<BookCopy> findByBookId(Long bookId) {
        String sql = "SELECT * FROM book_copies WHERE book_id = ?";
        List<BookCopy> copies = new ArrayList<>();
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    copies.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find book copies by book_id", e);
        }
        return copies;
    }

    @Override
    public List<BookCopy> findAvailableByBookId(Long bookId) {
        String sql = "SELECT * FROM book_copies WHERE book_id = ? AND status = 'AVAILABLE' FOR UPDATE SKIP LOCKED";
        List<BookCopy> copies = new ArrayList<>();
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    copies.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find available book copies", e);
        }
        return copies;
    }

    @Override
    public void update(BookCopy bookCopy) {
        String sql = "UPDATE book_copies SET status = ?, inventory_number = ? WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, bookCopy.getStatus().name());
            stmt.setString(2, bookCopy.getInventoryNumber());
            stmt.setLong(3, bookCopy.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update book copy", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM book_copies WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete book copy", e);
        }
    }
}
