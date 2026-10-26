package com.example.coffee_hrm.common.security;

import com.example.coffee_hrm.common.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * Số căn cước công dân: đúng 12 chữ số, lưu dạng chuỗi.
 * Thông báo lỗi không chứa giá trị đã nhập.
 */
public final class CitizenIds {

    public static final String REQUIRED_MESSAGE = "Vui lòng nhập số căn cước công dân.";
    public static final String INVALID_MESSAGE = "Số căn cước công dân phải gồm đúng 12 chữ số.";
    public static final String DUPLICATE_MESSAGE = "Số căn cước công dân đã thuộc về một hồ sơ nhân viên khác.";
    public static final String BELONGS_TO_FORMER =
            "Số căn cước công dân thuộc về nhân viên đã nghỉ việc. Hãy dùng đề xuất tuyển dụng lại, không tạo hồ sơ mới.";
    public static final String MISSING_FOR_CONTRACT =
            "Nhân viên chưa có số căn cước công dân. Hãy cập nhật hồ sơ nhân viên trước khi tạo hợp đồng.";

    private static final Pattern DIGITS = Pattern.compile("^[0-9]{12}$");

    private CitizenIds() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.replaceAll("[\\s.\\-]", "").trim();
        return compact.isEmpty() ? null : compact;
    }

    public static boolean isValid(String normalized) {
        return normalized != null && DIGITS.matcher(normalized).matches();
    }

    public static String require(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            throw new BusinessException(REQUIRED_MESSAGE);
        }
        if (!isValid(normalized)) {
            throw new BusinessException(INVALID_MESSAGE);
        }
        return normalized;
    }

    /**
     * Chuỗi trống nghĩa là không đổi. Giá trị có nội dung phải đúng 12 chữ số.
     */
    public static String optional(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return null;
        }
        if (!isValid(normalized)) {
            throw new BusinessException(INVALID_MESSAGE);
        }
        return normalized;
    }

    /**
     * Ví dụ {@code 079203000123} thành {@code 079******123}. Giá trị không hợp lệ không được lộ một phần.
     */
    public static String mask(String citizenId) {
        if (citizenId == null || citizenId.isBlank()) {
            return null;
        }
        if (!isValid(citizenId)) {
            return "************";
        }
        return citizenId.substring(0, 3) + "******" + citizenId.substring(9);
    }
}
