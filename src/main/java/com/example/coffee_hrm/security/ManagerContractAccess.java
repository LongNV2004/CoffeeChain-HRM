package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.service.impl.ContractValidityRules;

import java.time.LocalDate;
import java.util.List;

/**
 * Manager chỉ được dùng nghiệp vụ cửa hàng khi hợp đồng đang bao ngày hiện tại
 * và chưa bị chấm dứt. Dùng cùng quy tắc ngày của {@link ContractValidityRules}.
 */
public final class ManagerContractAccess {

    public static final String REQUEST_LIMIT = "coffee.managerContractLimit";

    public static final String NO_CONTRACT_MESSAGE =
            "Tài khoản của bạn đang chờ hoàn thiện hợp đồng lao động. Vui lòng liên hệ Admin để được hỗ trợ.";

    public static final String NOT_IN_FORCE_MESSAGE =
            "Hợp đồng của bạn chưa có hiệu lực hoặc không còn hiệu lực nên chưa dùng được chức năng quản lý cửa hàng.";

    public static final String PROFILE_REDIRECT = "/profile?limited=contract";

    public enum Limit {
        NONE,
        NO_CONTRACT,
        NOT_IN_FORCE
    }

    private ManagerContractAccess() {
    }

    public static Limit assess(List<EmploymentContract> contracts, LocalDate today) {
        if (contracts == null || contracts.isEmpty()) {
            return Limit.NO_CONTRACT;
        }
        if (today == null) {
            return Limit.NOT_IN_FORCE;
        }
        EmploymentContract current = ContractValidityRules.applicable(contracts, today);
        if (current == null) {
            return Limit.NOT_IN_FORCE;
        }
        ContractValidityStatus status = ContractValidityRules.resolve(
                current, today, hasDirectRenewal(contracts, current.getId()));
        if (status.isClosed() || status == ContractValidityStatus.CHO_HIEU_LUC) {
            return Limit.NOT_IN_FORCE;
        }
        return Limit.NONE;
    }

    public static String message(Limit limit) {
        if (limit == Limit.NO_CONTRACT) {
            return NO_CONTRACT_MESSAGE;
        }
        if (limit == Limit.NOT_IN_FORCE) {
            return NOT_IN_FORCE_MESSAGE;
        }
        return null;
    }

    /**
     * Khi bị giới hạn, Manager vẫn đăng nhập, xem và sửa hồ sơ của chính mình.
     * Dashboard và nghiệp vụ cửa hàng không nằm trong danh sách này.
     */
    public static boolean allowsWhenLimited(String path, String method) {
        if (path == null || path.isBlank()) {
            return true;
        }
        if (isPublic(path) || "/logout".equals(path) || "/login".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method)) {
            return "/profile".equals(path)
                    || "/profile/password".equals(path)
                    || "/notifications/read-all".equals(path)
                    || path.matches("/notifications/\\d+/read");
        }
        boolean read = method == null || "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        return read && ("/profile".equals(path)
                || "/profile/contract".equals(path)
                || "/profile/contracts".equals(path)
                || "/profile/history".equals(path)
                || "/notifications".equals(path)
                || "/notifications/feed".equals(path));
    }

    private static boolean hasDirectRenewal(List<EmploymentContract> contracts, Integer contractId) {
        if (contractId == null) {
            return false;
        }
        for (EmploymentContract contract : contracts) {
            EmploymentContract previous = contract.getPreviousContract();
            if (previous != null && contractId.equals(previous.getId())) {
                return true;
            }
        }
        return false;
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
