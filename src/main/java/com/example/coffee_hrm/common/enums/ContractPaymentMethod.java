package com.example.coffee_hrm.common.enums;

public enum ContractPaymentMethod {

    CASH("TienMat", "Tiền mặt"),
    BANK_TRANSFER("ChuyenKhoan", "Chuyển khoản");

    private final String dbValue;
    private final String label;

    ContractPaymentMethod(String dbValue, String label) {
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
