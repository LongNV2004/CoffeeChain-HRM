package com.example.coffee_hrm.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class AttendanceReportWorkbook {

    private final int storeId;
    private final String storeName;
    private final int year;
    private final int month;
    private final LocalDate createdDate;
    private final String authorName;
    private final List<SummaryRow> employees;
    private final List<ShiftRow> shifts;
    private final List<TrainingRow> trainings;
    private final Preview preview;

    public String periodLabel() {
        return String.format("%02d/%04d", month, year);
    }

    public LocalDate periodStart() {
        return LocalDate.of(year, month, 1);
    }

    public LocalDate periodEnd() {
        return periodStart().withDayOfMonth(periodStart().lengthOfMonth());
    }

    @Getter
    @Builder
    public static class Preview {
        private final int employeeCount;
        private final int shiftRecordCount;
        private final int trainingRecordCount;
        private final int lateCount;
        private final int shiftLateCount;
        private final int trainingLateCount;
        private final int absentCount;
        private final int openCheckoutCount;
        private final int reviewCount;
        private final int workDayCount;
        private final int violationCount;
        private final int missingCount;
    }

    @Getter
    @Builder
    public static class SummaryRow {
        private final int order;
        private final int employeeId;
        private final String fullName;
        private final String roleLabel;
        private final int workDays;
        private final int attendedDays;
        private final int lateRecords;
        private final int lateMinutes;
        private final int earlyRecords;
        private final int earlyMinutes;
        private final int absentDays;
        private final int absentRecords;
        private final int workedMinutes;
        private final int openCheckouts;
        private final String note;

        public BigDecimal workedHours() {
            return hours(workedMinutes);
        }
    }

    @Getter
    @Builder
    public static class ShiftRow {
        private final int order;
        private final LocalDate workDate;
        private final int employeeId;
        private final String fullName;
        private final String roleLabel;
        private final String shiftLabel;
        private final LocalTime scheduledStart;
        private final LocalTime scheduledEnd;
        private final LocalDateTime checkIn;
        private final LocalDateTime checkOut;
        private final int lateMinutes;
        private final int earlyMinutes;
        private final Integer workedMinutes;
        private final String statusLabel;
        private final String note;
        private final boolean needsReview;
        private final Integer attendanceId;
        private final boolean hasCheckInPhoto;
        private final boolean hasCheckOutPhoto;

        public BigDecimal workedHours() {
            return workedMinutes == null ? null : hours(workedMinutes);
        }
    }

    @Getter
    @Builder
    public static class TrainingRow {
        private final int order;
        private final LocalDate workDate;
        private final int employeeId;
        private final String fullName;
        private final String roleLabel;
        private final String classCode;
        private final String className;
        private final String trainerName;
        private final String trainingType;
        private final LocalTime scheduledStart;
        private final LocalTime scheduledEnd;
        private final LocalDateTime checkIn;
        private final LocalDateTime checkOut;
        private final int lateMinutes;
        private final int earlyMinutes;
        private final String statusLabel;
        private final String note;
        private final boolean needsReview;
        private final Integer attendanceId;
        private final boolean hasCheckInPhoto;
        private final boolean hasCheckOutPhoto;
    }

    public static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public static String formatHours(int minutes) {
        int safe = Math.max(minutes, 0);
        int hoursPart = safe / 60;
        int remain = safe % 60;
        if (hoursPart == 0 && remain == 0) {
            return "0 giờ";
        }
        if (remain == 0) {
            return hoursPart + " giờ";
        }
        if (hoursPart == 0) {
            return remain + " phút";
        }
        return hoursPart + " giờ " + remain + " phút";
    }
}
