package com.example.coffee_hrm.common.enums;

import com.example.coffee_hrm.common.exception.BusinessException;

public enum AttendanceHistoryKind {

    ALL,
    SHIFT,
    TRAINING;

    public static AttendanceHistoryKind parse(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw)) {
            return ALL;
        }
        if ("shift".equalsIgnoreCase(raw)) {
            return SHIFT;
        }
        if ("training".equalsIgnoreCase(raw)) {
            return TRAINING;
        }
        throw new BusinessException("Loại chấm công không hợp lệ.");
    }
}
