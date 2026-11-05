package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffEmploymentGuardTest {

    @Test
    void activeStaffKeepsFullAccessEvenWhenAContractCouldBeExpired() {
        assertEquals(StaffEmploymentGuard.Block.NONE,
                StaffEmploymentGuard.assess(account(RoleName.STAFF, EmployeeStatus.ACTIVE, true)));
        assertEquals(StaffEmploymentGuard.Block.NONE,
                StaffEmploymentGuard.assess(account(RoleName.STAFF, EmployeeStatus.ON_LEAVE, true)));
    }

    @Test
    void onlyTerminatedStaffIsRestrictedAndRehiringRestoresAccess() {
        assertEquals(StaffEmploymentGuard.Block.EMPLOYMENT_ENDED,
                StaffEmploymentGuard.assess(account(RoleName.STAFF, EmployeeStatus.TERMINATED, false)));
        assertEquals(StaffEmploymentGuard.Block.NONE,
                StaffEmploymentGuard.assess(account(RoleName.STAFF, EmployeeStatus.ACTIVE, true)));
        assertEquals(StaffEmploymentGuard.Block.NONE,
                StaffEmploymentGuard.assess(account(RoleName.ADMIN, EmployeeStatus.TERMINATED, true)));
        assertEquals(StaffEmploymentGuard.Block.NONE,
                StaffEmploymentGuard.assess(account(RoleName.MANAGER, EmployeeStatus.TERMINATED, true)));
    }

    @Test
    void pendingContractStaffCanSignInButCannotUseOperationalPages() {
        assertEquals(StaffEmploymentGuard.Block.PENDING_CONTRACT,
                StaffEmploymentGuard.assess(account(RoleName.STAFF, EmployeeStatus.PENDING_CONTRACT, true)));
        assertTrue(StaffEmploymentGuard.allowsWhenPendingContract("/dashboard/staff", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenPendingContract("/profile", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenPendingContract("/staff/contract/4/file", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenPendingContract("/profile/password", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenPendingContract("/availability", "GET"));
        assertFalse(StaffEmploymentGuard.allowsWhenPendingContract("/attendance/staff/check-in", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenPendingContract("/training/staff", "GET"));
        assertFalse(StaffEmploymentGuard.allowsWhenPendingContract("/staff/leave", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenPendingContract("/staff/salary", "GET"));
    }

    @Test
    void formerStaffMayReadHistoryAndChangePasswordButNotWork() {
        assertTrue(StaffEmploymentGuard.allowsWhenEmploymentEnded("/dashboard/staff", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenEmploymentEnded("/profile", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenEmploymentEnded("/profile/password", "POST"));
        assertTrue(StaffEmploymentGuard.allowsWhenEmploymentEnded("/staff/salary/detail", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenEmploymentEnded("/staff/contract/4/file", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenEmploymentEnded("/attendance/staff/history", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenEmploymentEnded("/training/staff/classes/8", "GET"));
        assertTrue(StaffEmploymentGuard.allowsWhenEmploymentEnded("/staff/leave/3/cancel", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenEmploymentEnded("/availability", "GET"));
        assertFalse(StaffEmploymentGuard.allowsWhenEmploymentEnded("/availability", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenEmploymentEnded("/attendance/staff/check-in", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenEmploymentEnded("/attendance/staff/check-out", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenEmploymentEnded("/profile", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenEmploymentEnded("/schedule/staff/request-change", "POST"));
        assertFalse(StaffEmploymentGuard.allowsWhenEmploymentEnded("/staff/leave", "POST"));
    }

    private User account(RoleName roleName, EmployeeStatus status, boolean active) {
        Employee employee = Employee.builder()
                .id(11)
                .fullName("An")
                .status(status)
                .build();
        return User.builder()
                .id(11)
                .username("an")
                .passwordHash("secret")
                .role(Role.builder().id(3).roleName(roleName).build())
                .employee(employee)
                .isActive(active)
                .build();
    }
}
