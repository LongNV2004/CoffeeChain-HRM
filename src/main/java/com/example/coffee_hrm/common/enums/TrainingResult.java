package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrainingResult {

    PASS("Pass"),
    FAIL("Fail");

    private final String dbValue;

    public static TrainingResult fromDbValue(String dbValue) {
        for (TrainingResult result : values()) {
            if (result.dbValue.equals(dbValue)) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unknown TrainingResult: " + dbValue);
    }
}
