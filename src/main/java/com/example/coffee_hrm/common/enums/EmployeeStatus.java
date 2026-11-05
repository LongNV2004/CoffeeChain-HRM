package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmployeeStatus {

    PENDING_CONTRACT("PendingContract"),
    ACTIVE("Active"),
    ON_LEAVE("OnLeave"),
    TERMINATED("Terminated");

    private final String dbValue;

    public String getLabel() {
        return switch (this) {
            case PENDING_CONTRACT -> "Chờ hoàn thiện hợp đồng";
            case ACTIVE -> "Đang làm việc";
            case ON_LEAVE -> "Đang nghỉ phép";
            case TERMINATED -> "Đã nghỉ việc";
        };
    }

    public static EmployeeStatus fromDbValue(String dbValue) {
        for (EmployeeStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown EmployeeStatus: " + dbValue);
    }
}
