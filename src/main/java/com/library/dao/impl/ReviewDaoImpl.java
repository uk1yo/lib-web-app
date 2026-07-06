package com.library.dao.impl;

import com.library.config.ConnectionManager;
import com.library.dao.ReviewDao;
import com.library.exception.DatabaseException;
import com.library.model.Review;
import com.library.util.GenericRowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ReviewDaoImpl implements ReviewDao {

    private final ConnectionManager connectionManager;
    private final GenericRowMapper<Review> rowMapper = new GenericRowMapper<>();

    public ReviewDaoImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    private Review mapRow(ResultSet rs) throws SQLException {
        return rowMapper.mapRow(rs, Review.class);
    }

    @Override
    public Review save(Review review) {
        String sql = "INSERT INTO reviews (book_id, user_id, rating, comment) VALUES (?, ?, ?, ?) RETURNING id, created_at";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, review.getBookId());
            stmt.setLong(2, review.getUserId());
            stmt.setInt(3, review.getRating());
            stmt.setString(4, review.getComment());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    review.setId(rs.getLong("id"));
                    review.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
                return review;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save review", e);
        }
    }

    @Override
    public Optional<Review> findById(Long id) {
        String sql = "SELECT * FROM reviews WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find review by id", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Review> findByBookId(Long bookId, int offset, int limit) {
        String sql = "SELECT * FROM reviews WHERE book_id = ? ORDER BY created_at DESC OFFSET ? LIMIT ?";
        List<Review> reviews = new ArrayList<>();
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            stmt.setInt(2, offset);
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reviews.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find reviews by book id", e);
        }
        return reviews;
    }

    @Override
    public long countByBookId(Long bookId) {
        String sql = "SELECT COUNT(*) FROM reviews WHERE book_id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count reviews by book id", e);
        }
        return 0;
    }

    @Override
    public double getAverageRatingByBookId(Long bookId) {
        String sql = "SELECT AVG(rating) FROM reviews WHERE book_id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to get average rating", e);
        }
        return 0.0;
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM reviews WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete review", e);
        }
    }
}
