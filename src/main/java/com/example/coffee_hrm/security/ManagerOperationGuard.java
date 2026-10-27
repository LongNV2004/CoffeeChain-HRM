package com.example.coffee_hrm.security;

/**
 * Manager chưa có chứng chỉ quản lý vẫn thấy menu, nhưng chỉ được xem lớp đào tạo của mình.
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
        if (path == null || path.isBlank()) {
            return true;
        }
        boolean readOnly = method == null || "GET".equalsIgnoreCase(method);
        return "/dashboard/manager".equals(path)
                || "/profile".equals(path)
                || path.startsWith("/profile/")
                || "/notifications".equals(path)
                || path.startsWith("/notifications/")
                || (readOnly && "/training".equals(path))
                || (readOnly && path.matches("/training/classes/\\d+"))
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
