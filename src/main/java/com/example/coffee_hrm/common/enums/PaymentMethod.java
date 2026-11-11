package com.example.coffee_hrm.common.enums;

/** Hình thức Admin đã trả lương ngoài hệ thống. CoffeeHRM chỉ ghi nhận, không kết nối ngân hàng. */
public enum PaymentMethod {
    CHUYEN_KHOAN,
    TIEN_MAT;

    public String label() {
        return switch (this) {
            case CHUYEN_KHOAN -> "Chuyển khoản";
            case TIEN_MAT -> "Tiền mặt";
        };
    }

    public static PaymentMethod parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
