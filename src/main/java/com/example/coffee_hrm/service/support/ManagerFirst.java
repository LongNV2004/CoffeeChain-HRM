package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;

import java.util.Objects;

/**
 * Thứ tự hiển thị trong báo cáo cửa hàng: Manager luôn đứng đầu, sau đó mới đến Staff theo thứ tự sẵn có.
 */
public final class ManagerFirst {

    private ManagerFirst() {
    }

    /** 0 cho Manager (Manager hiện tại của cửa hàng hoặc tài khoản vai trò Manager), 1 cho người còn lại. */
    public static int rank(Employee employee, Store store) {
        return isManager(employee, store) ? 0 : 1;
    }

    public static int rank(Integer employeeId, Store store) {
        Employee manager = store == null ? null : store.getManager();
        return manager != null && employeeId != null && Objects.equals(manager.getId(), employeeId) ? 0 : 1;
    }

    public static boolean isManager(Employee employee, Store store) {
        if (employee == null) {
            return false;
        }
        if (rank(employee.getId(), store) == 0) {
            return true;
        }
        return employee.getUser() != null
                && employee.getUser().getRole() != null
                && employee.getUser().getRole().getRoleName() == RoleName.MANAGER;
    }
}
