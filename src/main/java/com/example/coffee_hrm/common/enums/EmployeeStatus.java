package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmployeeStatus {

    ACTIVE("Active"),
    ON_LEAVE("OnLeave"),
    TERMINATED("Terminated");

    private final String dbValue;

    public static EmployeeStatus fromDbValue(String dbValue) {
        for (EmployeeStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown EmployeeStatus: " + dbValue);
    }
}
