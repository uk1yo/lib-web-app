package com.library.dao.impl;

import com.library.config.ConnectionManager;
import com.library.dao.AuthorDao;
import com.library.exception.DatabaseException;
import com.library.model.Author;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AuthorDaoImpl implements AuthorDao {
    private final ConnectionManager connectionManager;

    public AuthorDaoImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    private Author mapRow(ResultSet rs) throws SQLException {
        Author author = new Author();
        author.setId(rs.getLong("id"));
        author.setFirstName(rs.getString("first_name"));
        author.setLastName(rs.getString("last_name"));
        return author;
    }

    @Override
    public Author save(Author author) {
        String sql = "INSERT INTO authors (first_name, last_name) VALUES (?, ?) RETURNING id";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, author.getFirstName());
            stmt.setString(2, author.getLastName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    author.setId(rs.getLong("id"));
                }
                return author;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save author", e);
        }
    }

    @Override
    public Optional<Author> findById(Long id) {
        String sql = "SELECT * FROM authors WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find author by id", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Author> findAll() {
        String sql = "SELECT * FROM authors ORDER BY id ASC";
        List<Author> authors = new ArrayList<>();
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                authors.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch all authors", e);
        }
        return authors;
    }

    @Override
    public void update(Author author) {
        String sql = "UPDATE authors SET first_name = ?, last_name = ? WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, author.getFirstName());
            stmt.setString(2, author.getLastName());
            stmt.setLong(3, author.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update author", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM authors WHERE id = ?";
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete author", e);
        }
    }
}
