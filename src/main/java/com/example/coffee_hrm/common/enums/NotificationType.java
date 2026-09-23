package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    TRAINING_CLASS_CREATED("TRAINING_CLASS_CREATED"),
    TRAINING_CLASS_APPROVED("TRAINING_CLASS_APPROVED"),
    TRAINING_CLASS_REJECTED("TRAINING_CLASS_REJECTED"),
    WORK_AVAILABILITY_SUBMITTED("WORK_AVAILABILITY_SUBMITTED");

    private final String dbValue;

    public static NotificationType fromDbValue(String dbValue) {
        for (NotificationType type : values()) {
            if (type.dbValue.equals(dbValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown NotificationType: " + dbValue);
    }
}
