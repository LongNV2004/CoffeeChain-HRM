package com.example.coffee_hrm.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Giờ tính lương lấy từ lịch ca. Đi muộn và về sớm mỗi đầu ca chỉ trừ tối đa một giờ khi quá 20 phút.
 */
public final class PayrollCalculator {

    public static final int GRACE_MINUTES = 20;

    private PayrollCalculator() {
    }

    public record Outcome(
            int scheduledHours,
            int payableHours,
            BigDecimal hourlyRate,
            BigDecimal shiftSalary,
            boolean payable,
            boolean needsReview,
            String unpaidReason,
            boolean lateDeducted,
            boolean earlyDeducted) {
    }

    public static int scheduledHours(LocalDate workDate, LocalTime start, LocalTime end) {
        if (workDate == null || start == null || end == null) {
            return 0;
        }
        LocalDateTime startAt = workDate.atTime(start);
        LocalDateTime endAt = workDate.atTime(end);
        if (!endAt.isAfter(startAt)) {
            endAt = endAt.plusDays(1);
        }
        long hours = ChronoUnit.HOURS.between(startAt, endAt);
        return hours < 0 ? 0 : (int) hours;
    }

    public static Outcome evaluate(LocalDate workDate,
                                   LocalTime start,
                                   LocalTime end,
                                   boolean duplicate,
                                   boolean hasCheckIn,
                                   boolean hasCheckOut,
                                   int lateMinutes,
                                   int earlyMinutes,
                                   BigDecimal hourlyRate) {
        if (duplicate) {
            return blocked(0, null, "Bản ghi chấm công bị trùng");
        }
        if (workDate == null || start == null || end == null) {
            return blocked(0, null, "Không xác định được ca làm");
        }
        int scheduled = scheduledHours(workDate, start, end);
        if (scheduled <= 0) {
            return blocked(0, null, "Không xác định được ca làm");
        }
        BigDecimal rate = hourlyRate != null && hourlyRate.signum() > 0 ? hourlyRate : null;
        if (!hasCheckIn && !hasCheckOut) {
            return blocked(scheduled, rate, "Không check-in, không check-out");
        }
        if (!hasCheckIn) {
            return blocked(scheduled, rate, "Thiếu check-in");
        }
        if (!hasCheckOut) {
            return blocked(scheduled, rate, "Thiếu check-out");
        }
        if (rate == null) {
            return blocked(scheduled, null, "Không xác định được mức lương");
        }
        boolean lateDeducted = lateMinutes > GRACE_MINUTES;
        boolean earlyDeducted = earlyMinutes > GRACE_MINUTES;
        int payableHours = Math.max(0, scheduled - (lateDeducted ? 1 : 0) - (earlyDeducted ? 1 : 0));
        BigDecimal salary = rate.multiply(BigDecimal.valueOf(payableHours)).setScale(0, RoundingMode.HALF_UP);
        return new Outcome(scheduled, payableHours, rate, salary, true, false, null, lateDeducted, earlyDeducted);
    }

    private static Outcome blocked(int scheduledHours, BigDecimal hourlyRate, String reason) {
        return new Outcome(scheduledHours, 0, hourlyRate, BigDecimal.ZERO, false, true, reason, false, false);
    }
}
