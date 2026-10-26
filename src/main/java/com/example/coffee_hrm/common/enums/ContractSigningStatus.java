package com.example.coffee_hrm.common.enums;

public enum ContractSigningStatus {

    CHUA_CO_BAN_KY("ChuaCoBanKy", "Chưa có bản ký"),
    DA_TAI_BAN_KY("DaTaiBanKy", "Đã tải bản ký");

    private final String dbValue;
    private final String label;

    ContractSigningStatus(String dbValue, String label) {
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
