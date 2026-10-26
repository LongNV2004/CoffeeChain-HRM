package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.Gender;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ChangePasswordRequest;
import com.example.coffee_hrm.dto.request.UpdateProfileRequest;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.MyProfileView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.RecruitmentCandidate;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
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
    private EmployeeRepository employeeRepository;
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
                employeeRepository,
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
                        .skillName("Chứng chỉ nhân viên")
                        .certificationStatus(CertificationStatus.CERTIFIED)
                        .certificationLabel("Đã có chứng chỉ")
                        .certifiedDateLabel("01/10/2026")
                        .build()));

        MyProfileView profile = service.getProfile(AuthenticatedUser.from(manager));

        assertEquals(40, profile.getEmployeeId());
        assertEquals("Quản lý cửa hàng", profile.getRoleLabel());
        assertEquals("manager", profile.getSidebarRole());
        assertEquals("Coffee Quận 1", profile.getStoreName());
        assertEquals("an@shop.vn", profile.getEmail());
        assertEquals("079******123", profile.getCitizenIdMasked());
        assertEquals(LocalDate.of(1994, 3, 12), profile.getDateOfBirth());
        assertEquals("Nam", profile.getGenderLabel());
        assertEquals(1, profile.getSkillCertificates().size());
        assertEquals("Chứng chỉ nhân viên", profile.getSkillCertificates().get(0).getSkillName());
        assertEquals(CertificationStatus.CERTIFIED, profile.getSkillCertificates().get(0).getCertificationStatus());
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
                employeeRepository,
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

    @Test
    void staffAndManagerCanUpdateTheirOwnContactDetails() {
        User staff = account(RoleName.STAFF, 11, 20);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(staff));
        when(employeeRepository.countOtherEmployeesByNormalizedPhone("0912345678", 20)).thenReturn(0);
        when(recruitmentCandidateRepository.existsByPhoneAndStatus("0912345678", RecruitmentStatus.PENDING)).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("binh@shop.vn", 20)).thenReturn(false);
        when(recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus("binh@shop.vn", RecruitmentStatus.PENDING))
                .thenReturn(false);

        service.updateProfile(AuthenticatedUser.from(staff), UpdateProfileRequest.builder()
                .fullName("  Bình   Trần ")
                .phone("091 234 5678")
                .email(" BINH@shop.vn ")
                .address("  20 Lê Lợi  ")
                .build());

        Employee employee = staff.getEmployee();
        assertEquals("Bình Trần", employee.getFullName());
        assertEquals("0912345678", employee.getPhone());
        assertEquals("binh@shop.vn", employee.getEmail());
        assertEquals("20 Lê Lợi", employee.getAddress());
        assertEquals("079203000123", employee.getCitizenId());
        assertEquals(EmployeeStatus.ACTIVE, employee.getStatus());
        assertEquals(4, employee.getStore().getId());
        assertEquals("an.nguyen", staff.getUsername());
        assertEquals(RoleName.STAFF, staff.getRole().getRoleName());
        verify(employeeRepository).save(employee);
        verify(userRepository, never()).save(any());
    }

    @Test
    void profileUpdateRejectsInvalidOrDuplicateContactDetailsAndKeepsTheRecord() {
        User manager = account(RoleName.MANAGER, 4, 40);
        when(userRepository.findByIdWithRole(4)).thenReturn(Optional.of(manager));

        BusinessException name = assertThrows(BusinessException.class, () -> service.updateProfile(
                AuthenticatedUser.from(manager), contact("An1", "0901234567", "an@shop.vn", "12 Nguyễn Huệ")));
        assertEquals("Họ và tên chỉ được chứa chữ cái, không được nhập số.", name.getMessage());

        BusinessException phone = assertThrows(BusinessException.class, () -> service.updateProfile(
                AuthenticatedUser.from(manager), contact("An Nguyễn", "12ab", "an@shop.vn", "12 Nguyễn Huệ")));
        assertEquals("Số điện thoại gồm 9 đến 15 chữ số.", phone.getMessage());
        assertFalse(phone.getMessage().contains("12ab"));

        when(employeeRepository.countOtherEmployeesByNormalizedPhone("0987654321", 40)).thenReturn(1);
        BusinessException taken = assertThrows(BusinessException.class, () -> service.updateProfile(
                AuthenticatedUser.from(manager), contact("An Nguyễn", "0987654321", "an@shop.vn", "12 Nguyễn Huệ")));
        assertEquals("Số điện thoại đã được sử dụng.", taken.getMessage());

        when(employeeRepository.countOtherEmployeesByNormalizedPhone("0987000000", 40)).thenReturn(0);
        when(recruitmentCandidateRepository.existsByPhoneAndStatus("0987000000", RecruitmentStatus.PENDING)).thenReturn(true);
        BusinessException pending = assertThrows(BusinessException.class, () -> service.updateProfile(
                AuthenticatedUser.from(manager), contact("An Nguyễn", "0987000000", "an@shop.vn", "12 Nguyễn Huệ")));
        assertEquals("Số điện thoại đang được dùng trong một đề xuất tuyển dụng.", pending.getMessage());

        when(employeeRepository.countOtherEmployeesByNormalizedPhone("0987111111", 40)).thenReturn(0);
        when(recruitmentCandidateRepository.existsByPhoneAndStatus("0987111111", RecruitmentStatus.PENDING)).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("khac@shop.vn", 40)).thenReturn(true);
        BusinessException email = assertThrows(BusinessException.class, () -> service.updateProfile(
                AuthenticatedUser.from(manager), contact("An Nguyễn", "0987111111", "khac@shop.vn", "12 Nguyễn Huệ")));
        assertEquals("Email đã được sử dụng.", email.getMessage());
        assertFalse(email.getMessage().contains("khac@shop.vn"));

        assertEquals("An Nguyễn", manager.getEmployee().getFullName());
        assertEquals("0901234567", manager.getEmployee().getPhone());
        assertEquals("an@shop.vn", manager.getEmployee().getEmail());
        assertEquals("079203000123", manager.getEmployee().getCitizenId());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void unchangedContactDetailsDoNotNeedAUniquenessLookup() {
        User staff = account(RoleName.STAFF, 11, 20);
        when(userRepository.findByIdWithRole(11)).thenReturn(Optional.of(staff));

        service.updateProfile(AuthenticatedUser.from(staff), contact("Lan Phạm", "090 123 4567", "An@shop.vn", " "));

        assertEquals("Lan Phạm", staff.getEmployee().getFullName());
        assertEquals("0901234567", staff.getEmployee().getPhone());
        assertEquals("an@shop.vn", staff.getEmployee().getEmail());
        assertNull(staff.getEmployee().getAddress());
        assertEquals("079203000123", staff.getEmployee().getCitizenId());
        verify(employeeRepository, never()).countOtherEmployeesByNormalizedPhone(any(), any());
        verify(employeeRepository, never()).existsByEmailIgnoreCaseAndIdNot(any(), any());
    }

    @Test
    void adminCannotUpdateAPersonalProfile() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateProfile(actor(RoleName.ADMIN, 1), contact("An Nguyễn", "0901234567", "an@shop.vn", "1 Lê Lợi")));

        assertEquals("Bạn không có quyền chỉnh sửa hồ sơ này.", ex.getMessage());
        verify(userRepository, never()).findByIdWithRole(any());
        verify(employeeRepository, never()).save(any());
    }

    private UpdateProfileRequest contact(String fullName, String phone, String email, String address) {
        return UpdateProfileRequest.builder()
                .fullName(fullName)
                .phone(phone)
                .email(email)
                .address(address)
                .build();
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
                .citizenId("079203000123")
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
