package com.library.controller;

import com.library.annotation.RoleRequired;
import com.library.model.Author;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Genre;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.UserRole;
import com.library.service.BookService;
import com.library.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final BookService bookService;

    public AdminController(UserService userService, BookService bookService) {
        this.userService = userService;
        this.bookService = bookService;
    }

    @GetMapping("/users")
    @RoleRequired({UserRole.ADMIN})
    public String listUsers(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin";
    }

    @PostMapping("/users/{id}/lock")
    @RoleRequired({UserRole.ADMIN})
    public String lockUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.lockUser(id, true);
        redirectAttributes.addFlashAttribute("successMessage", "User locked successfully.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/unlock")
    @RoleRequired({UserRole.ADMIN})
    public String unlockUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.lockUser(id, false);
        redirectAttributes.addFlashAttribute("successMessage", "User unlocked successfully.");
        return "redirect:/admin/users";
    }

    @GetMapping("/books")
    @RoleRequired({UserRole.ADMIN, UserRole.LIBRARIAN})
    public String listBooks(Model model) {
        model.addAttribute("books", bookService.searchBooks(null, null, null, 0, 1000)); // Simplified fetch all
        return "librarian-books";
    }

    @GetMapping("/books/new")
    @RoleRequired({UserRole.ADMIN, UserRole.LIBRARIAN})
    public String showCreateBookForm(Model model) {
        model.addAttribute("bookRequest", new com.library.dto.BookCreateRequest());
        return "admin/books/new";
    }

    @PostMapping("/books")
    @RoleRequired({UserRole.ADMIN, UserRole.LIBRARIAN})
    public String createBook(@Valid @ModelAttribute("bookRequest") com.library.dto.BookCreateRequest bookRequest,
                             org.springframework.validation.BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Validation failed for book creation");
            return "redirect:/admin/books/new";
        }

        Book book = new Book();
        book.setTitle(bookRequest.getTitle());
        book.setDescription(bookRequest.getDescription());
        book.setPublicationYear(bookRequest.getPublicationYear());
        book.setIsbn(bookRequest.getIsbn());

        if (bookRequest.getAuthorIds() != null) {
            List<Author> authors = new ArrayList<>();
            for (Long id : bookRequest.getAuthorIds()) {
                Author a = new Author();
                a.setId(id);
                authors.add(a);
            }
            book.setAuthors(authors);
        }

        if (bookRequest.getGenreIds() != null) {
            List<Genre> genres = new ArrayList<>();
            for (Long id : bookRequest.getGenreIds()) {
                Genre g = new Genre();
                g.setId(id);
                genres.add(g);
            }
            book.setGenres(genres);
        }

        bookService.addBook(book);

        redirectAttributes.addFlashAttribute("successMessage", "Book created successfully.");
        return "redirect:/admin/books";
    }

    @PostMapping("/books/copies")
    @RoleRequired({UserRole.ADMIN, UserRole.LIBRARIAN})
    public String createBookCopy(@Valid @ModelAttribute("copyRequest") com.library.dto.BookCopyCreateRequest copyRequest,
                                 org.springframework.validation.BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Validation failed for copy creation");
            return "redirect:/admin/books";
        }

        BookCopy copy = new BookCopy();
        copy.setBookId(copyRequest.getBookId());
        copy.setInventoryNumber(copyRequest.getInventoryNumber());
        copy.setStatus(CopyStatus.AVAILABLE);

        bookService.addBookCopy(copy);

        redirectAttributes.addFlashAttribute("successMessage", "Book copy added successfully.");
        return "redirect:/admin/books";
    }
}
