package com.library.dao.impl;

import com.library.config.ConnectionManager;
import com.library.dao.BorrowRecordDao;
import com.library.exception.DatabaseException;
import com.library.model.BorrowRecord;
import com.library.util.GenericRowMapper;
import com.library.model.enums.BorrowStatus;
import com.library.model.enums.LendingType;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class BorrowRecordDaoImpl implements BorrowRecordDao {

    private final ConnectionManager connectionManager;
    private final GenericRowMapper<BorrowRecord> rowMapper = new GenericRowMapper<>();

    public BorrowRecordDaoImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    private BorrowRecord mapRow(ResultSet rs) throws SQLException {
        return rowMapper.mapRow(rs, BorrowRecord.class);
    }

    @Override
    public BorrowRecord save(BorrowRecord record) {
        String sql = "INSERT INTO borrow_records (user_id, book_copy_id, status, lending_type, due_date, borrowed_at, returned_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, record.getUserId());
            stmt.setLong(2, record.getBookCopyId());
            stmt.setString(3, record.getStatus().name());
            stmt.setString(4, record.getLendingType().name());
            stmt.setTimestamp(5, record.getDueDate() != null ? Timestamp.valueOf(record.getDueDate()) : null);
            stmt.setTimestamp(6, record.getBorrowedAt() != null ? Timestamp.valueOf(record.getBorrowedAt()) : null);
            stmt.setTimestamp(7, record.getReturnedAt() != null ? Timestamp.valueOf(record.getReturnedAt()) : null);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    record.setId(rs.getLong("id"));
                    record.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
                return record;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save borrow record", e);
        }
    }

    @Override
    public Optional<BorrowRecord> findById(Long id) {
        String sql = "SELECT * FROM borrow_records WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find borrow record by id", e);
        }
        return Optional.empty();
    }

    @Override
    public List<BorrowRecord> findByUserId(Long userId) {
        String sql = "SELECT * FROM borrow_records WHERE user_id = ? ORDER BY created_at DESC";
        List<BorrowRecord> records = new ArrayList<>();
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find borrow records by user_id", e);
        }
        return records;
    }

    @Override
    public List<BorrowRecord> findByStatus(BorrowStatus status, int offset, int limit) {
        String sql = "SELECT * FROM borrow_records WHERE status = ? ORDER BY created_at DESC OFFSET ? LIMIT ?";
        List<BorrowRecord> records = new ArrayList<>();
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setInt(2, offset);
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find borrow records by status", e);
        }
        return records;
    }

    @Override
    public long countByStatus(BorrowStatus status) {
        String sql = "SELECT COUNT(*) FROM borrow_records WHERE status = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count borrow records by status", e);
        }
        return 0;
    }

    @Override
    public void update(BorrowRecord record) {
        String sql = "UPDATE borrow_records SET status = ?, due_date = ?, borrowed_at = ?, returned_at = ? WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, record.getStatus().name());
            stmt.setTimestamp(2, record.getDueDate() != null ? Timestamp.valueOf(record.getDueDate()) : null);
            stmt.setTimestamp(3, record.getBorrowedAt() != null ? Timestamp.valueOf(record.getBorrowedAt()) : null);
            stmt.setTimestamp(4, record.getReturnedAt() != null ? Timestamp.valueOf(record.getReturnedAt()) : null);
            stmt.setLong(5, record.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update borrow record", e);
        }
    }
}
