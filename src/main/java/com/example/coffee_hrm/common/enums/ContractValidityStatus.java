package com.example.coffee_hrm.common.enums;

public enum ContractValidityStatus {

    CHUA_HIEU_LUC("Chưa hiệu lực"),
    DANG_HIEU_LUC("Đang hiệu lực"),
    SAP_HET_HAN("Sắp hết hạn"),
    DA_HET_HAN("Đã hết hạn"),
    DA_CHAM_DUT("Đã chấm dứt");

    private final String label;

    ContractValidityStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
