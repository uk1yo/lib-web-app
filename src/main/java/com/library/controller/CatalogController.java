package com.library.controller;

import com.library.model.Book;
import com.library.service.BookService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import com.library.service.ReviewService;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequestMapping("/catalog")
public class CatalogController {

    private final BookService bookService;
    private final ReviewService reviewService;

    public CatalogController(BookService bookService, ReviewService reviewService) {
        this.bookService = bookService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public String listBooks(@RequestParam(required = false) String title,
                            @RequestParam(required = false) String author,
                            @RequestParam(required = false) String genre,
                            @RequestParam(defaultValue = "1") int page,
                            Model model) {
        int limit = 10;
        int offset = (page - 1) * limit;

        List<Book> books = bookService.searchBooks(title, author, genre, offset, limit);
        long totalBooks = bookService.countBooks(title, author, genre);
        int totalPages = (int) Math.ceil((double) totalBooks / limit);

        model.addAttribute("books", books);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("title", title);
        model.addAttribute("author", author);
        model.addAttribute("genre", genre);

        return "catalog/list";
    }

    @GetMapping("/{id}")
    public String showBookDetails(@PathVariable Long id, @RequestParam(defaultValue = "1") int page, Model model) {
        Book book = bookService.findById(id);
        int limit = 10;
        int offset = (page - 1) * limit;
        
        model.addAttribute("book", book);
        model.addAttribute("reviews", reviewService.getReviewsByBookId(id, offset, limit));
        return "catalog/book";
    }
}
