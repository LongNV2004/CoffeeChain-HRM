package com.example.coffee_hrm.common.enums;

public enum ResignationProposalStatus {

    PENDING("Pending", "Chờ duyệt"),
    PARTIALLY_PROCESSED("PartiallyProcessed", "Đang xử lý"),
    COMPLETED("Completed", "Đã xử lý");

    private final String dbValue;
    private final String label;

    ResignationProposalStatus(String dbValue, String label) {
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
