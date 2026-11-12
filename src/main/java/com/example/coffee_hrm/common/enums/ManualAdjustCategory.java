package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Danh mục điều chỉnh điểm chuyên cần thủ công của Quản lý (thay cho nhập số điểm tự do).
 */
@Getter
@RequiredArgsConstructor
public enum ManualAdjustCategory {

    // Các hạng mục theo quy tắc ca làm & chuyên cần (các mục tự động +5đ, +2đ, -10đ đã do hệ thống tự xử lý)
    EMERGENCY_DECLINE_PENALTY("Từ chối lời mời hỗ trợ ca gấp (từ lần 3)", -3, ReliabilityActionType.EMERGENCY_DECLINE_PENALTY),
    ABANDONED_ACCEPTED_SHIFT("Nhận ca gấp rồi bỏ ca / vắng mặt", -15, ReliabilityActionType.ABANDONED_ACCEPTED_SHIFT),
    LATE_ARRIVAL_MINOR("Đi muộn / Về sớm (≤30 phút)", -2, ReliabilityActionType.LATE_ARRIVAL),
    LATE_ARRIVAL_MODERATE("Đi muộn / Về sớm (31–60 phút)", -4, ReliabilityActionType.LATE_ARRIVAL),
    LATE_ARRIVAL_SEVERE("Đi muộn / Về sớm (trên 60 phút)", -6, ReliabilityActionType.LATE_ARRIVAL),

    // Khen thưởng / nhắc nhở thủ công của Quản lý (áp dụng hạn mức ±15đ/tháng)
    CUSTOMER_PRAISE("Được khách hàng khen", 3, ReliabilityActionType.MANAGER_BONUS),
    EVENT_SUPPORT("Hỗ trợ sự kiện / khai trương", 5, ReliabilityActionType.MANAGER_BONUS),
    INITIATIVE("Sáng kiến cải tiến", 5, ReliabilityActionType.MANAGER_BONUS),
    UNIFORM_HYGIENE("Vi phạm đồng phục / vệ sinh", -3, ReliabilityActionType.MANAGER_PENALTY),
    SERVICE_ATTITUDE("Thái độ phục vụ chưa tốt", -5, ReliabilityActionType.MANAGER_PENALTY);

    private final String label;
    private final int points;
    private final ReliabilityActionType actionType;

    public boolean isBonus() {
        return points > 0;
    }

    public boolean isManualQuotaCategory() {
        return actionType == ReliabilityActionType.MANAGER_BONUS
                || actionType == ReliabilityActionType.MANAGER_PENALTY;
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

