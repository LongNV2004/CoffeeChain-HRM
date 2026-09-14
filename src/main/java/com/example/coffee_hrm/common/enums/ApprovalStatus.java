package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApprovalStatus {

    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String dbValue;

    public static ApprovalStatus fromDbValue(String dbValue) {
        for (ApprovalStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ApprovalStatus: " + dbValue);
    }
}
