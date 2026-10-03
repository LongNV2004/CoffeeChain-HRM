package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrainingResult {

    PASS("Pass"),
    NOT_PASS("NotPass");

    private final String dbValue;

    public static TrainingResult fromDbValue(String dbValue) {
        if ("Fail".equals(dbValue)) {
            return NOT_PASS;
        }
        for (TrainingResult result : values()) {
            if (result.dbValue.equals(dbValue)) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unknown TrainingResult: " + dbValue);
    }

    public String getLabel() {
        return this == PASS ? "Đạt" : "Không đạt";
    }
}
