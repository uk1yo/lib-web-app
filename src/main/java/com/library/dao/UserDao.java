package com.library.dao;

import com.library.model.User;

import java.util.List;
import java.util.Optional;

public interface UserDao {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    List<User> findAll();

    void update(User user);

    void delete(Long id);
}
