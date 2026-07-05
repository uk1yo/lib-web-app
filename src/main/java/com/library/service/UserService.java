package com.library.service;

import com.library.model.User;

import java.util.List;

/**
 * Service for managing users.
 */
public interface UserService {

    /**
     * Registers a new reader. Hashes the password and sets default role.
     *
     * @param user the user to register
     * @return the registered user
     */
    User registerReader(User user);

    /**
     * Creates a new librarian. Admin only. Hashes the password and sets librarian role.
     *
     * @param user the librarian to create
     * @return the created librarian
     */
    User createLibrarian(User user);

    /**
     * Finds a user by ID.
     *
     * @param id the user ID
     * @return the user
     */
    User findById(Long id);

    /**
     * Finds a user by email.
     *
     * @param email the user email
     * @return the user
     */
    User findByEmail(String email);

    /**
     * Retrieves all users.
     *
     * @return list of all users
     */
    List<User> findAll();

    /**
     * Updates an existing user.
     *
     * @param user the user to update
     */
    void update(User user);

    /**
     * Locks or unlocks a user.
     *
     * @param id       the user ID
     * @param isLocked true to lock, false to unlock
     */
    void lockUser(Long id, boolean isLocked);
}
