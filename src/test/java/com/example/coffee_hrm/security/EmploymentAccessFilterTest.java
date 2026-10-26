package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmploymentAccessFilterTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeSessionOfATerminatedStaffIsEnded() throws Exception {
        User staff = account(RoleName.STAFF, EmployeeStatus.TERMINATED, false);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(staff));
        authenticate(staff);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/staff");
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EmploymentAccessFilter(provider()).doFilter(request, response, chain);

        assertEquals("/login?loginError=true&errorCode=terminated", response.getRedirectedUrl());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void otherStaffAndAdminKeepTheirSession() throws Exception {
        User admin = account(RoleName.ADMIN, EmployeeStatus.ACTIVE, true);
        when(userRepository.findByIdWithRole(1)).thenReturn(Optional.of(admin));
        authenticate(admin);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EmploymentAccessFilter(provider()).doFilter(request, response, chain);

        assertNull(response.getRedirectedUrl());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        verify(chain).doFilter(request, response);
    }

    private ObjectProvider<UserRepository> provider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<UserRepository> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(userRepository);
        return provider;
    }

    private void authenticate(User user) {
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User account(RoleName roleName, EmployeeStatus status, boolean active) {
        Employee employee = Employee.builder()
                .id(roleName == RoleName.ADMIN ? 1 : 11)
                .fullName(roleName.name())
                .email(roleName == RoleName.ADMIN ? "admin@shop.vn" : "an@shop.vn")
                .status(status)
                .build();
        User user = User.builder()
                .id(employee.getId())
                .username(employee.getEmail())
                .passwordHash("secret")
                .role(Role.builder().id(3).roleName(roleName).build())
                .employee(employee)
                .isActive(active)
                .build();
        employee.setUser(user);
        return user;
    }
}
