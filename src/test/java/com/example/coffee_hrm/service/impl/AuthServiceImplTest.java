package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.EmploymentEndedException;
import com.example.coffee_hrm.dto.request.LoginRequest;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.CoffeeRememberMeServices;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SecurityContextRepository securityContextRepository;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                securityContextRepository,
                new CoffeeRememberMeServices("test-key", mock(org.springframework.security.core.userdetails.UserDetailsService.class)));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void terminatedStaffCannotLoginWithTheCorrectPassword() {
        User staff = account(RoleName.STAFF, EmployeeStatus.TERMINATED, true, "secret");
        when(userRepository.findByUsername("an@shop.vn")).thenReturn(Optional.of(staff));

        EmploymentEndedException ex = assertThrows(EmploymentEndedException.class,
                () -> service.authenticate(login("an@shop.vn", "secret"), request(), response()));

        assertEquals(EmploymentEndedException.MESSAGE, ex.getMessage());
    }

    @Test
    void inactiveTerminatedStaffGetsTheEmploymentEndedMessage() {
        User staff = account(RoleName.STAFF, EmployeeStatus.TERMINATED, false, "secret");
        when(userRepository.findByUsername("an@shop.vn")).thenReturn(Optional.of(staff));

        EmploymentEndedException ex = assertThrows(EmploymentEndedException.class,
                () -> service.authenticate(login("an@shop.vn", "secret"), request(), response()));

        assertEquals(EmploymentEndedException.MESSAGE, ex.getMessage());
    }

    @Test
    void inactiveAccountThatStillWorksIsRejectedWithoutTheResignationMessage() {
        User staff = account(RoleName.STAFF, EmployeeStatus.ACTIVE, false, "secret");
        when(userRepository.findByUsername("an@shop.vn")).thenReturn(Optional.of(staff));

        DisabledException ex = assertThrows(DisabledException.class,
                () -> service.authenticate(login("an@shop.vn", "secret"), request(), response()));

        assertTrue(ex.getMessage().contains("vô hiệu hóa"));
        assertTrue(!(ex instanceof EmploymentEndedException));
    }

    @Test
    void rehiredStaffLogsInWithTheEmailAndTheNewPassword() {
        String emailedPassword = "Rehire#12";
        User staff = account(RoleName.STAFF, EmployeeStatus.ACTIVE, true, "$2a$10$rehiredhash");
        staff.setUsername("an.nguyen");
        when(userRepository.findByUsername("an@shop.vn")).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeEmail("an@shop.vn")).thenReturn(java.util.List.of(staff));
        when(passwordEncoder.matches(emailedPassword, staff.getPasswordHash())).thenReturn(true);
        when(userRepository.save(staff)).thenReturn(staff);

        assertEquals(11, service.authenticate(login("an@shop.vn", emailedPassword), request(), response()).getUserId());
    }

    @Test
    void rehiredStaffCanPasteTheEmailedPasswordWithSurroundingWhitespace() {
        User staff = account(RoleName.STAFF, EmployeeStatus.ACTIVE, true, "secret");
        when(userRepository.findByUsername("an@shop.vn")).thenReturn(Optional.of(staff));
        when(userRepository.save(staff)).thenReturn(staff);

        assertEquals(11, service.authenticate(login("an@shop.vn", " secret \n"), request(), response()).getUserId());
    }

    @Test
    void terminatedStaffFoundByEmailStillCannotLogin() {
        User staff = account(RoleName.STAFF, EmployeeStatus.TERMINATED, false, "secret");
        staff.setUsername("an.nguyen");
        when(userRepository.findByUsername("an@shop.vn")).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeEmail("an@shop.vn")).thenReturn(java.util.List.of(staff));

        EmploymentEndedException ex = assertThrows(EmploymentEndedException.class,
                () -> service.authenticate(login("an@shop.vn", "secret"), request(), response()));

        assertEquals(EmploymentEndedException.MESSAGE, ex.getMessage());
    }

    @Test
    void adminAndManagerAreNotBlockedByATerminatedEmployeeStatus() {
        User admin = account(RoleName.ADMIN, EmployeeStatus.TERMINATED, true, "secret");
        when(userRepository.findByUsername("admin@shop.vn")).thenReturn(Optional.of(admin));
        when(userRepository.save(admin)).thenReturn(admin);

        assertEquals(RoleName.ADMIN, service.authenticate(login("admin@shop.vn", "secret"), request(), response()).getRoleName());
    }

    private LoginRequest login(String username, String password) {
        return LoginRequest.builder().username(username).password(password).rememberMe(false).build();
    }

    private HttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    private HttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    private User account(RoleName roleName, EmployeeStatus status, boolean active, String password) {
        Employee employee = Employee.builder()
                .id(11)
                .fullName("An Nguyễn")
                .email(roleName == RoleName.ADMIN ? "admin@shop.vn" : "an@shop.vn")
                .status(status)
                .build();
        User user = User.builder()
                .id(roleName == RoleName.ADMIN ? 1 : 11)
                .username(employee.getEmail())
                .passwordHash(password)
                .role(Role.builder().id(3).roleName(roleName).build())
                .employee(employee)
                .isActive(active)
                .build();
        employee.setUser(user);
        return user;
    }
}
