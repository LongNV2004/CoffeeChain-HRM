package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleName {

    ADMIN("Admin"),
    MANAGER("Manager"),
    STAFF("Staff");

    private final String dbValue;

    public static RoleName fromDbValue(String dbValue) {
        for (RoleName role : values()) {
            if (role.dbValue.equals(dbValue)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown RoleName: " + dbValue);
    }
}
