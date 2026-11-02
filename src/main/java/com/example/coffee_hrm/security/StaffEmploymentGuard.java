package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.User;

public final class StaffEmploymentGuard {

    public static final String DENIED_MESSAGE =
            "Tài khoản đã nghỉ việc chỉ được xem thông tin và lịch sử. Không thể thực hiện thao tác này.";

    public enum Block {
        NONE,
        INACTIVE,
        EMPLOYMENT_ENDED
    }

    private StaffEmploymentGuard() {
    }

    /**
     * Chỉ nhân viên có trạng thái đã nghỉ việc bị giới hạn.
     * Hợp đồng hết hạn nhưng hồ sơ vẫn đang làm việc hoặc nghỉ phép không bị khóa.
     */

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

    public static boolean allowsWhenEmploymentEnded(String path, String method) {
        if (path == null || path.isBlank()) {
            return true;
        }
        boolean read = method == null || "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        if (isPublic(path) || "/logout".equals(path) || "/login".equals(path)) {
            return true;
        }
        if (read && isReadable(path)) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && isAllowedWrite(path);
    }

    private static boolean isReadable(String path) {
        return "/dashboard/staff".equals(path)
                || "/profile".equals(path)
                || "/profile/contract".equals(path)
                || "/profile/contracts".equals(path)
                || "/profile/history".equals(path)
                || "/schedule/staff".equals(path)
                || "/schedule/staff/shift-change".equals(path)
                || "/schedule/staff/leaderboard".equals(path)
                || "/attendance/staff/history".equals(path)
                || "/training/staff".equals(path)
                || path.matches("/training/staff/classes/\\d+")
                || "/staff/leave".equals(path)
                || "/staff/leave/history".equals(path)
                || "/staff/leave/slots".equals(path)
                || "/staff/leave/preview".equals(path)
                || path.matches("/staff/leave/\\d+")
                || "/staff/salary".equals(path)
                || "/staff/salary/detail".equals(path)
                || "/staff/salary/export".equals(path)
                || "/staff/contract".equals(path)
                || path.matches("/staff/contract/\\d+")
                || path.matches("/staff/contract/\\d+/file")
                || "/notifications".equals(path)
                || "/notifications/feed".equals(path)
                || path.startsWith("/attendance/photos/")
                || path.startsWith("/attendance/training-photos/");
    }

    private static boolean isAllowedWrite(String path) {
        return "/profile/password".equals(path)
                || "/logout".equals(path)
                || path.matches("/staff/leave/\\d+/cancel")
                || path.matches("/notifications/\\d+/read")
                || "/notifications/read-all".equals(path);
    }

    private static boolean isPublic(String path) {
        return "/".equals(path)
                || "/coffee".equals(path)
                || "/error".equals(path)
                || "/favicon.ico".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/");
    }
}
