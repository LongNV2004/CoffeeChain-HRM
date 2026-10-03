package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.RoleRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private final Role managerRole = Role.builder().id(2).roleName(RoleName.MANAGER).build();
    private final Role staffRole = Role.builder().id(3).roleName(RoleName.STAFF).build();

    private AuthenticatedUser admin;
    private Store storeA;
    private Store storeB;
    private Employee staffEmployee;
    private User staffUser;

    @BeforeEach
    void setUp() {
        admin = actor(RoleName.ADMIN);
        storeA = Store.builder().id(1).storeName("Store A").address("A").build();
        storeB = Store.builder().id(2).storeName("Store B").address("B").build();
        staffEmployee = employee(10, storeA, EmployeeStatus.ACTIVE);
        staffUser = User.builder().id(100).username("staff").passwordHash("x").role(staffRole)
                .employee(staffEmployee).isActive(true).build();

        when(roleRepository.findByRoleName(RoleName.MANAGER)).thenReturn(Optional.of(managerRole));
        when(roleRepository.findByRoleName(RoleName.STAFF)).thenReturn(Optional.of(staffRole));
        when(employeeRepository.findById(10)).thenReturn(Optional.of(staffEmployee));
        when(storeRepository.findByIdForUpdate(1)).thenReturn(Optional.of(storeA));
        when(storeRepository.findByIdForUpdate(2)).thenReturn(Optional.of(storeB));
        when(userRepository.findByEmployee_Id(10)).thenReturn(Optional.of(staffUser));
        when(storeRepository.findAllByManager_Id(anyInt())).thenReturn(List.of());
    }

    @Test
    void promotesStaffToManagerOfOwnStoreWithoutCreatingRecords() {
        EmployeeResponse result = employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER");

        assertSame(managerRole, staffUser.getRole());
        assertSame(staffEmployee, storeA.getManager());
        assertEquals(RoleName.MANAGER, result.getRoleName());
        assertTrue(result.isStoreManager());
        verify(userRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
        verify(storeRepository, never()).save(any());
    }

    @Test
    void roleParameterIsCaseInsensitive() {
        employeeService.changeEmployeeRole(admin, 1, 10, " manager ");

        assertSame(managerRole, staffUser.getRole());
    }

    @Test
    void demotesManagerToStaffAndClearsStoreManager() {
        staffUser.setRole(managerRole);
        storeA.setManager(staffEmployee);
        when(storeRepository.findAllByManager_Id(10)).thenReturn(List.of(storeA));

        EmployeeResponse result = employeeService.changeEmployeeRole(admin, 1, 10, "STAFF");

        assertSame(staffRole, staffUser.getRole());
        assertNull(storeA.getManager());
        assertEquals(RoleName.STAFF, result.getRoleName());
        assertFalse(result.isStoreManager());
    }

    @Test
    void rejectsUnknownEmployee() {
        when(employeeRepository.findById(99)).thenReturn(Optional.empty());

        assertMessage(EmployeeServiceImpl.EMPLOYEE_NOT_FOUND,
                () -> employeeService.changeEmployeeRole(admin, 1, 99, "MANAGER"));
    }

    @Test
    void rejectsUnknownStore() {
        when(storeRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());

        assertMessage(EmployeeServiceImpl.STORE_NOT_FOUND,
                () -> employeeService.changeEmployeeRole(admin, 99, 10, "MANAGER"));
    }

    @Test
    void rejectsEmployeeOfAnotherStore() {
        assertMessage(EmployeeServiceImpl.EMPLOYEE_NOT_IN_STORE,
                () -> employeeService.changeEmployeeRole(admin, 2, 10, "MANAGER"));

        assertNull(storeB.getManager());
        assertSame(staffRole, staffUser.getRole());
    }

    @Test
    void rejectsRolesOtherThanManagerAndStaff() {
        for (String role : new String[]{"ADMIN", "OWNER", "", null}) {
            assertMessage(EmployeeServiceImpl.INVALID_ROLE,
                    () -> employeeService.changeEmployeeRole(admin, 1, 10, role));
        }
        assertSame(staffRole, staffUser.getRole());
    }

    @Test
    void rejectsSecondManagerWhenStoreAlreadyHasOne() {
        storeA.setManager(employee(11, storeA, EmployeeStatus.ACTIVE));

        assertMessage(EmployeeServiceImpl.STORE_ALREADY_HAS_MANAGER,
                () -> employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER"));

        assertEquals(11, storeA.getManager().getId());
        assertSame(staffRole, staffUser.getRole());
    }

    @Test
    void rejectsSecondManagerWhenAnotherMemberHasManagerRoleButIsNotLinked() {
        when(userRepository.countOtherStoreMembersWithRole(1, 10, RoleName.MANAGER)).thenReturn(1L);

        assertMessage(EmployeeServiceImpl.STORE_ALREADY_HAS_MANAGER,
                () -> employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER"));

        assertNull(storeA.getManager());
    }

    @Test
    void managerLinkedToAnotherStoreIsReleasedSoOneManagerManagesOneStore() {
        storeB.setManager(staffEmployee);
        when(storeRepository.findAllByManager_Id(10)).thenReturn(List.of(storeB));

        employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER");

        assertNull(storeB.getManager());
        assertSame(staffEmployee, storeA.getManager());
        verify(storeRepository).flush();
    }

    @Test
    void linksExistingManagerRoleToOwnStoreWhenStoreHasNoManager() {
        staffUser.setRole(managerRole);

        employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER");

        assertSame(staffEmployee, storeA.getManager());
    }

    @Test
    void rejectsUnchangedRole() {
        assertMessage(EmployeeServiceImpl.ROLE_UNCHANGED.formatted("STAFF"),
                () -> employeeService.changeEmployeeRole(admin, 1, 10, "STAFF"));

        staffUser.setRole(managerRole);
        storeA.setManager(staffEmployee);
        assertMessage(EmployeeServiceImpl.ROLE_UNCHANGED.formatted("MANAGER"),
                () -> employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER"));
    }

    @Test
    void rejectsEmployeeWithoutAccount() {
        when(userRepository.findByEmployee_Id(10)).thenReturn(Optional.empty());

        assertMessage(EmployeeServiceImpl.ACCOUNT_REQUIRED,
                () -> employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER"));
        assertNull(storeA.getManager());
    }

    @Test
    void rejectsChangingAdminAccount() {
        staffUser.setRole(Role.builder().id(1).roleName(RoleName.ADMIN).build());

        assertMessage(EmployeeServiceImpl.ADMIN_ROLE_LOCKED,
                () -> employeeService.changeEmployeeRole(admin, 1, 10, "STAFF"));
    }

    @Test
    void rejectsTerminatedEmployeeOrLockedAccountAsManager() {
        staffEmployee.setStatus(EmployeeStatus.TERMINATED);
        assertMessage(EmployeeServiceImpl.TERMINATED_CANNOT_BE_MANAGER,
                () -> employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER"));

        staffEmployee.setStatus(EmployeeStatus.ACTIVE);
        staffUser.setIsActive(false);
        assertMessage(EmployeeServiceImpl.INACTIVE_ACCOUNT_CANNOT_BE_MANAGER,
                () -> employeeService.changeEmployeeRole(admin, 1, 10, "MANAGER"));
        assertNull(storeA.getManager());
    }

    @Test
    void nonAdminCannotChangeRoleOrViewEmployees() {
        for (RoleName role : new RoleName[]{RoleName.MANAGER, RoleName.STAFF}) {
            assertMessage(EmployeeServiceImpl.ACCESS_DENIED,
                    () -> employeeService.changeEmployeeRole(actor(role), 1, 10, "MANAGER"));
            assertMessage(EmployeeServiceImpl.ACCESS_DENIED,
                    () -> employeeService.getStoreEmployees(actor(role), 1));
        }
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void listsOnlyEmployeesOfSelectedStoreWithRoleAndCertificate() {
        Employee manager = employee(11, storeA, EmployeeStatus.ACTIVE);
        manager.setHasCertificate(true);
        manager.setUser(User.builder().id(101).role(managerRole).employee(manager).build());
        staffEmployee.setUser(staffUser);
        Employee withoutAccount = employee(12, storeA, EmployeeStatus.ON_LEAVE);
        storeA.setManager(manager);
        when(storeRepository.findById(1)).thenReturn(Optional.of(storeA));
        when(employeeRepository.findByStoreIdWithAccount(1)).thenReturn(List.of(staffEmployee, manager, withoutAccount));

        List<EmployeeResponse> result = employeeService.getStoreEmployees(admin, 1);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(e -> e.getStoreId().equals(1)));
        assertEquals(RoleName.STAFF, result.get(0).getRoleName());
        assertFalse(result.get(0).isHasCertificate());
        assertEquals(RoleName.MANAGER, result.get(1).getRoleName());
        assertTrue(result.get(1).isStoreManager());
        assertTrue(result.get(1).isHasCertificate());
        assertNull(result.get(2).getRoleName());
    }

    @Test
    void listingUnknownStoreFails() {
        when(storeRepository.findById(99)).thenReturn(Optional.empty());

        assertMessage(EmployeeServiceImpl.STORE_NOT_FOUND, () -> employeeService.getStoreEmployees(admin, 99));
    }

    private void assertMessage(String expected, org.junit.jupiter.api.function.Executable call) {
        BusinessException ex = assertThrows(BusinessException.class, call);
        assertEquals(expected, ex.getMessage());
    }

    private Employee employee(int id, Store store, EmployeeStatus status) {
        return Employee.builder().id(id).fullName("Employee " + id).email("e" + id + "@x.vn")
                .store(store).status(status).build();
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder().id(1).username("user").passwordHash("x").role(role).build());
    }
}
