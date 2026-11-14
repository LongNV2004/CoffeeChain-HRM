package com.example.coffee_hrm.service.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Giờ làm việc cố định áp dụng chung cho mọi Manager: 08:30 – 17:30, tất cả các ngày trong tuần.
 * Manager không đăng ký ca, không được phân ca; chấm công, báo cáo và bảng lương đối chiếu với khung giờ này
 * thay cho lịch ca của Staff. Bản ghi chấm công của Manager không gắn ca ({@code shift = null}) và lưu
 * khung giờ vào các cột giờ dự kiến để báo cáo, lịch sử và bảng lương đọc như ca của Staff.
 */
public final class ManagerWorkSchedule {

    public static final LocalTime START = LocalTime.of(8, 30);
    public static final LocalTime END = LocalTime.of(17, 30);
    public static final String SHIFT_NAME = "Giờ hành chính";

    private ManagerWorkSchedule() {
    }

    /** Manager làm tất cả các ngày trong tuần. */
    public static boolean isWorkDay(LocalDate day) {
        return day != null;
    }

    public static LocalDateTime startOf(LocalDate day) {
        return day.atTime(START);
    }

    public static LocalDateTime endOf(LocalDate day) {
        return day.atTime(END);
    }

    /** Ngày làm việc đã hết giờ (sau 17:30): chưa chấm công thì tính vắng mặt. */
    public static boolean ended(LocalDate day, LocalDateTime now) {
        return isWorkDay(day) && now != null && !now.isBefore(endOf(day));
    }

    public static String label() {
        return SHIFT_NAME + " (08:30 – 17:30)";
    }
}
