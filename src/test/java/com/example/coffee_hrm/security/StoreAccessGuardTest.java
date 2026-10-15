package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.service.impl.StoreStatusTransition;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StoreAccessGuardTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final StoreStatusTransition transition = mock(StoreStatusTransition.class);
    private final StoreAccessGuard guard = new StoreAccessGuard(storeRepository, employeeRepository, transition);

    @Test
    void adminIsNeverBlocked() {
        assertFalse(guard.deniesBusinessAccess(actor(RoleName.ADMIN, null, null)));
        verifyNoInteractions(storeRepository, employeeRepository, transition);
    }

    @Test
    void managerUsesTheManagedStoreFromTheDatabase() {
        Store assignedOnProfile = Store.builder().id(1).status(StoreStatus.ACTIVE).isActive(true).build();
        Employee employee = Employee.builder().id(9).fullName("Quản lý").store(assignedOnProfile).build();
        Store managed = Store.builder().id(2).status(StoreStatus.INACTIVE).isActive(false).build();
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(managed));
        when(transition.promoteAndRead(eq(2), any())).thenReturn(StoreStatus.INACTIVE);

        assertTrue(guard.deniesBusinessAccess(AuthenticatedUser.from(user(RoleName.MANAGER, employee))));
        verify(employeeRepository, never()).findByIdWithStore(any());
    }

    @Test
    void preOpeningStaffCanContinueAfterTheOpeningCheck() {
        Store store = Store.builder().id(8).status(StoreStatus.PRE_OPENING).isActive(true).build();
        Employee employee = Employee.builder().id(4).fullName("Nhân viên").store(store).build();
        when(employeeRepository.findByIdWithStore(4)).thenReturn(Optional.of(employee));
        when(transition.promoteAndRead(eq(8), any())).thenReturn(StoreStatus.PRE_OPENING);

        assertFalse(guard.deniesBusinessAccess(AuthenticatedUser.from(user(RoleName.STAFF, employee))));
        verify(storeRepository, never()).findByManager_Id(any());
    }

    @Test
    void inactiveStaffIsBlockedFromTheStoreOnTheirProfile() {
        Store store = Store.builder().id(8).status(StoreStatus.INACTIVE).isActive(false).build();
        Employee employee = Employee.builder().id(4).fullName("Nhân viên").store(store).build();
        when(employeeRepository.findByIdWithStore(4)).thenReturn(Optional.of(employee));
        when(transition.promoteAndRead(eq(8), any())).thenReturn(StoreStatus.INACTIVE);

        assertTrue(guard.deniesBusinessAccess(AuthenticatedUser.from(user(RoleName.STAFF, employee))));
    }

    private AuthenticatedUser actor(RoleName roleName, Integer employeeId, Integer storeId) {
        Employee employee = employeeId == null ? null : Employee.builder()
                .id(employeeId)
                .fullName("Người dùng")
                .store(Store.builder().id(storeId).build())
                .build();
        return AuthenticatedUser.from(user(roleName, employee));
    }

    private User user(RoleName roleName, Employee employee) {
        return User.builder()
                .id(1)
                .username("user")
                .passwordHash("x")
                .role(Role.builder().id(1).roleName(roleName).build())
                .employee(employee)
                .isActive(true)
                .build();
    }
}
