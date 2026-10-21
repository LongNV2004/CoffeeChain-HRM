package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Nghỉ phép năm trừ quỹ LeaveBalances khi được duyệt.
 * Nghỉ không lương, nghỉ ốm và nghỉ việc riêng không trừ quỹ phép năm,
 * vì hệ thống chỉ theo dõi một quỹ phép năm của cửa hàng.
 */
@Getter
@RequiredArgsConstructor
public enum LeaveType {

    ANNUAL("Annual", "Nghỉ phép năm", true),
    UNPAID("Unpaid", "Nghỉ không hưởng lương", false),
    SICK("Sick", "Nghỉ ốm", false),
    PERSONAL("Personal", "Nghỉ việc riêng", false);

    private final String dbValue;
    private final String label;
    private final boolean consumesAnnualLeave;

    public static LeaveType fromDbValue(String dbValue) {
        for (LeaveType type : values()) {
            if (type.dbValue.equals(dbValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LeaveType: " + dbValue);
    }
}
