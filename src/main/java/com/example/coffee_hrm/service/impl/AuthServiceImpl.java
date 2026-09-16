package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.dto.request.LoginRequest;
import com.example.coffee_hrm.dto.response.LoginResponse;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.CoffeeRememberMeServices;
import com.example.coffee_hrm.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String LOGIN_FAILED_MESSAGE = "Tên đăng nhập hoặc mật khẩu không chính xác.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final CoffeeRememberMeServices rememberMeServices;

    @Override
    @Transactional
    public LoginResponse authenticate(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException(LOGIN_FAILED_MESSAGE));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa.");
        }

        if (!matchesPassword(request.getPassword(), user)) {
            throw new BadCredentialsException(LOGIN_FAILED_MESSAGE);
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        AuthenticatedUser principal = AuthenticatedUser.from(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute("CURRENT_USER_ID", principal.getUserId());
        session.setAttribute("CURRENT_ROLE", principal.getRoleName().name());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        if (Boolean.TRUE.equals(request.getRememberMe())) {
            httpRequest.setAttribute(CoffeeRememberMeServices.ISSUE_COOKIE_ATTRIBUTE, Boolean.TRUE);
            rememberMeServices.loginSuccess(httpRequest, httpResponse, authentication);
        }

        return LoginResponse.builder()
                .userId(principal.getUserId())
                .username(principal.getUsername())
                .roleName(principal.getRoleName())
                .employeeId(principal.getEmployeeId())
                .employeeName(principal.getDisplayName())
                .build();
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        rememberMeServices.logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        SecurityContextHolder.clearContext();
    }

    private boolean matchesPassword(String rawPassword, User user) {
        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank()) {
            return false;
        }
        if (isBcryptHash(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        if (stored.equals(rawPassword)) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            return true;
        }
        return false;
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
