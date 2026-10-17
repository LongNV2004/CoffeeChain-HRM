package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDetail;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDirectory;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmployeeSkillTrainingFact;
import com.example.coffee_hrm.repository.RoleRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;

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
            assertMessage(EmployeeServiceImpl.ACCESS_DENIED,
                    () -> employeeService.getStoreEmployee(actor(role), 1, 10));
        }
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void listsManagerBeforeStaffWithoutChangingStoredRoles() {
        Employee manager = employee(11, storeA, EmployeeStatus.ON_LEAVE);
        manager.setFullName("Zara Manager");
        manager.setHasCertificate(true);
        manager.setUser(User.builder().id(101).role(managerRole).employee(manager).passwordHash("SECRET-HASH").build());
        staffEmployee.setFullName("Binh Staff");
        staffEmployee.setUser(staffUser);
        Employee withoutAccount = employee(12, storeA, EmployeeStatus.TERMINATED);
        withoutAccount.setFullName("An Chua Co Tai Khoan");
        storeA.setManager(manager);
        when(storeRepository.findByIdWithManager(1)).thenReturn(Optional.of(storeA));
        when(employeeRepository.findByStoreIdWithAccount(1)).thenReturn(List.of(withoutAccount, staffEmployee, manager));

        List<EmployeeResponse> result = employeeService.getStoreEmployees(admin, 1);

        assertEquals(List.of(11, 10, 12), result.stream().map(EmployeeResponse::getId).toList());
        assertTrue(result.get(0).isStoreManager());
        assertEquals(RoleName.MANAGER, result.get(0).getRoleName());
        assertEquals(EmployeeStatus.ON_LEAVE, result.get(0).getStatus());
        assertEquals(RoleName.STAFF, result.get(1).getRoleName());
        assertEquals(EmployeeStatus.ACTIVE, result.get(1).getStatus());
        assertNull(result.get(2).getRoleName());
        assertEquals(EmployeeStatus.TERMINATED, result.get(2).getStatus());
        assertTrue(result.get(0).isHasCertificate());
        assertFalse(result.get(1).isHasCertificate());
        assertEquals(RoleName.MANAGER, manager.getUser().getRole().getRoleName());
        assertEquals(RoleName.STAFF, staffUser.getRole().getRoleName());
        assertTrue(result.stream().noneMatch(item -> "SECRET-HASH".equals(item.getEmail())
                || "SECRET-HASH".equals(item.getPhone())
                || "SECRET-HASH".equals(item.getAddress())
                || "SECRET-HASH".equals(item.getAvatarUrl())
                || "SECRET-HASH".equals(item.getFullName())));
    }

    @Test
    void listsStaffByNameWhenStoreHasNoManager() {
        staffEmployee.setFullName("Binh");
        staffEmployee.setUser(staffUser);
        Employee earlier = employee(12, storeA, EmployeeStatus.ACTIVE);
        earlier.setFullName("An");
        earlier.setUser(User.builder().id(12).role(staffRole).employee(earlier).isActive(true).build());
        when(storeRepository.findByIdWithManager(1)).thenReturn(Optional.of(storeA));
        when(employeeRepository.findByStoreIdWithAccount(1)).thenReturn(List.of(staffEmployee, earlier));

        List<EmployeeResponse> result = employeeService.getStoreEmployees(admin, 1);

        assertEquals(List.of(12, 10), result.stream().map(EmployeeResponse::getId).toList());
        assertTrue(result.stream().noneMatch(EmployeeResponse::isStoreManager));
        assertEquals(RoleName.STAFF, earlier.getUser().getRole().getRoleName());
    }

    @Test
    void readsEmployeeOnlyInsideSelectedStoreAndKeepsActualStatus() {
        staffEmployee.setStatus(EmployeeStatus.ON_LEAVE);
        staffEmployee.setPhone(null);
        staffUser.setPasswordHash("SECRET-HASH");
        staffEmployee.setUser(staffUser);
        when(storeRepository.findByIdWithManager(1)).thenReturn(Optional.of(storeA));
        when(employeeRepository.findByIdWithStoreAccount(10)).thenReturn(Optional.of(staffEmployee));

        EmployeeResponse result = employeeService.getStoreEmployee(admin, 1, 10);

        assertEquals(EmployeeStatus.ON_LEAVE, result.getStatus());
        assertEquals(10, result.getId());
        assertNull(result.getPhone());
        assertFalse("SECRET-HASH".equals(result.getEmail()));
        assertFalse("SECRET-HASH".equals(result.getFullName()));
    }

    @Test
    void rejectsEmployeeDetailOutsideSelectedStore() {
        when(storeRepository.findByIdWithManager(2)).thenReturn(Optional.of(storeB));
        when(employeeRepository.findByIdWithStoreAccount(10)).thenReturn(Optional.of(staffEmployee));

        assertMessage(EmployeeServiceImpl.EMPLOYEE_NOT_IN_STORE,
                () -> employeeService.getStoreEmployee(admin, 2, 10));
    }

    @Test
    void rejectsMissingEmployeeOrStoreOnDetail() {
        when(storeRepository.findByIdWithManager(1)).thenReturn(Optional.of(storeA));
        when(employeeRepository.findByIdWithStoreAccount(99)).thenReturn(Optional.empty());
        assertMessage(EmployeeServiceImpl.EMPLOYEE_NOT_FOUND,
                () -> employeeService.getStoreEmployee(admin, 1, 99));

        when(storeRepository.findByIdWithManager(99)).thenReturn(Optional.empty());
        assertMessage(EmployeeServiceImpl.STORE_NOT_FOUND,
                () -> employeeService.getStoreEmployee(admin, 99, 10));
        verify(employeeRepository, never()).findByIdWithStoreAccount(10);
    }

    @Test
    void listingUnknownStoreFails() {
        when(storeRepository.findByIdWithManager(99)).thenReturn(Optional.empty());

        assertMessage(EmployeeServiceImpl.STORE_NOT_FOUND, () -> employeeService.getStoreEmployees(admin, 99));
    }

    @Test
    void managerDirectoryUsesAssignedStoreAndCountsEachPersonOnce() {
        Employee managerEmployee = employee(20, storeB, EmployeeStatus.ACTIVE);
        managerEmployee.setFullName("Quản lý");
        User managerUser = User.builder().id(20).username("mgr").passwordHash("SECRET-HASH")
                .role(managerRole).employee(managerEmployee).isActive(true).build();
        managerEmployee.setUser(managerUser);
        storeA.setManager(managerEmployee);

        staffEmployee.setFullName("An");
        staffEmployee.setUser(staffUser);
        staffUser.setIsActive(false);
        staffUser.setPasswordHash("SECRET-HASH");

        Employee former = employee(11, storeA, EmployeeStatus.TERMINATED);
        former.setFullName("Bình");

        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(storeA));
        when(storeRepository.findByIdWithManager(1)).thenReturn(Optional.of(storeA));
        when(employeeRepository.findByStoreIdWithAccount(1))
                .thenReturn(List.of(former, staffEmployee, managerEmployee));

        ManagedEmployeeDirectory directory = employeeService.getManagedDirectory(
                AuthenticatedUser.from(managerUser));

        assertEquals(List.of(20, 10, 11), directory.employees().stream().map(EmployeeResponse::getId).toList());
        assertEquals(1, directory.storeId());
        assertEquals("Store A", directory.storeName());
        assertEquals(3, directory.totalCount());
        assertEquals(2, directory.activeCount());
        assertEquals(0, directory.onLeaveCount());
        assertEquals(1, directory.terminatedCount());
        assertEquals(1, directory.managerCount());
        assertEquals(1, directory.staffCount());
        assertEquals(1, directory.noAccountCount());
        assertEquals(0, directory.otherCount());
        assertEquals(Boolean.FALSE, directory.employees().get(1).getAccountActive());
        assertFalse(directory.employees().stream().anyMatch(row -> "SECRET-HASH".equals(row.getEmail())));
        verify(employeeRepository).findByStoreIdWithAccount(1);
        verify(employeeRepository, never()).findByStoreIdWithAccount(2);
    }

    @Test
    void managerDetailStaysInsideManagedStoreAndOmitsCredentials() {
        Employee managerEmployee = employee(20, storeA, EmployeeStatus.ACTIVE);
        User managerUser = User.builder().id(20).username("mgr").passwordHash("SECRET-HASH")
                .role(managerRole).employee(managerEmployee).isActive(true).build();
        managerEmployee.setUser(managerUser);
        storeA.setManager(managerEmployee);
        staffEmployee.setUser(staffUser);
        staffUser.setPasswordHash("SECRET-HASH");

        Shift shift = Shift.builder().id(3).shiftName("Ca sáng")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0)).build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(storeA));
        when(storeRepository.findByIdWithManager(1)).thenReturn(Optional.of(storeA));
        when(employeeRepository.findByIdWithStoreAccount(10)).thenReturn(Optional.of(staffEmployee));
        when(shiftAssignmentRepository.findUpcomingByEmployee(
                eq(10), any(), eq(AssignmentStatus.ASSIGNED), any()))
                .thenReturn(List.of(ShiftAssignment.builder()
                        .workDate(LocalDate.of(2026, 10, 20)).shift(shift).build()));
        when(attendanceRepository.findRecentByEmployee(eq(10), any()))
                .thenReturn(List.of(Attendance.builder()
                        .workDate(LocalDate.of(2026, 10, 17))
                        .status(AttendanceStatus.LATE)
                        .shift(shift)
                        .checkInTime(LocalDateTime.of(2026, 10, 17, 8, 15))
                        .build()));
        when(trainingClassEnrollmentRepository.findParticipatedByEmployee(10, TrainingClassStatus.REJECTED))
                .thenReturn(List.of(TrainingClassEnrollment.builder()
                        .trainingClass(TrainingClass.builder().className("Pha chế cơ bản")
                                .startDate(LocalDate.of(2026, 10, 1))
                                .endDate(LocalDate.of(2026, 10, 2))
                                .status(TrainingClassStatus.APPROVED)
                                .build())
                        .result(TrainingResult.PASS)
                        .build()));
        when(trainingClassEnrollmentRepository.findSkillTrainingFacts(List.of(10), TrainingClassStatus.REJECTED))
                .thenReturn(List.of(
                        skillFact(TrainingResult.NOT_PASS),
                        skillFact(TrainingResult.PASS)));

        ManagedEmployeeDetail detail = employeeService.getManagedEmployee(
                AuthenticatedUser.from(managerUser), 10);

        assertEquals(10, detail.employee().getId());
        assertEquals(Boolean.TRUE, detail.employee().getAccountActive());
        assertEquals("Ca sáng", detail.shifts().get(0).shiftName());
        assertEquals("08:00–12:00", detail.shifts().get(0).timeRange());
        assertEquals("Đi muộn", detail.attendances().get(0).statusLabel());
        assertEquals("Pha chế cơ bản", detail.trainings().get(0).className());
        assertEquals("Đạt", detail.trainings().get(0).resultLabel());
        assertEquals(1, detail.skills().size());
        assertEquals("Đã đạt", detail.skills().get(0).statusLabel());
        assertFalse("SECRET-HASH".equals(detail.employee().getFullName()));
        assertFalse("SECRET-HASH".equals(detail.employee().getEmail()));
    }

    @Test
    void managerCannotOpenEmployeeOutsideManagedStore() {
        Employee managerEmployee = employee(20, storeB, EmployeeStatus.ACTIVE);
        User managerUser = User.builder().id(20).username("mgr").passwordHash("x")
                .role(managerRole).employee(managerEmployee).isActive(true).build();
        storeB.setManager(managerEmployee);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(storeB));
        when(storeRepository.findByIdWithManager(2)).thenReturn(Optional.of(storeB));
        when(employeeRepository.findByIdWithStoreAccount(10)).thenReturn(Optional.of(staffEmployee));

        assertMessage(EmployeeServiceImpl.EMPLOYEE_NOT_IN_STORE,
                () -> employeeService.getManagedEmployee(AuthenticatedUser.from(managerUser), 10));
        verifyNoInteractions(attendanceRepository, shiftAssignmentRepository, trainingClassEnrollmentRepository);
    }

    @Test
    void managerDetailHidesUnknownEmployee() {
        Employee managerEmployee = employee(20, storeA, EmployeeStatus.ACTIVE);
        User managerUser = User.builder().id(20).username("mgr").passwordHash("x")
                .role(managerRole).employee(managerEmployee).isActive(true).build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(storeA));
        when(storeRepository.findByIdWithManager(1)).thenReturn(Optional.of(storeA));
        when(employeeRepository.findByIdWithStoreAccount(99)).thenReturn(Optional.empty());

        assertMessage(EmployeeServiceImpl.MANAGED_EMPLOYEE_NOT_FOUND,
                () -> employeeService.getManagedEmployee(AuthenticatedUser.from(managerUser), 99));
    }

    @Test
    void staffAndUnassignedManagerCannotListStoreEmployees() {
        assertMessage(EmployeeServiceImpl.ACCESS_DENIED,
                () -> employeeService.getManagedDirectory(actor(RoleName.STAFF)));
        assertMessage(EmployeeServiceImpl.ACCESS_DENIED,
                () -> employeeService.getManagedDirectory(actor(RoleName.ADMIN)));
        assertMessage(EmployeeServiceImpl.ACCESS_DENIED,
                () -> employeeService.getManagedEmployee(actor(RoleName.STAFF), 10));

        Employee managerEmployee = employee(20, storeA, EmployeeStatus.ACTIVE);
        User managerUser = User.builder().id(20).username("mgr").passwordHash("x")
                .role(managerRole).employee(managerEmployee).isActive(true).build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.empty());
        assertMessage(EmployeeServiceImpl.STORE_NOT_ASSIGNED,
                () -> employeeService.getManagedDirectory(AuthenticatedUser.from(managerUser)));
        verify(employeeRepository, never()).findByStoreIdWithAccount(any());
    }

    private EmployeeSkillTrainingFact skillFact(TrainingResult result) {
        return new EmployeeSkillTrainingFact(
                10, 7, "Pha chế", result, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), null,
                TrainingClassStatus.APPROVED);
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
