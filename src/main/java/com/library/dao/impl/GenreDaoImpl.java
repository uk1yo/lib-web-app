package com.library.dao.impl;

import com.library.config.ConnectionManager;
import com.library.dao.GenreDao;
import com.library.exception.DatabaseException;
import com.library.model.Genre;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class GenreDaoImpl implements GenreDao {
    private final ConnectionManager connectionManager;

    public GenreDaoImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    private Genre mapRow(ResultSet rs) throws SQLException {
        Genre genre = new Genre();
        genre.setId(rs.getLong("id"));
        genre.setName(rs.getString("name"));
        return genre;
    }

    @Override
    public Genre save(Genre genre) {
        String sql = "INSERT INTO genres (name) VALUES (?) RETURNING id";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, genre.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    genre.setId(rs.getLong("id"));
                }
                return genre;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save genre", e);
        }
    }

    @Override
    public Optional<Genre> findById(Long id) {
        String sql = "SELECT * FROM genres WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find genre by id", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Genre> findAll() {
        String sql = "SELECT * FROM genres ORDER BY id ASC";
        List<Genre> genres = new ArrayList<>();
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                genres.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch all genres", e);
        }
        return genres;
    }

    @Override
    public void update(Genre genre) {
        String sql = "UPDATE genres SET name = ? WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, genre.getName());
            stmt.setLong(2, genre.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update genre", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM genres WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete genre", e);
        }
    }
}
