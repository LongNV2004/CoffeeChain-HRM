package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrainingClassStatus {

    PENDING_APPROVAL("PENDING_APPROVAL", "Chờ duyệt"),
    APPROVED("APPROVED", "Đã duyệt"),
    REJECTED("REJECTED", "Từ chối");

    private final String dbValue;
    private final String label;

    public static TrainingClassStatus fromDbValue(String dbValue) {
        for (TrainingClassStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TrainingClassStatus: " + dbValue);
    }
}
