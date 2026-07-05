package com.library.interceptor;

import com.library.annotation.RoleRequired;
import com.library.model.User;
import com.library.model.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            RoleRequired roleRequired = handlerMethod.getMethodAnnotation(RoleRequired.class);

            if (roleRequired != null) {
                HttpSession session = request.getSession(false);
                if (session == null || session.getAttribute("user") == null) {
                    response.sendRedirect(request.getContextPath() + "/login");
                    return false;
                }

                User user = (User) session.getAttribute("user");
                UserRole userRole = user.getRole();

                boolean hasRole = Arrays.asList(roleRequired.value()).contains(userRole);
                if (!hasRole) {
                    response.sendRedirect(request.getContextPath() + "/403");
                    return false;
                }
            }
        }
        return true;
    }
}
