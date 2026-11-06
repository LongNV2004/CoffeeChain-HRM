package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class EmploymentAccessFilter extends OncePerRequestFilter {

    private static final Pattern OWN_CONTRACT_FILE = Pattern.compile("^/manager/contract/(\\d+)/file$");

    private final ObjectProvider<UserRepository> userRepositories;
    private final ObjectProvider<EmploymentContractRepository> contractRepositories;

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
            if (user != null && user.getRole() != null && user.getRole().getRoleName() == RoleName.MANAGER) {
                ManagerContractAccess.Limit limit = managerLimit(user);
                request.setAttribute(ManagerContractAccess.REQUEST_LIMIT, limit);
                String path = pathOf(request);
                if (limit != ManagerContractAccess.Limit.NONE
                        && !ManagerContractAccess.allowsWhenLimited(path, request.getMethod())
                        && !ownsContractFile(path, request.getMethod(), user)) {
                    denyFormerStaff(request, response, ManagerContractAccess.message(limit),
                            ManagerContractAccess.PROFILE_REDIRECT);
                    return;
                }
            }
            filterChain.doFilter(request, response);
            return;
        }
        if (block == StaffEmploymentGuard.Block.EMPLOYMENT_ENDED) {
            if (StaffEmploymentGuard.allowsWhenEmploymentEnded(pathOf(request), request.getMethod())) {
                filterChain.doFilter(request, response);
                return;
            }
            denyFormerStaff(request, response, StaffEmploymentGuard.DENIED_MESSAGE, "/dashboard/staff?limited=1");
            return;
        }
        if (block == StaffEmploymentGuard.Block.PENDING_CONTRACT) {
            if (StaffEmploymentGuard.allowsWhenPendingContract(pathOf(request), request.getMethod())) {
                filterChain.doFilter(request, response);
                return;
            }
            denyFormerStaff(request, response, StaffEmploymentGuard.PENDING_CONTRACT_MESSAGE,
                    "/dashboard/staff?limited=contract");
            return;
        }

        endSession(request, response, authentication);
        if (isPublic(pathOf(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?loginError=true&errorCode=inactive");
    }

    private ManagerContractAccess.Limit managerLimit(User user) {
        Integer employeeId = user.getEmployee() == null ? null : user.getEmployee().getId();
        EmploymentContractRepository contracts = contractRepositories.getIfAvailable();
        if (employeeId == null || contracts == null) {
            return ManagerContractAccess.Limit.NO_CONTRACT;
        }
        List<EmploymentContract> owned = contracts.findByEmployeeId(employeeId);
        return ManagerContractAccess.assess(owned == null ? List.of() : owned, VietnamTime.today());
    }

    private boolean ownsContractFile(String path, String method, User user) {
        boolean read = method == null || "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        if (!read || user.getEmployee() == null || user.getEmployee().getId() == null) {
            return false;
        }
        Matcher matcher = OWN_CONTRACT_FILE.matcher(path);
        if (!matcher.matches()) {
            return false;
        }
        EmploymentContractRepository contracts = contractRepositories.getIfAvailable();
        if (contracts == null) {
            return false;
        }
        EmploymentContract contract = contracts.findDetailById(Integer.valueOf(matcher.group(1))).orElse(null);
        return contract != null
                && contract.getEmployee() != null
                && user.getEmployee().getId().equals(contract.getEmployee().getId());
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

    private void denyFormerStaff(HttpServletRequest request, HttpServletResponse response,
                                 String message, String redirect) throws IOException {
        String accept = request.getHeader("Accept");
        boolean json = (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (json) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + message + "\"}");
            return;
        }
        response.sendRedirect(request.getContextPath() + redirect);
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
