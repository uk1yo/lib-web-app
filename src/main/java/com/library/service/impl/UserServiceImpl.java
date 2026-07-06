package com.library.service.impl;

import com.library.annotation.JdbcTransactional;
import com.library.dao.UserDao;
import com.library.exception.BusinessLogicException;
import com.library.exception.ResourceNotFoundException;
import com.library.model.User;
import com.library.model.enums.UserRole;
import com.library.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserDao userDao, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @JdbcTransactional
    public User registerReader(User user) {
        return createUserWithRole(user, UserRole.READER);
    }

    @Override
    @JdbcTransactional
    public User createLibrarian(User user) {
        return createUserWithRole(user, UserRole.LIBRARIAN);
    }

    private User createUserWithRole(User user, UserRole role) {
        Optional<User> existingUser = userDao.findByEmail(user.getEmail());
        if (existingUser.isPresent()) {
            throw new BusinessLogicException("User with email " + user.getEmail() + " already exists");
        }

        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash())); // assume plain text is passed in passwordHash field for creation
        user.setIsLocked(false);

        return userDao.save(user);
    }

    @Override
    @JdbcTransactional
    public User findById(Long id) {
        return userDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    @JdbcTransactional
    public User findByEmail(String email) {
        return userDao.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    @JdbcTransactional
    public List<User> findAll() {
        return userDao.findAll();
    }

    @Override
    @JdbcTransactional
    public void update(User user) {
        User existingUser = userDao.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + user.getId()));

        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());

        userDao.update(existingUser);
    }

    @Override
    @JdbcTransactional
    public void lockUser(Long id, boolean isLocked) {
        User user = userDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setIsLocked(isLocked);
        userDao.update(user);
    }
}
