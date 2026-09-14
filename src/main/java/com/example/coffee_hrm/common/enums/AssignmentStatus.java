package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AssignmentStatus {

    ASSIGNED("Assigned"),
    CANCELLED("Cancelled");

    private final String dbValue;

    public static AssignmentStatus fromDbValue(String dbValue) {
        for (AssignmentStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AssignmentStatus: " + dbValue);
    }
}
