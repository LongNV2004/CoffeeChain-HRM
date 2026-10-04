package com.example.coffee_hrm.common.enums;

public enum RecruitmentStatus {

    PENDING("Pending", "Chờ duyệt"),
    APPROVED("Approved", "Đã duyệt"),
    REJECTED("Rejected", "Từ chối");

    private final String dbValue;
    private final String label;

    RecruitmentStatus(String dbValue, String label) {
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
