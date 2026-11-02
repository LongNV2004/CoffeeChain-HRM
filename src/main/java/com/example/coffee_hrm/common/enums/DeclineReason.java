package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Lý do nhân viên từ chối lời mời hỗ trợ ca gấp (chỉ để Quản lý tham khảo, không ảnh hưởng điểm).
 */
@Getter
@RequiredArgsConstructor
public enum DeclineReason {

    PERSONAL("Bận việc cá nhân"),
    STUDY("Lịch học / thi"),
    HEALTH("Sức khỏe"),
    OTHER("Lý do khác");

    private final String label;

    public static DeclineReason fromParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DeclineReason reason : values()) {
            if (reason.name().equalsIgnoreCase(value.trim())) {
                return reason;
            }
        }
        return null;
    }
}
