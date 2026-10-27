package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Đọc role và chứng chỉ quản lý từ database trên mỗi request
 * để session cũ không giữ quyền Manager sau khi đổi chức vụ hoặc chưa đạt chứng chỉ.
 */
public class ManagerCertificateFilter extends OncePerRequestFilter {

    private final ObjectProvider<UserRepository> userRepositories;

    public ManagerCertificateFilter(ObjectProvider<UserRepository> userRepositories) {
        this.userRepositories = userRepositories;
    }

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
        if (user == null || user.getRole() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        refreshSession(request, authentication, principal, user);
        if (user.getRole().getRoleName() == RoleName.MANAGER && !hasManagementCertificate(user)) {
            String path = pathOf(request);
            if (!ManagerOperationGuard.allowsWithoutCertificate(path, request.getMethod())) {
                deny(request, response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void refreshSession(HttpServletRequest request,
                                Authentication authentication,
                                AuthenticatedUser principal,
                                User user) {
        AuthenticatedUser fresh = AuthenticatedUser.from(user);
        if (fresh.getRoleName() == principal.getRoleName()
                && Objects.equals(fresh.getStoreId(), principal.getStoreId())
                && fresh.isActive() == principal.isActive()) {
            return;
        }
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                fresh, authentication.getCredentials(), fresh.getAuthorities());
        token.setDetails(authentication.getDetails());
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(token);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
    }

    private boolean hasManagementCertificate(User user) {
        Employee employee = user.getEmployee();
        return employee != null && Boolean.TRUE.equals(employee.getManagementCertified());
    }

    private void deny(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String accept = request.getHeader("Accept");
        boolean json = (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (json) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + ManagerOperationGuard.REQUIRED_MESSAGE + "\"}");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/dashboard/manager");
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
