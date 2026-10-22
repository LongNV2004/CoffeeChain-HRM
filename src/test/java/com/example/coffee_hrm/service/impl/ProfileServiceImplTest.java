package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.Gender;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ChangePasswordRequest;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.MyProfileView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.RecruitmentCandidate;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.RecruitmentCandidateRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.CoffeeRememberMeServices;
import com.example.coffee_hrm.service.TrainingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RecruitmentCandidateRepository recruitmentCandidateRepository;
    @Mock
    private TrainingService trainingService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SecurityContextRepository securityContextRepository;
    @Mock
    private CoffeeRememberMeServices rememberMeServices;

    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProfileServiceImpl(
                userRepository,
                recruitmentCandidateRepository,
                trainingService,
                passwordEncoder,
                securityContextRepository,
                rememberMeServices);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerSeesOnlyTheirOwnProfileAndCurrentCertificates() {
        User manager = account(RoleName.MANAGER, 4, 40);
        when(userRepository.findByIdWithRole(4)).thenReturn(Optional.of(manager));
        when(recruitmentCandidateRepository.findByLinkedEmployeeId(40)).thenReturn(List.of(
                RecruitmentCandidate.builder()
                        .id(8)
                        .dateOfBirth(LocalDate.of(1994, 3, 12))
                        .gender(Gender.MALE)
                        .build()));
        when(trainingService.listCurrentSkillStatuses(40)).thenReturn(List.of(
                EmployeeSkillStatusLine.builder()
                        .skillId(1)
                        .skillName("Pha chế")
                        .certificationStatus(CertificationStatus.CERTIFIED)
                        .certifiedDateLabel("01/10/2026")
                        .build(),
                EmployeeSkillStatusLine.builder()
                        .skillId(2)
                        .skillName("Phục vụ")
                        .certificationStatus(CertificationStatus.NOTCERTIFIED)
                        .build()));

        MyProfileView profile = service.getProfile(AuthenticatedUser.from(manager));

        assertEquals(40, profile.getEmployeeId());
        assertEquals("Quản lý cửa hàng", profile.getRoleLabel());
        assertEquals("manager", profile.getSidebarRole());
        assertEquals("Coffee Quận 1", profile.getStoreName());
        assertEquals("an@shop.vn", profile.getEmail());
        assertEquals(LocalDate.of(1994, 3, 12), profile.getDateOfBirth());
        assertEquals("Nam", profile.getGenderLabel());
        assertEquals(2, profile.getSkillCertificates().size());
        assertEquals(CertificationStatus.CERTIFIED, profile.getSkillCertificates().get(0).getCertificationStatus());
        assertEquals(CertificationStatus.NOTCERTIFIED, profile.getSkillCertificates().get(1).getCertificationStatus());
        verify(trainingService).listCurrentSkillStatuses(40);
        verify(trainingService, never()).listCurrentSkillStatuses(99);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void staffWithoutCertificatesGetsAnEmptyList() {
        User staff = account(RoleName.STAFF, 11, 20);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(staff));
        when(recruitmentCandidateRepository.findByLinkedEmployeeId(20)).thenReturn(List.of());
        when(trainingService.listCurrentSkillStatuses(20)).thenReturn(List.of());

        MyProfileView profile = service.getProfile(AuthenticatedUser.from(staff));

        assertEquals("Nhân viên", profile.getRoleLabel());
        assertEquals("staff", profile.getSidebarRole());
        assertTrue(profile.getSkillCertificates().isEmpty());
        assertNull(profile.getDateOfBirth());
        verify(trainingService).listCurrentSkillStatuses(20);
    }

    @Test
    void passwordChangeRejectsTheWrongCurrentPassword() {
        User staff = account(RoleName.STAFF, 11, 20);
        staff.setPasswordHash("$2a$10$stored");
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches("wrongpass", "$2a$10$stored")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.changePassword(
                AuthenticatedUser.from(staff),
                password("wrongpass", "newpass1", "newpass1"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));

        assertEquals("Mật khẩu hiện tại không chính xác.", ex.getMessage());
        assertFalse(ex.getMessage().contains("wrongpass"));
        assertEquals("$2a$10$stored", staff.getPasswordHash());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void passwordChangeRejectsAConfirmationMismatch() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changePassword(
                actor(RoleName.STAFF, 11),
                password("current1", "newpass1", "newpass2"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));

        assertEquals("Mật khẩu mới và xác nhận mật khẩu không trùng nhau.", ex.getMessage());
        verify(userRepository, never()).findByIdWithRole(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordChangeRejectsANewPasswordOutsideTheExistingLengthRule() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changePassword(
                actor(RoleName.MANAGER, 4),
                password("current1", "12345", "12345"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));

        assertEquals("Mật khẩu mới phải từ 6 đến 100 ký tự.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordChangeKeepsRoleAndSessionOnTheAuthenticatedAccount() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        ProfileServiceImpl local = new ProfileServiceImpl(
                userRepository,
                recruitmentCandidateRepository,
                trainingService,
                encoder,
                securityContextRepository,
                rememberMeServices);
        User staff = account(RoleName.STAFF, 11, 20);
        String previousHash = encoder.encode("current1");
        staff.setPasswordHash(previousHash);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(staff));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("coffee-hrm-remember-me", "old-token"));

        local.changePassword(
                AuthenticatedUser.from(staff),
                password("current1", "newpass1", "newpass1"),
                request,
                new MockHttpServletResponse());

        assertTrue(staff.getPasswordHash().startsWith("$2a$"));
        assertTrue(encoder.matches("newpass1", staff.getPasswordHash()));
        assertFalse(staff.getPasswordHash().contains("newpass1"));
        assertFalse(staff.getPasswordHash().equals(previousHash));
        assertEquals(RoleName.STAFF, staff.getRole().getRoleName());
        assertTrue(staff.getIsActive());
        assertEquals(20, staff.getEmployee().getId());
        assertEquals(11, request.getSession().getAttribute("CURRENT_USER_ID"));
        assertEquals("STAFF", request.getSession().getAttribute("CURRENT_ROLE"));
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(11, principal.getUserId());
        assertEquals(RoleName.STAFF, principal.getRoleName());
        verify(userRepository).findByIdWithRole(11);
        verify(userRepository, never()).findById(any());
        verify(userRepository).save(staff);
        verify(rememberMeServices).loginSuccess(eq(request), any(), any());
    }

    private AuthenticatedUser actor(RoleName roleName, int userId) {
        return AuthenticatedUser.from(account(roleName, userId, userId * 10));
    }

    private ChangePasswordRequest password(String current, String next, String confirm) {
        return ChangePasswordRequest.builder()
                .currentPassword(current)
                .newPassword(next)
                .confirmPassword(confirm)
                .build();
    }

    private User account(RoleName roleName, int userId, int employeeId) {
        Store store = Store.builder().id(4).storeName("Coffee Quận 1").address("1 Lê Lợi").build();
        Employee employee = Employee.builder()
                .id(employeeId)
                .fullName("An Nguyễn")
                .email("an@shop.vn")
                .phone("0901234567")
                .address("12 Nguyễn Huệ")
                .status(EmployeeStatus.ACTIVE)
                .hireDate(LocalDate.of(2024, 1, 15))
                .store(store)
                .build();
        User user = User.builder()
                .id(userId)
                .username("an.nguyen")
                .passwordHash("$2a$10$stored")
                .role(Role.builder().id(3).roleName(roleName).build())
                .employee(employee)
                .isActive(true)
                .build();
        user.setCreatedAt(LocalDateTime.of(2024, 1, 15, 8, 0));
        user.setLastLogin(LocalDateTime.of(2026, 10, 1, 9, 30));
        employee.setUser(user);
        return user;
    }
}
