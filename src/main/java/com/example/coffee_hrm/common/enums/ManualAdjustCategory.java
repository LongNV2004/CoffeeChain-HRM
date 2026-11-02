package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Danh mục điều chỉnh điểm chuyên cần thủ công của Quản lý (thay cho nhập số điểm tự do).
 */
@Getter
@RequiredArgsConstructor
public enum ManualAdjustCategory {

    CUSTOMER_PRAISE("Được khách hàng khen", 3),
    EVENT_SUPPORT("Hỗ trợ sự kiện / khai trương", 5),
    INITIATIVE("Sáng kiến cải tiến", 5),
    UNIFORM_HYGIENE("Vi phạm đồng phục / vệ sinh", -3),
    SERVICE_ATTITUDE("Thái độ phục vụ chưa tốt", -5);

    private final String label;
    private final int points;

    public boolean isBonus() {
        return points > 0;
    }

    public static ManualAdjustCategory fromParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ManualAdjustCategory c : values()) {
            if (c.name().equalsIgnoreCase(value.trim())) {
                return c;
            }
        }
        return null;
    }
}

