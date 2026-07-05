package com.library.dao;

import com.library.model.Genre;

import java.util.List;
import java.util.Optional;

public interface GenreDao {
    Genre save(Genre genre);

    Optional<Genre> findById(Long id);

    List<Genre> findAll();

    void update(Genre genre);

    void delete(Long id);
}
