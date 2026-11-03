package com.example.coffee_hrm.common.enums;

/**
 * Đúng bốn trạng thái hợp đồng lao động. Không thêm trạng thái khác.
 */
public enum ContractValidityStatus {

    DANG_HIEU_LUC("Đang hiệu lực"),
    CHO_GIA_HAN("Chờ gia hạn"),
    DA_CHAM_DUT_TRUOC_THOI_HAN("Đã chấm dứt trước thời hạn"),
    DA_CHAM_DUT("Đã chấm dứt");

    private final String label;

    ContractValidityStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Hợp đồng còn cho phép làm việc: đang hiệu lực hoặc đang chờ gia hạn. */
    public boolean isInForce() {
        return this == DANG_HIEU_LUC || this == CHO_GIA_HAN;
    }

    public boolean isClosed() {
        return this == DA_CHAM_DUT || this == DA_CHAM_DUT_TRUOC_THOI_HAN;
    }
}
