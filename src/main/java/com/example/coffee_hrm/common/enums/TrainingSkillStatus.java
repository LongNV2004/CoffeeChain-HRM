package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrainingSkillStatus {

    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String dbValue;

    public static TrainingSkillStatus fromDbValue(String dbValue) {
        for (TrainingSkillStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TrainingSkillStatus: " + dbValue);
    }
}
