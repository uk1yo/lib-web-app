package com.library.service.impl;

import com.library.annotation.JdbcTransactional;
import com.library.dao.ReviewDao;
import com.library.exception.BusinessLogicException;
import com.library.exception.ResourceNotFoundException;
import com.library.model.Review;
import com.library.service.ReviewService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewDao reviewDao;

    public ReviewServiceImpl(ReviewDao reviewDao) {
        this.reviewDao = reviewDao;
    }

    @Override
    @JdbcTransactional
    public Review addReview(Review review) {
        if (review.getRating() < 1 || review.getRating() > 5) {
            throw new BusinessLogicException("Rating must be between 1 and 5.");
        }
        return reviewDao.save(review);
    }

    @Override
    @JdbcTransactional
    public List<Review> getReviewsByBookId(Long bookId, int offset, int limit) {
        return reviewDao.findByBookId(bookId, offset, limit);
    }

    @Override
    @JdbcTransactional
    public void deleteReview(Long id) {
        reviewDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        reviewDao.delete(id);
    }
}
