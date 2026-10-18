package com.example.coffee_hrm.common.exception;

import org.springframework.security.authentication.DisabledException;

public class EmploymentEndedException extends DisabledException {

    public static final String MESSAGE =
            "Tài khoản của bạn đã bị vô hiệu hóa do chấm dứt làm việc tại hệ thống.";

    public EmploymentEndedException() {
        super(MESSAGE);
    }
}
