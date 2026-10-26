package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.TemporaryPasswordGenerator;
import com.example.coffee_hrm.dto.request.CreateStoreManagerRequest;
import com.example.coffee_hrm.dto.response.ManagerProvisionResult;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.RoleRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AccountMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreManagerAccountServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TemporaryPasswordGenerator temporaryPasswordGenerator;
    @Mock
    private AccountMailService accountMailService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private StoreManagerAccountServiceImpl service;
    private AuthenticatedUser admin;
    private Store store;
    private Role managerRole;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new StoreManagerAccountServiceImpl(
                storeRepository,
                employeeRepository,
                userRepository,
                roleRepository,
                passwordEncoder,
                temporaryPasswordGenerator,
                accountMailService,
                transactionManager);
        admin = actor(RoleName.ADMIN);
        managerRole = Role.builder().id(2).roleName(RoleName.MANAGER).build();
        store = Store.builder().id(5).storeName("Coffee Nguyễn Trãi").address("12 Nguyễn Trãi")
                .status(StoreStatus.ACTIVE).isActive(true).build();
    }

    @Test
    void createsManagerAccountHashesPasswordAndSendsEmail() {
        stubOpenStore();
        when(roleRepository.findByRoleName(RoleName.MANAGER)).thenReturn(Optional.of(managerRole));
        when(temporaryPasswordGenerator.generate()).thenReturn("Temp#a12");
        when(passwordEncoder.encode("Temp#a12")).thenReturn("$2a$hash");
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(41);
            return employee;
        });
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ManagerProvisionResult result = service.createManager(admin, 5, request());

        ArgumentCaptor<User> account = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(account.capture());
        assertEquals(RoleName.MANAGER, account.getValue().getRole().getRoleName());
        assertEquals("lan@coffee.vn", account.getValue().getUsername());
        assertEquals("$2a$hash", account.getValue().getPasswordHash());
        assertNotEquals("Temp#a12", account.getValue().getPasswordHash());
        assertFalse(Boolean.TRUE.equals(account.getValue().getMustChangePassword()));
        assertEquals(store.getManager().getId(), Integer.valueOf(41));
        assertEquals(EmployeeStatus.ACTIVE, store.getManager().getStatus());
        verify(accountMailService).sendManagerCredentials(
                "lan@coffee.vn", "Lan Nguyen", "Coffee Nguyễn Trãi", "lan@coffee.vn", "Temp#a12");
        verify(roleRepository, never()).findByRoleName(RoleName.STAFF);
        verify(roleRepository, never()).findByRoleName(RoleName.ADMIN);
        assertTrue(result.isEmailSent());
        assertFalse(result.isResent());
        assertFalse(result.toString().contains("Temp#a12"));
    }

    @Test
    void keepsAccountWhenEmailFails() {
        stubOpenStore();
        when(roleRepository.findByRoleName(RoleName.MANAGER)).thenReturn(Optional.of(managerRole));
        when(temporaryPasswordGenerator.generate()).thenReturn("Temp#a12");
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new BusinessException("Không gửi được email thông tin tài khoản quản lý."))
                .when(accountMailService).sendManagerCredentials(any(), any(), any(), any(), any());

        ManagerProvisionResult result = service.createManager(admin, 5, request());

        assertFalse(result.isEmailSent());
        assertEquals("Lan Nguyen", result.getManagerName());
        verify(employeeRepository, never()).delete(any());
        verify(userRepository, never()).delete(any());
        verify(transactionManager).commit(any());
    }

    @Test
    void rejectsSecondManagerAndInactiveStoreAndDuplicateIdentity() {
        store.setManager(Employee.builder().id(9).fullName("Da co").build());
        when(storeRepository.findByIdForUpdate(5)).thenReturn(Optional.of(store));
        assertEquals(StoreManagerAccountServiceImpl.STORE_ALREADY_HAS_MANAGER,
                assertThrows(BusinessException.class, () -> service.createManager(admin, 5, request())).getMessage());

        store.setManager(null);
        store.setStatus(StoreStatus.INACTIVE);
        assertEquals(StoreManagerAccountServiceImpl.STORE_NOT_ELIGIBLE,
                assertThrows(BusinessException.class, () -> service.createManager(admin, 5, request())).getMessage());

        store.setStatus(StoreStatus.ACTIVE);
        when(employeeRepository.existsByEmailIgnoreCase("lan@coffee.vn")).thenReturn(true);
        assertEquals(StoreManagerAccountServiceImpl.EMAIL_EXISTS,
                assertThrows(BusinessException.class, () -> service.createManager(admin, 5, request())).getMessage());
        verify(employeeRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonAdmin() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createManager(actor(RoleName.MANAGER), 5, request()));
        assertEquals(StoreManagerAccountServiceImpl.ACCESS_DENIED, ex.getMessage());
        verify(storeRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void resendRotatesPasswordWithoutCreatingAnotherAccount() {
        Employee manager = Employee.builder().id(41).fullName("Lan Nguyen").email("lan@coffee.vn").store(store).build();
        User account = User.builder().id(8).username("lan@coffee.vn").passwordHash("old").role(managerRole)
                .employee(manager).mustChangePassword(true).isActive(true).build();
        manager.setUser(account);
        store.setManager(manager);
        when(storeRepository.findByIdForUpdate(5)).thenReturn(Optional.of(store));
        when(userRepository.findByEmployee_Id(41)).thenReturn(Optional.of(account));
        when(temporaryPasswordGenerator.generate()).thenReturn("New#b34");
        when(passwordEncoder.encode("New#b34")).thenReturn("$2a$new");

        ManagerProvisionResult result = service.resendCredentials(admin, 5);

        assertTrue(result.isEmailSent());
        assertTrue(result.isResent());
        assertEquals("$2a$new", account.getPasswordHash());
        assertFalse(Boolean.TRUE.equals(account.getMustChangePassword()));
        verify(employeeRepository, never()).saveAndFlush(any());
        verify(accountMailService).sendManagerCredentials(
                eq("lan@coffee.vn"), eq("Lan Nguyen"), eq("Coffee Nguyễn Trãi"), eq("lan@coffee.vn"), eq("New#b34"));
    }

    @Test
    void mapsConcurrentUniqueViolationToExistingManager() {
        stubOpenStore();
        when(roleRepository.findByRoleName(RoleName.MANAGER)).thenReturn(Optional.of(managerRole));
        when(temporaryPasswordGenerator.generate()).thenReturn("Temp#a12");
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeRepository.saveAndFlush(any(Store.class))).thenThrow(new DataIntegrityViolationException(
                "Cannot insert duplicate key row", new RuntimeException("UX_Stores_ManagerId")));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createManager(admin, 5, request()));

        assertEquals(StoreManagerAccountServiceImpl.STORE_ALREADY_HAS_MANAGER, ex.getMessage());
        verify(accountMailService, never()).sendManagerCredentials(any(), any(), any(), any(), any());
    }

    private void stubOpenStore() {
        when(storeRepository.findByIdForUpdate(5)).thenReturn(Optional.of(store));
        when(userRepository.countOtherStoreMembersWithRole(5, -1, RoleName.MANAGER)).thenReturn(0L);
        when(employeeRepository.existsByEmailIgnoreCase("lan@coffee.vn")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("lan@coffee.vn")).thenReturn(false);
        when(employeeRepository.countByNormalizedPhone("0901234567")).thenReturn(0);
    }

    private CreateStoreManagerRequest request() {
        return CreateStoreManagerRequest.builder()
                .fullName("  Lan Nguyen  ")
                .email("Lan@Coffee.vn")
                .phone("090-123-4567")
                .build();
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder().id(1).username("admin").passwordHash("x").role(role)
                .isActive(true).build());
    }
}
