package com.library.interceptor;

import com.library.model.User;
import com.library.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LockCheckInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public LockCheckInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            User sessionUser = (User) session.getAttribute("user");
            User dbUser = userService.findById(sessionUser.getId());

            if (Boolean.TRUE.equals(dbUser.getIsLocked())) {
                session.invalidate();
                response.sendRedirect(request.getContextPath() + "/login?error=account_locked");
                return false;
            }
        }
        return true;
    }
}
