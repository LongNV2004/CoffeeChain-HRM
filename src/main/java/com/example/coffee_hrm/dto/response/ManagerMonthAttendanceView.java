package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ManagerMonthAttendanceView {

    private final int year;
    private final int month;
    private final String monthValue;
    private final String monthLabel;
    private final String storeName;
    private final int employeeCount;
    private final int scheduledSlots;
    private final int completedSlots;
    private final int lateSlots;
    private final int earlySlots;
    /** Số ca đi muộn hoặc về sớm; một ca vi phạm cả hai chỉ tính 1. */
    private final int lateOrEarlySlots;
    /** Số ca tăng ca (đơn tăng ca đã duyệt) trong kỳ. */
    private final int overtimeSlots;
    private final int missingSlots;
    /** Tổng ca nghỉ phép đã duyệt trong kỳ. */
    private final int leaveSlots;
    private final String detailFrom;
    private final String detailTo;
    private final boolean detailReady;
    @Builder.Default
    private final List<EmployeeMonth> employees = List.of();

    @Getter
    @Builder
    public static class EmployeeMonth {
        private final Integer employeeId;
        private final String employeeName;
        private final String employeeCode;
        private final String roleLabel;
        private final String employmentKey;
        private final String searchText;
        private final int scheduledSlots;
        private final int workedSlots;
        private final int completedSlots;
        private final int lateSlots;
        private final int earlySlots;
        /** Số ca đi muộn hoặc về sớm; một ca vi phạm cả hai chỉ tính 1. */
        private final int lateOrEarlySlots;
        private final int overtimeSlots;
        private final int absentSlots;
        private final int missingSlots;
        private final String paidHoursLabel;
        private final String lateDurationLabel;
        private final String earlyDurationLabel;
        private final boolean late;
        private final boolean early;
        private final boolean overtime;
        /** Ca nghỉ phép đã duyệt trong kỳ. */
        private final int leaveSlots;
        private final int leaveDays;
        private final int paidLeaveSlots;
        private final String leaveLabel;
        @Builder.Default
        private final List<DayLine> days = List.of();
    }

    @Getter
    @Builder
    public static class DayLine {
        private final String dateLabel;
        private final String shiftName;
        private final String scheduleLabel;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final String lateLabel;
        private final String earlyLabel;
        private final String workedLabel;
        private final String statusKey;
        private final String statusLabel;
        private final Integer attendanceId;
        private final boolean hasCheckInPhoto;
        private final boolean hasCheckOutPhoto;
        private final boolean late;
        private final boolean earlyLeave;
        private final boolean absent;
        private final boolean leave;
        /** Ca sinh từ đơn tăng ca đã duyệt. */
        private final boolean overtime;
    }
}
