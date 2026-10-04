package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.TemporaryPasswordGenerator;
import com.example.coffee_hrm.dto.request.CreateRecruitmentRequest;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.RecruitmentRequest;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentRequestServiceImplTest {

    @Mock
    private RecruitmentRequestRepository recruitmentRequestRepository;
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
    }

    @Test
    void createStoresPendingRequestAndNotifiesAdmins() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(recruitmentRequestRepository.existsByEmailIgnoreCaseAndStatus("an@shop.vn", RecruitmentStatus.PENDING))
                .thenReturn(false);
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of(adminUser));
        when(recruitmentRequestRepository.save(any())).thenAnswer(invocation -> {
            RecruitmentRequest request = invocation.getArgument(0);
            request.setId(7);
            return request;
        });

        service.create(manager, CreateRecruitmentRequest.builder()
                .fullName(" An Nguyễn ")
                .email("An@Shop.vn")
                .phone("0901 234 567")
                .address("  Quận 1 ")
                .build());

        ArgumentCaptor<RecruitmentRequest> captor = ArgumentCaptor.forClass(RecruitmentRequest.class);
        verify(recruitmentRequestRepository).save(captor.capture());
        RecruitmentRequest saved = captor.getValue();
        assertEquals(RecruitmentStatus.PENDING, saved.getStatus());
        assertEquals("an@shop.vn", saved.getEmail());
        assertEquals("0901234567", saved.getPhone());
        assertEquals("An Nguyễn", saved.getFullName());
        assertEquals("Quận 1", saved.getAddress());
        assertEquals(store, saved.getStore());
        verify(notificationService).notifyUsers(
                eq(List.of(adminUser)),
                eq("Đề xuất tuyển nhân sự mới"),
                eq("Manager Lan đã gửi đề xuất tuyển nhân sự mới cho cửa hàng Store A."),
                eq(NotificationType.RECRUITMENT_REQUEST_CREATED),
                eq("RECRUITMENT_REQUEST"),
                eq(7));
        verifyNoInteractions(accountMailService, passwordEncoder);
    }

    @Test
    void createRejectsEmailAlreadyUsedByAnAccount() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, candidate()));

        assertEquals(RecruitmentRequestServiceImpl.EMAIL_EXISTS, ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsPendingRequestWithSameEmail() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(recruitmentRequestRepository.existsByEmailIgnoreCaseAndStatus("an@shop.vn", RecruitmentStatus.PENDING))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, candidate()));

        assertEquals(RecruitmentRequestServiceImpl.EMAIL_PENDING, ex.getMessage());
        verify(recruitmentRequestRepository, never()).save(any());
    }

    @Test
    void managerCannotApprove() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(manager, 7));
        assertTrue(ex.getMessage().contains("Chỉ Admin"));
        verifyNoInteractions(accountMailService, employeeRepository, passwordEncoder);
    }

    @Test
    void approveCreatesStaffAccountWithoutStoringPlainPassword() {
        RecruitmentRequest request = pendingRequest();
        when(recruitmentRequestRepository.findDetailById(7)).thenReturn(Optional.of(request));
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

        service.approve(admin, 7);

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> rawPassword = ArgumentCaptor.forClass(String.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        verify(userRepository).save(userCaptor.capture());
        verify(passwordEncoder).encode(rawPassword.capture());
        verify(accountMailService).sendTemporaryPassword(
                eq("an@shop.vn"), eq("An Nguyễn"), eq("an@shop.vn"), eq(rawPassword.getValue()));

        Employee employee = employeeCaptor.getValue();
        User account = userCaptor.getValue();
        assertEquals(CertificationStatus.NOTCERTIFIED, employee.getCertificationStatus());
        assertFalse(employee.getHasCertificate());
        assertEquals(EmployeeStatus.ACTIVE, employee.getStatus());
        assertEquals(store, employee.getStore());
        assertEquals(RoleName.STAFF, account.getRole().getRoleName());
        assertEquals("an@shop.vn", account.getUsername());
        assertTrue(account.getIsActive());
        assertTrue(account.getPasswordHash().startsWith("bcrypt:"));
        assertNotEquals(rawPassword.getValue(), account.getPasswordHash());
        assertEquals(RecruitmentStatus.APPROVED, request.getStatus());
        assertNull(request.getRejectReason());
        verify(notificationService).notifyUsers(
                eq(List.of(managerUser)),
                eq("Đề xuất tuyển nhân sự đã được duyệt"),
                eq("Đề xuất tuyển nhân sự An Nguyễn đã được Admin duyệt."),
                eq(NotificationType.RECRUITMENT_REQUEST_APPROVED),
                eq("RECRUITMENT_REQUEST"),
                eq(7));
    }

    @Test
    void approveDoesNotReportSuccessWhenEmailFails() {
        RecruitmentRequest request = pendingRequest();
        when(recruitmentRequestRepository.findDetailById(7)).thenReturn(Optional.of(request));
        when(employeeRepository.existsByEmailIgnoreCase("an@shop.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("an@shop.vn")).thenReturn(false);
        when(roleRepository.findByRoleName(RoleName.STAFF))
                .thenReturn(Optional.of(Role.builder().id(3).roleName(RoleName.STAFF).build()));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new BusinessException(
                "Không gửi được email mật khẩu tạm thời. Hệ thống chưa tạo tài khoản. Vui lòng kiểm tra cấu hình email rồi duyệt lại."))
                .when(accountMailService).sendTemporaryPassword(any(), any(), any(), any());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(admin, 7));

        assertTrue(ex.getMessage().contains("chưa tạo tài khoản"));
        assertFalse(ex.getMessage().toLowerCase().contains("thành công"));
    }

    @Test
    void rejectDoesNotCreateAccountOrSendEmail() {
        RecruitmentRequest request = pendingRequest();
        when(recruitmentRequestRepository.findDetailById(7)).thenReturn(Optional.of(request));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));

        service.reject(admin, 7, "  Chưa đủ hồ sơ  ");

        assertEquals(RecruitmentStatus.REJECTED, request.getStatus());
        assertEquals("Chưa đủ hồ sơ", request.getRejectReason());
        verify(employeeRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(accountMailService, passwordEncoder);
        verify(notificationService).notifyUsers(
                anyList(),
                eq("Đề xuất tuyển nhân sự bị từ chối"),
                eq("Đề xuất tuyển nhân sự An Nguyễn đã bị từ chối."),
                eq(NotificationType.RECRUITMENT_REQUEST_REJECTED),
                eq("RECRUITMENT_REQUEST"),
                eq(7));
    }

    private CreateRecruitmentRequest candidate() {
        return CreateRecruitmentRequest.builder()
                .fullName("An Nguyễn")
                .email("an@shop.vn")
                .phone("0901234567")
                .build();
    }

    private RecruitmentRequest pendingRequest() {
        return RecruitmentRequest.builder()
                .id(7)
                .store(store)
                .requestedBy(managerEmployee)
                .fullName("An Nguyễn")
                .email("an@shop.vn")
                .phone("0901234567")
                .address("Quận 1")
                .status(RecruitmentStatus.PENDING)
                .build();
    }
}
