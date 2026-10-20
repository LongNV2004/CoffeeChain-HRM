package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApprovalStatus {

    PENDING("Pending", "Chờ duyệt"),
    APPROVED("Approved", "Đã duyệt"),
    REJECTED("Rejected", "Đã từ chối"),
    CANCELLED("Cancelled", "Đã hủy");

    private final String dbValue;
    private final String label;

    public static ApprovalStatus fromDbValue(String dbValue) {
        for (ApprovalStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ApprovalStatus: " + dbValue);
    }
}
