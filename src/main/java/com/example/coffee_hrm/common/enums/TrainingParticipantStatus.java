package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrainingParticipantStatus {

    REGISTERED("Registered"),
    ATTENDED("Attended"),
    ABSENT("Absent");

    private final String dbValue;

    public static TrainingParticipantStatus fromDbValue(String dbValue) {
        for (TrainingParticipantStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TrainingParticipantStatus: " + dbValue);
    }
}
