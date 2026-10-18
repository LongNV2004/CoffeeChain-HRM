package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.User;

public final class StaffEmploymentGuard {

    public enum Block {
        NONE,
        INACTIVE,
        EMPLOYMENT_ENDED
    }

    private StaffEmploymentGuard() {
    }

    public static Block assess(User user) {
        if (user == null || user.getRole() == null) {
            return Block.INACTIVE;
        }
        RoleName role = user.getRole().getRoleName();
        Employee employee = user.getEmployee();
        if (role == RoleName.STAFF
                && employee != null
                && employee.getStatus() == EmployeeStatus.TERMINATED) {
            return Block.EMPLOYMENT_ENDED;
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return Block.INACTIVE;
        }
        return Block.NONE;
    }

    public static boolean isRehirableStaff(User account) {
        if (account == null || account.getRole() == null || account.getRole().getRoleName() == null) {
            return true;
        }
        RoleName role = account.getRole().getRoleName();
        return role != RoleName.ADMIN && role != RoleName.MANAGER;
    }
}
