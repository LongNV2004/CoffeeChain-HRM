package com.example.coffee_hrm.common.enums;

/**
 * Trạng thái thanh toán của một nhân viên hoặc cả báo cáo. Chỉ "Đã thanh toán" khi số đã trả bằng số phải trả;
 * trả một phần vẫn là "Chưa thanh toán" (số còn lại hiển thị riêng).
 */
public enum PayrollPaymentStatus {
    CHUA_THANH_TOAN,
    DA_THANH_TOAN;

    public String label() {
        return switch (this) {
            case CHUA_THANH_TOAN -> "Chưa thanh toán";
            case DA_THANH_TOAN -> "Đã thanh toán";
        };
    }

    public static PayrollPaymentStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PayrollPaymentStatus.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
