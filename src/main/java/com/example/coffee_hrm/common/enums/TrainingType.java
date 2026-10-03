package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrainingType {

    STORE_TRAINING("STORE_TRAINING", "Đào tạo tại cửa hàng"),
    CENTRALIZED_TRAINING("CENTRALIZED_TRAINING", "Đào tạo tập trung");

    private final String dbValue;
    private final String label;

    public static TrainingType fromDbValue(String dbValue) {
        for (TrainingType type : values()) {
            if (type.dbValue.equals(dbValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TrainingType: " + dbValue);
    }
}
