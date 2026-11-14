package com.example.coffee_hrm.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record PayrollEmployeeDraft(
        Integer employeeId,
        String employeeName,
        List<Shift> shifts) {

    public record Shift(
            Integer assignmentId,
            Integer attendanceId,
            LocalDate workDate,
            String shiftName,
            LocalTime scheduledStart,
            LocalTime scheduledEnd,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            String attendanceStatus,
            int scheduledHours,
            boolean lateDeducted,
            boolean earlyDeducted,
            int payableHours,
            BigDecimal hourlyRate,
            BigDecimal shiftSalary,
            boolean payable,
            boolean needsReview,
            String unpaidReason) {

        /** Ca nghỉ phép đã duyệt: không có chấm công, trạng thái là tên loại nghỉ. */
        public boolean leave() {
            return attendanceId == null && PeriodLeave.isLeaveLabel(attendanceStatus);
        }

        /** Ca đi làm sinh từ đơn tăng ca đã duyệt (xem {@link OvertimeShifts}). */
        public boolean overtime(Set<Integer> overtimeAssignmentIds) {
            return !leave() && assignmentId != null
                    && overtimeAssignmentIds != null && overtimeAssignmentIds.contains(assignmentId);
        }
    }

    /** Số ca tăng ca trong kỳ (đã nằm trong {@link #assignedShifts()}). */
    public int overtimeShifts(Set<Integer> overtimeAssignmentIds) {
        return (int) shifts.stream().filter(shift -> shift.overtime(overtimeAssignmentIds)).count();
    }

    public int overtimeHours(Set<Integer> overtimeAssignmentIds) {
        return shifts.stream().filter(shift -> shift.overtime(overtimeAssignmentIds)).mapToInt(Shift::payableHours).sum();
    }

    /**
     * Lương các ca tăng ca: mỗi ca đã được tính theo đúng quy tắc ca thường (đơn giá hợp đồng, trừ đi muộn/về sớm...).
     * Đây là một phần của {@link #expectedSalary()}, không cộng thêm.
     */
    public BigDecimal overtimeSalary(Set<Integer> overtimeAssignmentIds) {
        return shifts.stream()
                .filter(shift -> shift.overtime(overtimeAssignmentIds))
                .map(Shift::shiftSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Lương các ca thông thường (gồm nghỉ phép hưởng lương) = tổng lương − lương tăng ca. */
    public BigDecimal regularSalary(Set<Integer> overtimeAssignmentIds) {
        return expectedSalary().subtract(overtimeSalary(overtimeAssignmentIds));
    }

    /** Ca được phân công đi làm (không gồm ca nghỉ phép). */
    public int assignedShifts() {
        return (int) shifts.stream().filter(shift -> !shift.leave()).count();
    }

    /** Ca đi làm hợp lệ được tính lương (không gồm ca nghỉ phép). */
    public int validShifts() {
        return (int) shifts.stream().filter(shift -> !shift.leave() && shift.payable()).count();
    }

    public int leaveShifts() {
        return (int) shifts.stream().filter(Shift::leave).count();
    }

    public int paidLeaveShifts() {
        return (int) shifts.stream().filter(shift -> shift.leave() && shift.payable()).count();
    }

    public int paidLeaveHours() {
        return shifts.stream().filter(Shift::leave).mapToInt(Shift::payableHours).sum();
    }

    public int reviewShifts() {
        return (int) shifts.stream().filter(Shift::needsReview).count();
    }

    public int payableHours() {
        return shifts.stream().mapToInt(Shift::payableHours).sum();
    }

    public BigDecimal expectedSalary() {
        return shifts.stream()
                .map(Shift::shiftSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal hourlyRate() {
        Set<BigDecimal> rates = rates();
        if (rates.size() == 1) {
            return rates.iterator().next();
        }
        return null;
    }

    public boolean multipleRates() {
        return rates().size() > 1;
    }

    private Set<BigDecimal> rates() {
        Set<BigDecimal> rates = new LinkedHashSet<>();
        for (Shift shift : shifts) {
            if (shift.hourlyRate() != null) {
                rates.add(shift.hourlyRate().setScale(2, RoundingMode.HALF_UP));
            }
        }
        return rates;
    }
}
