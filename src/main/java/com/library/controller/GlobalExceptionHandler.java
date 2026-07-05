package com.library.controller;

import com.library.exception.BusinessLogicException;
import com.library.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({BusinessLogicException.class, ResourceNotFoundException.class})
    public String handleBusinessExceptions(Exception ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isEmpty()) {
            return "redirect:/"; // fallback
        }
        return "redirect:" + referer;
    }
}
