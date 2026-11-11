package com.example.coffee_hrm.common.time;

import com.example.coffee_hrm.common.exception.BusinessException;

import java.time.LocalDate;

/** Quy tắc chung cho ngày sinh trong hồ sơ nhân viên. Ngày sinh không bắt buộc. */
public final class BirthDates {

    public static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    public static final String FUTURE_MESSAGE = "Ngày sinh phải là ngày trong quá khứ.";
    public static final String TOO_EARLY_MESSAGE = "Ngày sinh không được trước 01/01/1900.";

    private BirthDates() {
    }

    /** Trả về chính ngày sinh nếu hợp lệ (hoặc {@code null}); ngày tương lai, hôm nay hoặc trước 1900 bị từ chối. */
    public static LocalDate validateOptional(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        if (!dateOfBirth.isBefore(VietnamTime.today())) {
            throw new BusinessException(FUTURE_MESSAGE);
        }
        if (dateOfBirth.isBefore(EARLIEST)) {
            throw new BusinessException(TOO_EARLY_MESSAGE);
        }
        return dateOfBirth;
    }
}
