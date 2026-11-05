package com.example.coffee_hrm.common.enums;

/**
 * Loại hợp đồng lao động. Không xác định thời hạn thì không có ngày kết thúc.
 */
public enum ContractTermType {

    KHONG_XAC_DINH_THOI_HAN("Không xác định thời hạn"),
    XAC_DINH_THOI_HAN("Xác định thời hạn");

    private final String label;

    ContractTermType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ContractTermType fromEndDate(java.time.LocalDate endDate) {
        return endDate == null ? KHONG_XAC_DINH_THOI_HAN : XAC_DINH_THOI_HAN;
    }
}
