package com.library.service;

import com.library.model.Review;

import java.util.List;

/**
 * Service for managing book reviews.
 */
public interface ReviewService {

    /**
     * Adds a review for a book.
     *
     * @param review the review to add
     * @return the added review
     */
    Review addReview(Review review);

    /**
     * Retrieves reviews for a specific book with pagination.
     *
     * @param bookId the book ID
     * @param offset the offset
     * @param limit  the limit
     * @return list of reviews
     */
    List<Review> getReviewsByBookId(Long bookId, int offset, int limit);

    /**
     * Deletes a review by ID.
     *
     * @param id the review ID
     */
    void deleteReview(Long id);
}
