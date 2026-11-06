package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
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
    private EmploymentContractRepository contractRepository;
    @Mock
    private FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lockedTerminatedStaffIsSentToLogin() throws Exception {
        User staff = account(RoleName.STAFF, EmployeeStatus.TERMINATED, false);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(staff));
        authenticate(staff);
        MockHttpServletRequest dashboard = new MockHttpServletRequest("GET", "/dashboard/staff");
        MockHttpServletResponse dashboardResponse = new MockHttpServletResponse();

        new EmploymentAccessFilter(provider(), contracts()).doFilter(dashboard, dashboardResponse, chain);

        assertEquals("/login?loginError=true&errorCode=inactive", dashboardResponse.getRedirectedUrl());
        verify(chain, never()).doFilter(dashboard, dashboardResponse);
    }

    @Test
    void terminatedStaffCheckInIsRejected() throws Exception {
        User staff = account(RoleName.STAFF, EmployeeStatus.TERMINATED, true);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(staff));
        authenticate(staff);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/attendance/staff/check-in");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EmploymentAccessFilter(provider(), contracts()).doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void otherStaffAndAdminKeepTheirSession() throws Exception {
        User admin = account(RoleName.ADMIN, EmployeeStatus.ACTIVE, true);
        when(userRepository.findByIdWithRole(1)).thenReturn(Optional.of(admin));
        authenticate(admin);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EmploymentAccessFilter(provider(), contracts()).doFilter(request, response, chain);

        assertNull(response.getRedirectedUrl());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        verify(chain).doFilter(request, response);
    }

    @Test
    void managerWithoutAContractCanEditProfileButNotOpenTheStore() throws Exception {
        User manager = account(RoleName.MANAGER, EmployeeStatus.ACTIVE, true);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(manager));
        when(contractRepository.findByEmployeeId(11)).thenReturn(java.util.List.of());
        EmploymentAccessFilter filter = new EmploymentAccessFilter(provider(), contractsReturningRepository());

        authenticate(manager);
        MockHttpServletRequest profile = new MockHttpServletRequest("POST", "/profile");
        MockHttpServletResponse profileResponse = new MockHttpServletResponse();
        filter.doFilter(profile, profileResponse, chain);
        assertNull(profileResponse.getRedirectedUrl());
        verify(chain).doFilter(profile, profileResponse);

        authenticate(manager);
        MockHttpServletRequest dashboard = new MockHttpServletRequest("GET", "/dashboard/manager");
        MockHttpServletResponse dashboardResponse = new MockHttpServletResponse();
        filter.doFilter(dashboard, dashboardResponse, chain);
        assertEquals("/profile?limited=contract", dashboardResponse.getRedirectedUrl());

        authenticate(manager);
        MockHttpServletRequest employees = new MockHttpServletRequest("GET", "/manager/employees");
        MockHttpServletResponse employeesResponse = new MockHttpServletResponse();
        filter.doFilter(employees, employeesResponse, chain);
        assertEquals("/profile?limited=contract", employeesResponse.getRedirectedUrl());
    }

    @Test
    void managerWithContractInForceKeepsTheDashboard() throws Exception {
        User manager = account(RoleName.MANAGER, EmployeeStatus.ACTIVE, true);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(manager));
        EmploymentContract own = EmploymentContract.builder()
                .id(4)
                .employee(manager.getEmployee())
                .startDate(java.time.LocalDate.now().minusDays(3))
                .endDate(java.time.LocalDate.now().plusMonths(6))
                .build();
        when(contractRepository.findByEmployeeId(11)).thenReturn(java.util.List.of(own));
        EmploymentAccessFilter filter = new EmploymentAccessFilter(provider(), contractsReturningRepository());

        authenticate(manager);
        MockHttpServletRequest dashboard = new MockHttpServletRequest("GET", "/dashboard/manager");
        MockHttpServletResponse dashboardResponse = new MockHttpServletResponse();
        filter.doFilter(dashboard, dashboardResponse, chain);
        assertNull(dashboardResponse.getRedirectedUrl());
        verify(chain).doFilter(dashboard, dashboardResponse);
    }

    @Test
    void limitedManagerCanDownloadOnlyTheirOwnContractFile() throws Exception {
        User manager = account(RoleName.MANAGER, EmployeeStatus.ACTIVE, true);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(manager));
        when(contractRepository.findByEmployeeId(11)).thenReturn(java.util.List.of());
        EmploymentContract own = EmploymentContract.builder()
                .id(4)
                .employee(manager.getEmployee())
                .build();
        when(contractRepository.findDetailById(4)).thenReturn(Optional.of(own));
        when(contractRepository.findDetailById(9)).thenReturn(Optional.of(EmploymentContract.builder()
                .id(9)
                .employee(Employee.builder().id(30).fullName("Nhân viên").build())
                .build()));
        EmploymentAccessFilter filter = new EmploymentAccessFilter(provider(), contractsReturningRepository());

        authenticate(manager);
        MockHttpServletRequest ownFile = new MockHttpServletRequest("GET", "/manager/contract/4/file");
        MockHttpServletResponse ownResponse = new MockHttpServletResponse();
        filter.doFilter(ownFile, ownResponse, chain);
        assertNull(ownResponse.getRedirectedUrl());

        authenticate(manager);
        MockHttpServletRequest otherFile = new MockHttpServletRequest("GET", "/manager/contract/9/file");
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();
        filter.doFilter(otherFile, otherResponse, chain);
        assertEquals("/profile?limited=contract", otherResponse.getRedirectedUrl());
    }

    private ObjectProvider<UserRepository> provider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<UserRepository> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(userRepository);
        return provider;
    }

    private ObjectProvider<EmploymentContractRepository> contracts() {
        @SuppressWarnings("unchecked")
        ObjectProvider<EmploymentContractRepository> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        return provider;
    }

    private ObjectProvider<EmploymentContractRepository> contractsReturningRepository() {
        @SuppressWarnings("unchecked")
        ObjectProvider<EmploymentContractRepository> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(contractRepository);
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
