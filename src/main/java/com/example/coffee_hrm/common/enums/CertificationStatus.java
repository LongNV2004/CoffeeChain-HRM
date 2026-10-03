package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CertificationStatus {

    CERTIFIED("CERTIFIED", "Đã chứng nhận"),
    NOTCERTIFIED("NOTCERTIFIED", "Chưa chứng nhận");

    private final String dbValue;
    private final String label;

    public static CertificationStatus fromDbValue(String dbValue) {
        for (CertificationStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CertificationStatus: " + dbValue);
    }
}
