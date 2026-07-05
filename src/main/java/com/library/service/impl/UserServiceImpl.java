package com.library.service.impl;

import com.library.config.ConnectionManager;
import com.library.dao.UserDao;
import com.library.exception.BusinessLogicException;
import com.library.exception.DatabaseException;
import com.library.exception.ResourceNotFoundException;
import com.library.model.User;
import com.library.model.enums.UserRole;
import com.library.service.UserService;
import com.library.util.PasswordUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final ConnectionManager connectionManager;

    public UserServiceImpl(UserDao userDao, ConnectionManager connectionManager) {
        this.userDao = userDao;
        this.connectionManager = connectionManager;
    }

    @Override
    public User registerReader(User user) {
        return createUserWithRole(user, UserRole.READER);
    }

    @Override
    public User createLibrarian(User user) {
        return createUserWithRole(user, UserRole.LIBRARIAN);
    }

    private User createUserWithRole(User user, UserRole role) {
        connectionManager.beginTransaction();
        try {
            Optional<User> existingUser = userDao.findByEmail(user.getEmail());
            if (existingUser.isPresent()) {
                throw new BusinessLogicException("User with email " + user.getEmail() + " already exists");
            }

            user.setRole(role);
            user.setPasswordHash(PasswordUtil.hashPassword(user.getPasswordHash())); // assume plain text is passed in passwordHash field for creation
            user.setIsLocked(false);

            User savedUser = userDao.save(user);
            connectionManager.commit();
            return savedUser;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to create user", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public User findById(Long id) {
        connectionManager.beginTransaction();
        try {
            User user = userDao.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
            connectionManager.commit();
            return user;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to find user by id", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public User findByEmail(String email) {
        connectionManager.beginTransaction();
        try {
            User user = userDao.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
            connectionManager.commit();
            return user;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to find user by email", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public List<User> findAll() {
        connectionManager.beginTransaction();
        try {
            List<User> users = userDao.findAll();
            connectionManager.commit();
            return users;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to list users", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void update(User user) {
        connectionManager.beginTransaction();
        try {
            User existingUser = userDao.findById(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + user.getId()));

            existingUser.setFirstName(user.getFirstName());
            existingUser.setLastName(user.getLastName());

            userDao.update(existingUser);
            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to update user", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void lockUser(Long id, boolean isLocked) {
        connectionManager.beginTransaction();
        try {
            User user = userDao.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

            user.setIsLocked(isLocked);
            userDao.update(user);
            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to update user lock status", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }
}
