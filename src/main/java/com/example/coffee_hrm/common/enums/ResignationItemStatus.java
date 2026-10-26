package com.example.coffee_hrm.common.enums;

public enum ResignationItemStatus {

    PENDING("Pending", "Chờ duyệt"),
    APPROVED("Approved", "Đã duyệt"),
    REJECTED("Rejected", "Bị từ chối");

    private final String dbValue;
    private final String label;

    ResignationItemStatus(String dbValue, String label) {
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
