package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PayrollDetailView {

    private final int year;
    private final int month;
    private final String periodLabel;
    private final String homePath;
    private final String navRole;
    private final String employeeCode;
    private final String employeeName;
    private final String storeName;
    private final String statusKey;
    private final String statusLabel;
    private final boolean locked;
    private final int assignedShifts;
    private final int validShifts;
    private final int leaveShifts;
    private final int paidLeaveShifts;
    private final int paidLeaveHours;
    private final int reviewShifts;
    private final int payableHours;
    private final String hourlyRateLabel;
    private final String expectedSalaryLabel;
    /** Tổng lương = lương ca thường + lương tăng ca. */
    private final int overtimeShifts;
    private final int overtimeHours;
    private final String regularSalaryLabel;
    private final String overtimeSalaryLabel;
    @Builder.Default
    private final List<ShiftRow> shifts = List.of();

    @Getter
    @Builder
    public static class ShiftRow {
        private final String dateLabel;
        private final String shiftName;
        private final String scheduleLabel;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final String attendanceStatus;
        private final int payableHours;
        private final String hourlyRateLabel;
        private final String shiftSalaryLabel;
        private final boolean payable;
        private final String unpaidReason;
        private final boolean overtime;
    }
}
