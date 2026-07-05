package com.library.dao;

import com.library.model.Author;

import java.util.List;
import java.util.Optional;

public interface AuthorDao {
    Author save(Author author);

    Optional<Author> findById(Long id);

    List<Author> findAll();

    void update(Author author);

    void delete(Long id);
}
