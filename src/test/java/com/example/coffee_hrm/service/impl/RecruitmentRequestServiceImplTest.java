package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.Gender;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RecruitmentProposalStatus;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.CitizenIds;
import com.example.coffee_hrm.common.security.TemporaryPasswordGenerator;
import com.example.coffee_hrm.dto.request.CreateRecruitmentRequest;
import com.example.coffee_hrm.dto.request.RecruitmentCandidateRequest;
import com.example.coffee_hrm.dto.response.CandidateApprovalResult;
import com.example.coffee_hrm.dto.response.CandidateRejectionResult;
import com.example.coffee_hrm.dto.response.RecruitmentContactCheckResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.RecruitmentCandidate;
import com.example.coffee_hrm.entity.RecruitmentRequest;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.RecruitmentCandidateRepository;
import com.example.coffee_hrm.repository.RecruitmentRequestRepository;
import com.example.coffee_hrm.repository.RoleRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AccountMailService;
import com.example.coffee_hrm.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentRequestServiceImplTest {

    @Mock
    private RecruitmentRequestRepository recruitmentRequestRepository;
    @Mock
    private RecruitmentCandidateRepository recruitmentCandidateRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AccountMailService accountMailService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private RecruitmentRequestServiceImpl service;
    private Store store;
    private Employee managerEmployee;
    private User managerUser;
    private User adminUser;
    private AuthenticatedUser manager;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        service = new RecruitmentRequestServiceImpl(
                recruitmentRequestRepository,
                recruitmentCandidateRepository,
                employeeRepository,
                userRepository,
                storeRepository,
                roleRepository,
                notificationService,
                accountMailService,
                passwordEncoder,
                new TemporaryPasswordGenerator());

        store = Store.builder().id(4).storeName("Store A").address("12 Nguyễn Trãi").build();
        managerEmployee = Employee.builder().id(9).fullName("Lan").email("lan@shop.vn").store(store).build();
        store.setManager(managerEmployee);
        Role managerRole = Role.builder().id(2).roleName(RoleName.MANAGER).build();
        Role adminRole = Role.builder().id(1).roleName(RoleName.ADMIN).build();
        managerUser = User.builder().id(20).username("lan").passwordHash("x").role(managerRole)
                .employee(managerEmployee).isActive(true).build();
        adminUser = User.builder().id(1).username("admin").passwordHash("x").role(adminRole).isActive(true).build();
        manager = AuthenticatedUser.from(managerUser);
        admin = AuthenticatedUser.from(adminUser);
        lenient().when(passwordEncoder.matches(any(), any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            String hash = invocation.getArgument(1);
            return hash != null && hash.equals("bcrypt:" + raw);
        });
    }

    @Test
    void createStoresEveryCandidateAsPendingAndNotifiesAdmins() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        stubAvailable("an@shop.vn", "0901234567");
        stubAvailable("binh@shop.vn", "0907654321");
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of(adminUser));
        when(recruitmentRequestRepository.save(any())).thenAnswer(invocation -> {
            RecruitmentRequest request = invocation.getArgument(0);
            request.setId(7);
            return request;
        });

        service.create(manager, proposal(
                person(" An Nguyễn ", "An@Shop.vn", "0901 234 567", Gender.FEMALE, "  Quận 1 "),
                person("Bình Trần", "binh@shop.vn", "0907654321", Gender.MALE, null)));

        ArgumentCaptor<RecruitmentRequest> captor = ArgumentCaptor.forClass(RecruitmentRequest.class);
        verify(recruitmentRequestRepository).save(captor.capture());
        RecruitmentRequest saved = captor.getValue();
        assertEquals("Tuyển ca tối", saved.getTitle());
        assertEquals(RecruitmentProposalStatus.PENDING, saved.getStatus());
        assertEquals(2, saved.getCandidates().size());
        RecruitmentCandidate first = saved.getCandidates().get(0);
        assertEquals(RecruitmentStatus.PENDING, first.getStatus());
        assertEquals("an@shop.vn", first.getEmail());
        assertEquals("0901234567", first.getPhone());
        assertEquals("An Nguyễn", first.getFullName());
        assertEquals(LocalDate.of(2000, 5, 12), first.getDateOfBirth());
        assertEquals(Gender.FEMALE, first.getGender());
        assertEquals("Quận 1", first.getAddress());
        assertEquals("079901234567", first.getCitizenId());
        assertEquals("079907654321", saved.getCandidates().get(1).getCitizenId());
        assertEquals(saved, first.getRequest());
        assertEquals(RecruitmentStatus.PENDING, saved.getCandidates().get(1).getStatus());
        assertEquals("binh@shop.vn", saved.getCandidates().get(1).getEmail());
        verify(notificationService).notifyUsers(
                eq(List.of(adminUser)),
                eq("Đề xuất tuyển nhân sự mới"),
                eq("Manager Lan đã gửi đề xuất \"Tuyển ca tối\" (2 nhân viên) cho cửa hàng Store A."),
                eq(NotificationType.RECRUITMENT_REQUEST_CREATED),
                eq("RECRUITMENT_REQUEST"),
                eq(7));
        verifyNoInteractions(accountMailService, passwordEncoder);
    }

    @Test
    void createAllowsTheSameFullNameForDifferentCandidates() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        stubAvailable("an@shop.vn", "0901234567");
        stubAvailable("binh@shop.vn", "0907654321");
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of(adminUser));
        when(recruitmentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(manager, proposal(
                person("Nguyễn Văn An", "an@shop.vn", "0901234567", Gender.MALE, null),
                person("Nguyễn Văn An", "binh@shop.vn", "0907654321", Gender.FEMALE, null)));

        ArgumentCaptor<RecruitmentRequest> captor = ArgumentCaptor.forClass(RecruitmentRequest.class);
        verify(recruitmentRequestRepository).save(captor.capture());
        assertEquals("Nguyễn Văn An", captor.getValue().getCandidates().get(0).getFullName());
        assertEquals("Nguyễn Văn An", captor.getValue().getCandidates().get(1).getFullName());
    }

    @Test
    void checkActiveEmployeeContactRejectsEmailAndPhoneUsedByAnActiveEmployee() {
        when(employeeRepository.existsByEmailIgnoreCaseAndStatusIn("an@shop.vn", List.of(EmployeeStatus.ACTIVE)))
                .thenReturn(true);
        when(employeeRepository.countByNormalizedPhoneAndStatus("0901234567", EmployeeStatus.ACTIVE.getDbValue()))
                .thenReturn(1);

        RecruitmentContactCheckResponse result = service.checkActiveEmployeeContact(
                manager, "  An@Shop.vn  ", "0901 234 567");

        assertEquals(RecruitmentRequestServiceImpl.EMAIL_ACTIVE_EMPLOYEE + " (an@shop.vn)", result.getEmailMessage());
        assertEquals(RecruitmentRequestServiceImpl.PHONE_ACTIVE_EMPLOYEE + " (0901234567)", result.getPhoneMessage());
    }

    @Test
    void checkActiveEmployeeContactAllowsContactThatNoActiveEmployeeUses() {
        when(employeeRepository.existsByEmailIgnoreCaseAndStatusIn("moi@shop.vn", List.of(EmployeeStatus.ACTIVE)))
                .thenReturn(false);
        when(employeeRepository.countByNormalizedPhoneAndStatus("0909999999", EmployeeStatus.ACTIVE.getDbValue()))
                .thenReturn(0);

        RecruitmentContactCheckResponse result = service.checkActiveEmployeeContact(
                manager, "moi@shop.vn", "0909999999");

        assertNull(result.getEmailMessage());
        assertNull(result.getPhoneMessage());
    }

    @Test
    void createRejectsDuplicateEmailInsideTheProposal() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                person("An Nguyễn", "an@shop.vn", "0901234567", Gender.MALE, null),
                person("An Khác", "An@Shop.vn", "0907654321", Gender.MALE, null))));

        assertEquals("Email an@shop.vn bị trùng trong danh sách nhân viên.", ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
        verifyNoInteractions(accountMailService);
    }

    @Test
    void createRejectsEmailAlreadyUsedByAnAccount() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(candidate())));

        assertEquals(RecruitmentRequestServiceImpl.EMAIL_EXISTS + " (an@shop.vn)", ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsPendingCandidateWithSameEmail() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus("an@shop.vn", RecruitmentStatus.PENDING))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(candidate())));

        assertEquals(RecruitmentRequestServiceImpl.EMAIL_PENDING + " (an@shop.vn)", ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsPhoneAlreadyUsedByAnEmployee() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus("an@shop.vn", RecruitmentStatus.PENDING))
                .thenReturn(false);
        when(employeeRepository.countByNormalizedPhone("0901234567")).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(candidate())));

        assertEquals(RecruitmentRequestServiceImpl.PHONE_EXISTS + " (0901234567)", ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsPendingCandidateWithSamePhone() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus("an@shop.vn", RecruitmentStatus.PENDING))
                .thenReturn(false);
        when(employeeRepository.countByNormalizedPhone("0901234567")).thenReturn(0);
        when(recruitmentCandidateRepository.existsByPhoneAndStatus("0901234567", RecruitmentStatus.PENDING))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(candidate())));

        assertEquals(RecruitmentRequestServiceImpl.PHONE_PENDING + " (0901234567)", ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsFullNameThatContainsDigitsAfterTrim() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                person("  Nguyen 1  ", "an@shop.vn", "0901234567", Gender.MALE, null))));

        assertEquals(RecruitmentRequestServiceImpl.NAME_NOT_LETTERS, ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsEmailThatIsNotAnEmailAfterTrim() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                person("An Nguyễn", "  khong-phai-email  ", "0901234567", Gender.MALE, null))));

        assertEquals(RecruitmentRequestServiceImpl.EMAIL_INVALID, ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsEmailUsedByAWorkingEmployee() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCaseAndStatusIn(
                "an@shop.vn", List.of(EmployeeStatus.ACTIVE, EmployeeStatus.ON_LEAVE))).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(candidate())));

        assertEquals(RecruitmentRequestServiceImpl.EMAIL_APPROVED_OR_WORKING + " (an@shop.vn)", ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsEmailUsedByAnApprovedCandidate() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(recruitmentCandidateRepository.existsApprovedByEmailIgnoreCase("an@shop.vn")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(candidate())));

        assertEquals(RecruitmentRequestServiceImpl.EMAIL_APPROVED_OR_WORKING + " (an@shop.vn)", ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsTitleAlreadySentToAdminAfterTrim() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(recruitmentRequestRepository.existsByTitleIgnoreCase("Tuyển ca tối")).thenReturn(true);
        CreateRecruitmentRequest request = CreateRecruitmentRequest.builder()
                .title("  Tuyển ca tối  ")
                .candidates(new ArrayList<>(List.of(candidate())))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, request));

        assertEquals(RecruitmentRequestServiceImpl.TITLE_EXISTS, ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsDateOfBirthThatIsNotInThePast() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        RecruitmentCandidateRequest person = candidate();
        person.setDateOfBirth(LocalDate.now().plusDays(1));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(person)));

        assertEquals("Ngày sinh phải là ngày trong quá khứ.", ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void managerCannotApprove() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approveCandidate(manager, 7, 11));
        assertTrue(ex.getMessage().contains("Chỉ Admin"));
        verifyNoInteractions(accountMailService, employeeRepository, passwordEncoder, recruitmentCandidateRepository);
    }

    @Test
    void approveCreatesAccountOnlyForTheSelectedCandidate() {
        RecruitmentRequest request = pendingRequest();
        lock(request);
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(roleRepository.findByRoleName(RoleName.STAFF))
                .thenReturn(Optional.of(Role.builder().id(3).roleName(RoleName.STAFF).build()));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "bcrypt:" + invocation.getArgument(0));
        when(employeeRepository.save(any())).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(50);
            return employee;
        });
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        request.getCandidates().get(0).setCitizenId("079203000123");
        service.approveCandidate(admin, 7, 11);

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> rawPassword = ArgumentCaptor.forClass(String.class);
        verify(employeeRepository, times(1)).save(employeeCaptor.capture());
        verify(userRepository, times(1)).save(userCaptor.capture());
        verify(passwordEncoder).encode(rawPassword.capture());
        verify(accountMailService).sendTemporaryPassword(
                eq("an@shop.vn"), eq("An Nguyễn"), eq("an@shop.vn"), eq(rawPassword.getValue()));
        verify(accountMailService, never()).sendRecruitmentRejection(any(), any(), any());

        Employee employee = employeeCaptor.getValue();
        User account = userCaptor.getValue();
        assertEquals(CertificationStatus.NOTCERTIFIED, employee.getCertificationStatus());
        assertFalse(employee.getHasCertificate());
        assertEquals(EmployeeStatus.ACTIVE, employee.getStatus());
        assertEquals("079203000123", employee.getCitizenId());
        assertEquals(store, employee.getStore());
        assertEquals(RoleName.STAFF, account.getRole().getRoleName());
        assertEquals("an@shop.vn", account.getUsername());
        assertTrue(account.getIsActive());
        assertTrue(account.getPasswordHash().startsWith("bcrypt:"));
        assertNotEquals(rawPassword.getValue(), account.getPasswordHash());

        RecruitmentCandidate approved = request.getCandidates().get(0);
        RecruitmentCandidate waiting = request.getCandidates().get(1);
        assertEquals(RecruitmentStatus.APPROVED, approved.getStatus());
        assertEquals(50, approved.getCreatedEmployee().getId());
        assertNull(approved.getRejectReason());
        assertEquals(RecruitmentStatus.PENDING, waiting.getStatus());
        assertNull(waiting.getCreatedEmployee());
        assertEquals(RecruitmentProposalStatus.PARTIALLY_PROCESSED, request.getStatus());
        verify(notificationService).notifyUsers(
                eq(List.of(managerUser)),
                eq("Đề xuất tuyển nhân sự đã được duyệt"),
                eq("Nhân viên An Nguyễn trong đề xuất \"Tuyển ca tối\" đã được Admin duyệt."),
                eq(NotificationType.RECRUITMENT_REQUEST_APPROVED),
                eq("RECRUITMENT_REQUEST"),
                eq(7));
    }

    @Test
    void approveLastPendingCandidateCompletesTheProposal() {
        RecruitmentRequest request = pendingRequest();
        RecruitmentCandidate first = request.getCandidates().get(0);
        first.setStatus(RecruitmentStatus.APPROVED);
        first.setCreatedEmployee(Employee.builder().id(40).fullName("An Nguyễn").build());
        lock(request);
        when(employeeRepository.existsByEmailIgnoreCase("binh@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("binh@shop.vn")).thenReturn(false);
        when(roleRepository.findByRoleName(RoleName.STAFF))
                .thenReturn(Optional.of(Role.builder().id(3).roleName(RoleName.STAFF).build()));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "bcrypt:" + invocation.getArgument(0));
        when(employeeRepository.save(any())).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(51);
            return employee;
        });
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approveCandidate(admin, 7, 12);

        assertEquals(RecruitmentStatus.APPROVED, first.getStatus());
        assertEquals(40, first.getCreatedEmployee().getId());
        assertEquals(RecruitmentStatus.APPROVED, request.getCandidates().get(1).getStatus());
        assertEquals(51, request.getCandidates().get(1).getCreatedEmployee().getId());
        assertEquals(RecruitmentProposalStatus.COMPLETED, request.getStatus());
        verify(employeeRepository, times(1)).save(any());
        verify(accountMailService).sendTemporaryPassword(eq("binh@shop.vn"), eq("Bình Trần"), eq("binh@shop.vn"), any());
        verify(accountMailService, never()).sendRecruitmentRejection(any(), any(), any());
    }

    @Test
    void approveAgainDoesNotCreateAnotherAccount() {
        RecruitmentRequest request = pendingRequest();
        RecruitmentCandidate first = request.getCandidates().get(0);
        first.setStatus(RecruitmentStatus.APPROVED);
        first.setCreatedEmployee(Employee.builder().id(50).fullName("An Nguyễn").build());
        lock(request);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approveCandidate(admin, 7, 11));

        assertEquals(RecruitmentRequestServiceImpl.ALREADY_APPROVED, ex.getMessage());
        assertEquals(RecruitmentStatus.PENDING, request.getCandidates().get(1).getStatus());
        verify(employeeRepository, never()).save(any());
        verifyNoInteractions(accountMailService, passwordEncoder);
    }

    @Test
    void approveDoesNotReportSuccessWhenEmailFails() {
        RecruitmentRequest request = pendingRequest();
        lock(request);
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(roleRepository.findByRoleName(RoleName.STAFF))
                .thenReturn(Optional.of(Role.builder().id(3).roleName(RoleName.STAFF).build()));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "bcrypt:" + invocation.getArgument(0));
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new BusinessException(
                "Không gửi được email mật khẩu tạm thời. Hệ thống chưa tạo tài khoản. Vui lòng kiểm tra cấu hình email rồi duyệt lại."))
                .when(accountMailService).sendTemporaryPassword(any(), any(), any(), any());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approveCandidate(admin, 7, 11));

        assertTrue(ex.getMessage().contains("chưa tạo tài khoản"));
        assertFalse(ex.getMessage().toLowerCase().contains("thành công"));
        verify(accountMailService, never()).sendRecruitmentRejection(any(), any(), any());
    }

    @Test
    void rejectDoesNotCreateAccountAndLeavesTheOtherCandidatePending() {
        RecruitmentRequest request = pendingRequest();
        lock(request);
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(accountMailService.sendRecruitmentRejection("an@shop.vn", "An Nguyễn", "Chưa đủ hồ sơ")).thenReturn(true);

        CandidateRejectionResult result = service.rejectCandidate(admin, 7, 11, "  Chưa đủ hồ sơ  ");

        assertEquals("An Nguyễn", result.fullName());
        assertTrue(result.noticeEmailSent());
        assertEquals(RecruitmentStatus.REJECTED, request.getCandidates().get(0).getStatus());
        assertEquals("Chưa đủ hồ sơ", request.getCandidates().get(0).getRejectReason());
        assertNull(request.getCandidates().get(0).getCreatedEmployee());
        assertEquals(RecruitmentStatus.PENDING, request.getCandidates().get(1).getStatus());
        assertNull(request.getCandidates().get(1).getRejectReason());
        assertEquals(RecruitmentProposalStatus.PARTIALLY_PROCESSED, request.getStatus());
        verify(employeeRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(accountMailService, times(1)).sendRecruitmentRejection("an@shop.vn", "An Nguyễn", "Chưa đủ hồ sơ");
        verify(accountMailService, never()).sendRecruitmentRejection(eq("binh@shop.vn"), any(), any());
        verify(accountMailService, never()).sendTemporaryPassword(any(), any(), any(), any());
        verifyNoInteractions(passwordEncoder);
        verify(notificationService).notifyUsers(
                anyList(),
                eq("Đề xuất tuyển nhân sự bị từ chối"),
                eq("Nhân viên An Nguyễn trong đề xuất \"Tuyển ca tối\" đã bị từ chối."),
                eq(NotificationType.RECRUITMENT_REQUEST_REJECTED),
                eq("RECRUITMENT_REQUEST"),
                eq(7));
    }

    @Test
    void viewingPendingProposalDoesNotSendRejectionEmail() {
        RecruitmentRequest request = pendingRequest();
        when(recruitmentRequestRepository.findDetailById(7)).thenReturn(Optional.of(request));
        when(recruitmentRequestRepository.search(null, null, RecruitmentProposalStatus.PENDING, null, null))
                .thenReturn(List.of(request));

        service.getForAdmin(admin, 7);
        service.listForAdmin(admin, null, null, RecruitmentProposalStatus.PENDING, null, null);

        verifyNoInteractions(accountMailService);
    }

    @Test
    void rejectingAgainDoesNotSendAnotherEmail() {
        RecruitmentRequest request = pendingRequest();
        lock(request);
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(accountMailService.sendRecruitmentRejection("an@shop.vn", "An Nguyễn", "Chưa đủ hồ sơ")).thenReturn(true);

        service.rejectCandidate(admin, 7, 11, "Chưa đủ hồ sơ");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rejectCandidate(admin, 7, 11, "Lý do khác"));

        assertEquals("Nhân viên này đã bị từ chối.", ex.getMessage());
        assertEquals(RecruitmentStatus.REJECTED, request.getCandidates().get(0).getStatus());
        assertEquals("Chưa đủ hồ sơ", request.getCandidates().get(0).getRejectReason());
        assertEquals(RecruitmentStatus.PENDING, request.getCandidates().get(1).getStatus());
        verify(accountMailService, times(1)).sendRecruitmentRejection("an@shop.vn", "An Nguyễn", "Chưa đủ hồ sơ");
    }

    @Test
    void rejectionStaysSavedWhenNoticeEmailFails() {
        RecruitmentRequest request = pendingRequest();
        lock(request);
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        doThrow(new RuntimeException("SMTP down"))
                .when(accountMailService).sendRecruitmentRejection(any(), any(), any());

        CandidateRejectionResult result = service.rejectCandidate(admin, 7, 11, null);

        assertEquals("An Nguyễn", result.fullName());
        assertFalse(result.noticeEmailSent());
        assertEquals(RecruitmentStatus.REJECTED, request.getCandidates().get(0).getStatus());
        assertEquals(RecruitmentStatus.PENDING, request.getCandidates().get(1).getStatus());
        verify(recruitmentRequestRepository).save(request);
        verify(recruitmentRequestRepository).flush();
    }

    @Test
    void invalidCandidateEmailDoesNotUndoRejection() {
        RecruitmentRequest request = pendingRequest();
        request.getCandidates().get(0).setEmail("khong-hop-le");
        lock(request);
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(accountMailService.sendRecruitmentRejection("khong-hop-le", "An Nguyễn", null)).thenReturn(false);

        CandidateRejectionResult result = service.rejectCandidate(admin, 7, 11, " ");

        assertFalse(result.noticeEmailSent());
        assertEquals(RecruitmentStatus.REJECTED, request.getCandidates().get(0).getStatus());
        assertNull(request.getCandidates().get(0).getRejectReason());
        assertEquals(RecruitmentStatus.PENDING, request.getCandidates().get(1).getStatus());
        verify(accountMailService).sendRecruitmentRejection("khong-hop-le", "An Nguyễn", null);
        verify(accountMailService, never()).sendRecruitmentRejection(eq("binh@shop.vn"), any(), any());
    }

    @Test
    void createAllowsRehireOfATerminatedEmployeeWithoutActivatingTheAccount() {
        Employee former = terminatedStaff();
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByEmailIgnoreCaseWithAccount("an@shop.vn")).thenReturn(Optional.of(former));
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of(adminUser));
        when(recruitmentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(manager, proposal(candidate()));

        ArgumentCaptor<RecruitmentRequest> captor = ArgumentCaptor.forClass(RecruitmentRequest.class);
        verify(recruitmentRequestRepository).save(captor.capture());
        RecruitmentCandidate saved = captor.getValue().getCandidates().get(0);
        assertEquals(former, saved.getRehireEmployee());
        assertEquals(RecruitmentStatus.PENDING, saved.getStatus());
        assertEquals(EmployeeStatus.TERMINATED, former.getStatus());
        assertFalse(former.getUser().getIsActive());
        verify(employeeRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(accountMailService, passwordEncoder);
    }

    @Test
    void createRejectsRehireOfAWorkingEmployee() {
        Employee working = terminatedStaff();
        working.setStatus(EmployeeStatus.ACTIVE);
        working.getUser().setIsActive(true);
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByIdWithStoreAccount(30)).thenReturn(Optional.of(working));
        RecruitmentCandidateRequest person = candidate();
        person.setRehireEmployeeId(30);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(person)));

        assertEquals(RecruitmentRequestServiceImpl.REHIRE_WORKING, ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createRejectsASecondPendingRehireForTheSameEmployee() {
        Employee former = terminatedStaff();
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByEmailIgnoreCaseWithAccount("an@shop.vn")).thenReturn(Optional.of(former));
        when(recruitmentCandidateRepository.existsByRehireEmployee_IdAndStatus(30, RecruitmentStatus.PENDING))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(candidate())));

        assertTrue(ex.getMessage().contains(RecruitmentRequestServiceImpl.REHIRE_PENDING));
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void approveRehireReactivatesTheSameAccountAndDoesNotCreateAnother() {
        RecruitmentRequest request = pendingRequest();
        Employee former = terminatedStaff();
        LocalDate originalHireDate = former.getHireDate();
        request.getCandidates().get(0).setCitizenId("079203000123");
        request.getCandidates().get(0).setRehireEmployee(former);
        lock(request);
        when(employeeRepository.lockByIdForLeaveRequest(30)).thenReturn(Optional.of(former));
        when(roleRepository.findByRoleName(RoleName.STAFF))
                .thenReturn(Optional.of(Role.builder().id(3).roleName(RoleName.STAFF).build()));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "bcrypt:" + invocation.getArgument(0));
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateApprovalResult result = service.approveCandidate(admin, 7, 11);

        assertTrue(result.reactivatedExistingAccount());
        assertEquals(EmployeeStatus.ACTIVE, former.getStatus());
        assertNull(former.getTerminationDate());
        assertEquals(originalHireDate, former.getHireDate());
        assertEquals("079203000123", former.getCitizenId());
        assertEquals(CertificationStatus.CERTIFIED, former.getCertificationStatus());
        assertEquals(store, former.getStore());
        assertTrue(former.getUser().getIsActive());
        assertEquals(80, former.getUser().getId());
        assertTrue(former.getUser().getPasswordHash().startsWith("bcrypt:"));
        assertNotEquals(former.getUser().getPasswordHash().substring("bcrypt:".length()), former.getUser().getPasswordHash());
        assertEquals(30, request.getCandidates().get(0).getCreatedEmployee().getId());
        assertEquals(RecruitmentStatus.PENDING, request.getCandidates().get(1).getStatus());
        verify(employeeRepository, times(1)).save(any());
        verify(userRepository, times(1)).save(any());
        verify(accountMailService).sendTemporaryPassword(eq("an@shop.vn"), eq("An Nguyễn"), eq("an@shop.vn"), any());
    }

    @Test
    void approveRehireLetsTheEmployeeEmailLogInWithTheMailedPassword() {
        RecruitmentRequest request = pendingRequest();
        Employee former = terminatedStaff();
        former.getUser().setUsername("an.nguyen");
        former.setCitizenId("079203000123");
        request.getCandidates().get(0).setCitizenId("079203000123");
        request.getCandidates().get(0).setRehireEmployee(former);
        lock(request);
        when(employeeRepository.lockByIdForLeaveRequest(30)).thenReturn(Optional.of(former));
        when(roleRepository.findByRoleName(RoleName.STAFF))
                .thenReturn(Optional.of(Role.builder().id(3).roleName(RoleName.STAFF).build()));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "bcrypt:" + invocation.getArgument(0));
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approveCandidate(admin, 7, 11);

        ArgumentCaptor<String> mailedPassword = ArgumentCaptor.forClass(String.class);
        verify(accountMailService).sendTemporaryPassword(
                eq("an@shop.vn"), eq("An Nguyễn"), eq("an@shop.vn"), mailedPassword.capture());
        assertEquals("an@shop.vn", former.getUser().getUsername());
        assertEquals("bcrypt:" + mailedPassword.getValue(), former.getUser().getPasswordHash());
        assertTrue(former.getUser().getIsActive());
        assertEquals(EmployeeStatus.ACTIVE, former.getStatus());
        assertNull(former.getTerminationDate());
    }

    @Test
    void rejectRehireLeavesTheOldAccountDisabled() {
        RecruitmentRequest request = pendingRequest();
        Employee former = terminatedStaff();
        former.setCitizenId("079203000123");
        request.getCandidates().get(0).setCitizenId("079203000123");
        request.getCandidates().get(0).setRehireEmployee(former);
        lock(request);
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(accountMailService.sendRecruitmentRejection("an@shop.vn", "An Nguyễn", null)).thenReturn(true);

        service.rejectCandidate(admin, 7, 11, null);

        assertEquals(EmployeeStatus.TERMINATED, former.getStatus());
        assertEquals("079203000123", former.getCitizenId());
        assertFalse(former.getUser().getIsActive());
        assertEquals(RecruitmentStatus.REJECTED, request.getCandidates().get(0).getStatus());
        verify(employeeRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(accountMailService, never()).sendTemporaryPassword(any(), any(), any(), any());
    }

    @Test
    void createRejectsTwoCandidatesWithTheSameCitizenId() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        RecruitmentCandidateRequest first = candidate();
        RecruitmentCandidateRequest second = person("Bình Trần", "binh@shop.vn", "0907654321", Gender.MALE, null);
        second.setCitizenId(first.getCitizenId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(manager, proposal(first, second)));

        assertEquals("Số căn cước công dân bị trùng trong danh sách nhân viên.", ex.getMessage());
        assertFalse(ex.getMessage().contains(first.getCitizenId()));
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsACitizenIdThatAlreadyBelongsToAWorkingEmployee() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        Employee working = Employee.builder().id(40).fullName("Đang làm").status(EmployeeStatus.ACTIVE).build();
        when(employeeRepository.findByCitizenId(candidate().getCitizenId())).thenReturn(Optional.of(working));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(manager, proposal(candidate())));

        assertEquals(CitizenIds.DUPLICATE_MESSAGE, ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createRejectsACitizenIdAlreadyWaitingOnAnotherProposal() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus("an@shop.vn", RecruitmentStatus.PENDING))
                .thenReturn(false);
        when(recruitmentCandidateRepository.existsByCitizenIdAndStatus(
                candidate().getCitizenId(), RecruitmentStatus.PENDING)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(manager, proposal(candidate())));

        assertEquals(RecruitmentRequestServiceImpl.CITIZEN_ID_PENDING, ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRehireByCitizenIdUsesTheExistingProfile() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        Employee former = terminatedStaff();
        former.setCitizenId("079911111111");
        when(employeeRepository.countByNormalizedPhone("0911111111")).thenReturn(0);
        when(recruitmentCandidateRepository.existsByPhoneAndStatus("0911111111", RecruitmentStatus.PENDING))
                .thenReturn(false);
        when(employeeRepository.findByCitizenId("079911111111")).thenReturn(Optional.of(former));
        when(recruitmentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(manager, proposal(person("An Nguyễn", "moi@shop.vn", "0911111111", Gender.MALE, null)));

        ArgumentCaptor<RecruitmentRequest> captor = ArgumentCaptor.forClass(RecruitmentRequest.class);
        verify(recruitmentRequestRepository).save(captor.capture());
        assertEquals(former, captor.getValue().getCandidates().get(0).getRehireEmployee());
        verify(employeeRepository, never()).save(any());
    }

    private Employee terminatedStaff() {
        Store oldStore = Store.builder().id(8).storeName("Store B").build();
        Employee former = Employee.builder()
                .id(30)
                .fullName("An Nguyễn")
                .email("an@shop.vn")
                .phone("0901234567")
                .store(oldStore)
                .status(EmployeeStatus.TERMINATED)
                .hireDate(LocalDate.of(2019, 1, 1))
                .terminationDate(LocalDate.of(2024, 6, 1))
                .certificationStatus(CertificationStatus.CERTIFIED)
                .hasCertificate(true)
                .build();
        User account = User.builder()
                .id(80)
                .username("an@shop.vn")
                .passwordHash("old-hash")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .employee(former)
                .isActive(false)
                .build();
        former.setUser(account);
        return former;
    }

    private void stubAvailable(String email, String phone) {
        when(employeeRepository.existsByEmailIgnoreCase(email)).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(email)).thenReturn(false);
        when(recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus(email, RecruitmentStatus.PENDING))
                .thenReturn(false);
        when(employeeRepository.countByNormalizedPhone(phone)).thenReturn(0);
        when(recruitmentCandidateRepository.existsByPhoneAndStatus(phone, RecruitmentStatus.PENDING)).thenReturn(false);
    }

    private void lock(RecruitmentRequest request) {
        when(recruitmentRequestRepository.lockById(request.getId())).thenReturn(Optional.of(request));
        when(recruitmentCandidateRepository.lockByRequestId(request.getId()))
                .thenReturn(new ArrayList<>(request.getCandidates()));
    }

    private CreateRecruitmentRequest proposal(RecruitmentCandidateRequest... people) {
        return CreateRecruitmentRequest.builder()
                .title("Tuyển ca tối")
                .candidates(new ArrayList<>(List.of(people)))
                .build();
    }

    private RecruitmentCandidateRequest candidate() {
        return person("An Nguyễn", "an@shop.vn", "0901234567", Gender.MALE, null);
    }

    private String citizenIdFrom(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() < 9) {
            digits = ("000000000" + digits).substring(digits.length());
        }
        return "079" + digits.substring(digits.length() - 9);
    }

    private RecruitmentCandidateRequest person(String fullName, String email, String phone, Gender gender, String address) {
        return RecruitmentCandidateRequest.builder()
                .fullName(fullName)
                .dateOfBirth(LocalDate.of(2000, 5, 12))
                .gender(gender)
                .email(email)
                .phone(phone)
                .citizenId(citizenIdFrom(phone))
                .address(address)
                .build();
    }

    private RecruitmentRequest pendingRequest() {
        RecruitmentRequest request = RecruitmentRequest.builder()
                .id(7)
                .store(store)
                .requestedBy(managerEmployee)
                .title("Tuyển ca tối")
                .status(RecruitmentProposalStatus.PENDING)
                .candidates(new ArrayList<>())
                .build();
        request.getCandidates().add(candidateEntity(request, 11, 1, "An Nguyễn", "an@shop.vn", "0901234567"));
        request.getCandidates().add(candidateEntity(request, 12, 2, "Bình Trần", "binh@shop.vn", "0907654321"));
        return request;
    }

    private RecruitmentCandidate candidateEntity(RecruitmentRequest request, int id, int sortOrder,
                                                 String fullName, String email, String phone) {
        return RecruitmentCandidate.builder()
                .id(id)
                .request(request)
                .sortOrder(sortOrder)
                .fullName(fullName)
                .dateOfBirth(LocalDate.of(2000, 5, 12))
                .gender(Gender.MALE)
                .email(email)
                .phone(phone)
                .address("Quận 1")
                .status(RecruitmentStatus.PENDING)
                .build();
    }
}
