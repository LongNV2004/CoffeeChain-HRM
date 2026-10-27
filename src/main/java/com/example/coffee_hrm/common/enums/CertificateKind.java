package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CertificateKind {

    STAFF("STAFF", "Chứng chỉ nhân viên"),
    MANAGEMENT("MANAGEMENT", "Chứng chỉ quản lý");

    private final String dbValue;
    private final String label;

    public static CertificateKind fromDbValue(String dbValue) {
        for (CertificateKind kind : values()) {
            if (kind.dbValue.equals(dbValue)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown CertificateKind: " + dbValue);
    }
}
