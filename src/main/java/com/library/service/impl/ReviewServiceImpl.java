package com.library.service.impl;

import com.library.config.ConnectionManager;
import com.library.dao.ReviewDao;
import com.library.exception.BusinessLogicException;
import com.library.exception.DatabaseException;
import com.library.exception.ResourceNotFoundException;
import com.library.model.Review;
import com.library.service.ReviewService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewDao reviewDao;
    private final ConnectionManager connectionManager;

    public ReviewServiceImpl(ReviewDao reviewDao, ConnectionManager connectionManager) {
        this.reviewDao = reviewDao;
        this.connectionManager = connectionManager;
    }

    @Override
    public Review addReview(Review review) {
        if (review.getRating() < 1 || review.getRating() > 5) {
            throw new BusinessLogicException("Rating must be between 1 and 5.");
        }

        connectionManager.beginTransaction();
        try {
            Review savedReview = reviewDao.save(review);
            connectionManager.commit();
            return savedReview;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to add review", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public List<Review> getReviewsByBookId(Long bookId, int offset, int limit) {
        connectionManager.beginTransaction();
        try {
            List<Review> reviews = reviewDao.findByBookId(bookId, offset, limit);
            connectionManager.commit();
            return reviews;
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to get reviews", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }

    @Override
    public void deleteReview(Long id) {
        connectionManager.beginTransaction();
        try {
            reviewDao.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

            reviewDao.delete(id);
            connectionManager.commit();
        } catch (BusinessLogicException | ResourceNotFoundException e) {
            connectionManager.rollback();
            throw e;
        } catch (Exception e) {
            connectionManager.rollback();
            throw new DatabaseException("Failed to delete review", e);
        } finally {
            connectionManager.releaseConnection();
        }
    }
}
