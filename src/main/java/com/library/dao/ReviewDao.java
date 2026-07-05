package com.library.dao;

import com.library.model.Review;

import java.util.List;
import java.util.Optional;

public interface ReviewDao {
    Review save(Review review);

    Optional<Review> findById(Long id);

    List<Review> findByBookId(Long bookId, int offset, int limit);

    long countByBookId(Long bookId);

    double getAverageRatingByBookId(Long bookId);

    void delete(Long id);
}
