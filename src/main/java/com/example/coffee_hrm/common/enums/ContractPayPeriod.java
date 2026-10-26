package com.example.coffee_hrm.common.enums;

public enum ContractPayPeriod {

    MONTHLY("HangThang", "Hàng tháng"),
    TWICE_MONTHLY("HaiLanThang", "Hai lần mỗi tháng");

    private final String dbValue;
    private final String label;

    ContractPayPeriod(String dbValue, String label) {
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
