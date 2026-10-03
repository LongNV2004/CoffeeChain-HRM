package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttendanceStatus {

    PRESENT("Present"),
    ON_TIME("OnTime"),
    LATE("Late"),
    EARLY_LEAVE("EarlyLeave"),
    LATE_AND_EARLY("LateAndEarly"),
    ABSENT("Absent");

    private final String dbValue;

    public static AttendanceStatus fromDbValue(String dbValue) {
        for (AttendanceStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AttendanceStatus: " + dbValue);
    }
}
