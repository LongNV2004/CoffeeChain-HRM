package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReliabilityActionType {

    RESCUE_SHIFT_24H("RESCUE_SHIFT_24H", "Hỗ trợ ca gấp (<24h)", 5),
    COMPLETE_SWAPPED_SHIFT("COMPLETE_SWAPPED_SHIFT", "Hoàn thành tốt ca đổi", 2),
    LATE_SHIFT_CANCEL("LATE_SHIFT_CANCEL", "Xin hủy/đổi ca sát giờ (<24h)", -10),
    EMERGENCY_DECLINE_WARNING("EMERGENCY_DECLINE_WARNING", "Nhắc nhở từ chối ca khẩn cấp (<24h)", 0),
    EMERGENCY_DECLINE_PENALTY("EMERGENCY_DECLINE_PENALTY", "Từ chối quá nhiều ca khẩn cấp", -5),
    LATE_ARRIVAL("LATE_ARRIVAL", "Đi muộn", -2),
    EARLY_LEAVE("EARLY_LEAVE", "Về sớm", -2),
    MANAGER_BONUS("MANAGER_BONUS", "Quản lý thưởng điểm chuyên cần", 5),
    MANAGER_PENALTY("MANAGER_PENALTY", "Quản lý trừ điểm chuyên cần", -5);

    private final String dbValue;
    private final String label;
    private final int defaultPoints;

    public static ReliabilityActionType fromDbValue(String dbValue) {
        if (dbValue == null) {
            return MANAGER_BONUS;
        }
        for (ReliabilityActionType type : values()) {
            if (type.dbValue.equalsIgnoreCase(dbValue) || type.name().equalsIgnoreCase(dbValue)) {
                return type;
            }
        }
        return MANAGER_BONUS;
    }
}
