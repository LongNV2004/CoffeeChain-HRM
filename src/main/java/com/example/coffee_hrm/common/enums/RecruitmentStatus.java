package com.example.coffee_hrm.common.enums;

public enum RecruitmentStatus {

    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");


    private final String dbValue;


    RecruitmentStatus(String dbValue) {
        this.dbValue = dbValue;
    }


    public String getDbValue() {
        return dbValue;
    }
}