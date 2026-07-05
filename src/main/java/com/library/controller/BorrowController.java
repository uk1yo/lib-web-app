package com.library.controller;

import com.library.annotation.RoleRequired;
import com.library.model.User;
import com.library.model.enums.LendingType;
import com.library.model.enums.UserRole;
import com.library.service.BorrowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/borrows")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @GetMapping("/my")
    @RoleRequired({UserRole.READER})
    public String myBorrows(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        model.addAttribute("borrows", borrowService.findUserBorrows(user.getId()));
        return "my-books";
    }

    @GetMapping("/manage")
    @RoleRequired({UserRole.LIBRARIAN, UserRole.ADMIN})
    public String manageBorrows(@RequestParam(defaultValue = "1") int page, Model model) {
        int limit = 20;
        int offset = (page - 1) * limit;
        model.addAttribute("pendingBorrows", borrowService.findPendingBorrows(offset, limit));
        return "librarian";
    }

    @PostMapping("/{bookId}")
    @RoleRequired({UserRole.READER})
    public String borrowBook(@PathVariable Long bookId,
                             @RequestParam LendingType lendingType,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        borrowService.createBorrowRequest(user.getId(), bookId, lendingType);
        redirectAttributes.addFlashAttribute("successMessage", "Borrow request created successfully.");
        return "redirect:/catalog";
    }

    @PostMapping("/{id}/approve")
    @RoleRequired({UserRole.LIBRARIAN, UserRole.ADMIN})
    public String approveBorrow(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        borrowService.approveBorrow(id);
        redirectAttributes.addFlashAttribute("successMessage", "Borrow approved.");
        return "redirect:/borrows/manage";
    }

    @PostMapping("/{id}/reject")
    @RoleRequired({UserRole.LIBRARIAN, UserRole.ADMIN})
    public String rejectBorrow(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        borrowService.rejectBorrow(id);
        redirectAttributes.addFlashAttribute("successMessage", "Borrow rejected.");
        return "redirect:/borrows/manage";
    }

    @PostMapping("/{id}/return")
    @RoleRequired({UserRole.LIBRARIAN, UserRole.ADMIN})
    public String returnBook(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        borrowService.returnBook(id);
        redirectAttributes.addFlashAttribute("successMessage", "Book returned successfully.");
        return "redirect:/borrows/manage";
    }

    @PostMapping("/{id}/cancel")
    @RoleRequired({UserRole.READER})
    public String cancelBorrow(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        borrowService.cancelBorrow(id);
        redirectAttributes.addFlashAttribute("successMessage", "Borrow request cancelled.");
        return "redirect:/borrows/my";
    }
}
