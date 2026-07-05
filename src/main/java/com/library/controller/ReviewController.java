package com.library.controller;

import com.library.annotation.RoleRequired;
import com.library.model.Review;
import com.library.model.User;
import com.library.model.enums.UserRole;
import com.library.service.ReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @RoleRequired({UserRole.READER})
    public String addReview(@RequestParam Long bookId,
                            @RequestParam Integer rating,
                            @RequestParam String comment,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        
        Review review = new Review();
        review.setBookId(bookId);
        review.setUserId(user.getId());
        review.setRating(rating);
        review.setComment(comment);
        
        reviewService.addReview(review);
        redirectAttributes.addFlashAttribute("successMessage", "Review added successfully.");
        
        return "redirect:/catalog/" + bookId;
    }
}
