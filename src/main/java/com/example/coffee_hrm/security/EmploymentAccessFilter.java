package com.example.coffee_hrm.security;

import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class EmploymentAccessFilter extends OncePerRequestFilter {

    private final ObjectProvider<UserRepository> userRepositories;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserRepository userRepository = userRepositories.getIfAvailable();
        if (userRepository == null) {
            filterChain.doFilter(request, response);
            return;
        }
        User user = userRepository.findByIdWithRole(principal.getUserId()).orElse(null);
        StaffEmploymentGuard.Block block = StaffEmploymentGuard.assess(user);
        if (block == StaffEmploymentGuard.Block.NONE) {
            filterChain.doFilter(request, response);
            return;
        }

        endSession(request, response, authentication);
        if (isPublic(pathOf(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        String code = block == StaffEmploymentGuard.Block.EMPLOYMENT_ENDED ? "terminated" : "inactive";
        response.sendRedirect(request.getContextPath() + "/login?loginError=true&errorCode=" + code);
    }

    private void endSession(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession session = request.getSession(false);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // SecurityContextLogoutHandler đã hủy session.
            }
        }
        SecurityContextHolder.clearContext();
        Cookie cookie = new Cookie("coffee-hrm-remember-me", "");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setPath(request.getContextPath() == null || request.getContextPath().isBlank()
                ? "/"
                : request.getContextPath());
        response.addCookie(cookie);
    }

    private boolean isPublic(String path) {
        return "/".equals(path)
                || "/coffee".equals(path)
                || "/login".equals(path)
                || "/logout".equals(path)
                || "/error".equals(path)
                || "/favicon.ico".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/");
    }

    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }
}
