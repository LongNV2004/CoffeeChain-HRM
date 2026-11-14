package com.example.coffee_hrm.security;

/**
 * Manager chưa có chứng chỉ quản lý không bị chặn toàn bộ tài khoản: vẫn dùng các quyền cá nhân
 * (hồ sơ, hợp đồng, chấm công, đào tạo của mình) và được tham gia lớp đào tạo. Chỉ nghiệp vụ
 * quản lý cửa hàng cần chứng chỉ.
 *
 * <p>Điều kiện hợp đồng (ví dụ phải có hợp đồng mới được tham gia đào tạo) do
 * {@link ManagerContractAccess} kiểm tra trước ở {@link EmploymentAccessFilter}.</p>
 */
public final class ManagerOperationGuard {

    public static final String REQUIRED_MESSAGE =
            "Bạn cần hoàn thành đào tạo và đạt Chứng chỉ quản lý để sử dụng chức năng này.";

    private ManagerOperationGuard() {
    }

    public static boolean allowsWithoutCertificate(String path) {
        return allowsWithoutCertificate(path, "GET");
    }

    public static boolean allowsWithoutCertificate(String path, String method) {
        return allowsWithoutCertificate(path, method, false);
    }

    /**
     * @param selfHistory request xem lịch sử chấm công của chính mình ({@code ?self=true})
     */
    public static boolean allowsWithoutCertificate(String path, String method, boolean selfHistory) {
        if (path == null || path.isBlank()) {
            return true;
        }
        return "/dashboard/manager".equals(path)
                || "/profile".equals(path)
                || path.startsWith("/profile/")
                || "/notifications".equals(path)
                || path.startsWith("/notifications/")
                || ManagerContractAccess.allowsPersonal(path, method, selfHistory)
                || ManagerContractAccess.allowsTrainingParticipation(path, method)
                || "/logout".equals(path)
                || "/login".equals(path)
                || "/error".equals(path)
                || "/".equals(path)
                || "/coffee".equals(path)
                || "/favicon.ico".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/");
    }
}
