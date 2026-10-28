package com.example.coffee_hrm.common.enums;

public enum PayrollStatus {
    DA_TINH,
    DA_CHOT;

    public String label() {
        return switch (this) {
            case DA_TINH -> "Đã tính";
            case DA_CHOT -> "Đã chốt";
        };
    }
}
