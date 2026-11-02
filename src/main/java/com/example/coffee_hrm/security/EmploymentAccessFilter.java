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
        if (block == StaffEmploymentGuard.Block.EMPLOYMENT_ENDED) {
            if (StaffEmploymentGuard.allowsWhenEmploymentEnded(pathOf(request), request.getMethod())) {
                filterChain.doFilter(request, response);
                return;
            }
            denyFormerStaff(request, response);
            return;
        }

        endSession(request, response, authentication);
        if (isPublic(pathOf(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?loginError=true&errorCode=inactive");
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

    private void denyFormerStaff(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String accept = request.getHeader("Accept");
        boolean json = (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (json) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + StaffEmploymentGuard.DENIED_MESSAGE + "\"}");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/dashboard/staff?limited=1");
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
