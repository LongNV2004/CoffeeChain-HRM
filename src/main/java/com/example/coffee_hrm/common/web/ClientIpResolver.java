package com.example.coffee_hrm.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * Ứng dụng không cấu hình trusted proxy, nên chỉ dùng địa chỉ socket của request.
     * Header X-Forwarded-For do client tự gửi không được dùng để tránh giả mạo IP.
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return normalize(request.getRemoteAddr());
    }

    public static String normalize(String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.regionMatches(true, 0, "::ffff:", 0, 7)) {
            value = value.substring(7);
        }
        int zone = value.indexOf('%');
        if (zone > 0) {
            value = value.substring(0, zone);
        }
        if ("0:0:0:0:0:0:0:1".equals(value)) {
            return "::1";
        }
        return value;
    }

    public static boolean isLocalOrPrivate(String ip) {
        String value = normalize(ip);
        if (value == null) {
            return true;
        }
        if ("::1".equalsIgnoreCase(value)) {
            return true;
        }
        String lower = value.toLowerCase();
        if (lower.startsWith("fe80:") || lower.startsWith("fc") || lower.startsWith("fd")) {
            return true;
        }
        if (lower.contains(":")) {
            return false;
        }
        String[] parts = lower.split("\\.");
        if (parts.length != 4) {
            return true;
        }
        int first;
        int second;
        try {
            first = Integer.parseInt(parts[0]);
            second = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return true;
        }
        if (first == 0 || first == 10 || first == 127) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        return first == 192 && second == 168;
    }

    public static boolean matches(String requestIp, String storeIp) {
        String left = normalize(requestIp);
        String right = normalize(storeIp);
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
