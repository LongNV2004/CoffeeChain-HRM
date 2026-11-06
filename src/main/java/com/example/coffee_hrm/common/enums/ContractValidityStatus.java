package com.example.coffee_hrm.common.enums;

/**
 * Bảy trạng thái hợp đồng dùng chung cho Manager và Staff.
 * Trạng thái được tính từ ngày hiệu lực, không lưu thành cột.
 */
public enum ContractValidityStatus {

    CHO_HIEU_LUC("Chờ hiệu lực"),
    DANG_HIEU_LUC("Đang hiệu lực"),
    CHO_GIA_HAN("Chờ gia hạn"),
    DA_GIA_HAN("Đã gia hạn"),
    KHONG_GIA_HAN("Không gia hạn"),
    DA_HET_HAN("Đã hết hạn"),
    DA_CHAM_DUT("Đã chấm dứt");

    private final String label;

    ContractValidityStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Hợp đồng đang trong kỳ hiệu lực, chưa sang giai đoạn gia hạn hoặc đã xử lý gia hạn. */
    public boolean isInForce() {
        return this == DANG_HIEU_LUC || this == CHO_GIA_HAN;
    }

    /** Chỉ hợp đồng chờ gia hạn mới được gia hạn hoặc xác nhận không gia hạn. */
    public boolean isRenewable() {
        return this == CHO_GIA_HAN;
    }

    /**
     * Còn được đăng ký ca, chấm công, đào tạo và nghỉ phép.
     * Đã gia hạn và không gia hạn vẫn làm đến hết ngày kết thúc của chính hợp đồng đó.
     */
    public boolean allowsWork() {
        return this == DANG_HIEU_LUC
                || this == CHO_GIA_HAN
                || this == DA_GIA_HAN
                || this == KHONG_GIA_HAN;
    }

    public boolean isClosed() {
        return this == DA_HET_HAN || this == DA_CHAM_DUT;
    }
}
