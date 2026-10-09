package com.example.coffee_hrm.common.enums;

public enum Gender {

    MALE("Male", "Nam"),
    FEMALE("Female", "Nữ"),
    OTHER("Other", "Khác"),
    UNSPECIFIED("Unspecified", "Không cung cấp");

    private final String dbValue;
    private final String label;

    Gender(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getLabel() {
        return label;
    }
}
