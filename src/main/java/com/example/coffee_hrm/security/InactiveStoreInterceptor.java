package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.store.StoreAccessMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class InactiveStoreInterceptor implements HandlerInterceptor {

    private final StoreAccessGuard storeAccessGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (isSafePath(request)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return true;
        }
        if (!storeAccessGuard.deniesBusinessAccess(user)) {
            return true;
        }
        if (response.isCommitted()) {
            return false;
        }
        if (prefersHtmlPage(request)) {
            response.sendRedirect(request.getContextPath() + "/store-inactive");
            return false;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"" + StoreAccessMessages.INACTIVE_CODE
                + "\",\"message\":\"" + StoreAccessMessages.INACTIVE + "\"}");
        return false;
    }

    private boolean isSafePath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        return "/".equals(path)
                || "/coffee".equals(path)
                || "/login".equals(path)
                || "/logout".equals(path)
                || "/store-inactive".equals(path)
                || "/error".equals(path)
                || "/favicon.ico".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/");
    }

    private boolean prefersHtmlPage(HttpServletRequest request) {
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("json")) {
            return false;
        }
        String fetchMode = request.getHeader("Sec-Fetch-Mode");
        if ("navigate".equalsIgnoreCase(fetchMode) || "nested-navigate".equalsIgnoreCase(fetchMode)) {
            return true;
        }
        if (fetchMode != null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept == null || accept.contains("text/html");
    }
}
