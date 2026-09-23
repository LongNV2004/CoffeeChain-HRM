package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AttendanceHistoryView {

    private final Integer employeeId;
    private final String employeeName;
    private final String storeName;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final Integer filterEmployeeId;
    private final List<EmployeeOption> employees;
    private final List<AttendanceRow> rows;
    private final int recordCount;
    private final BigDecimal totalHours;
    private final String totalHoursLabel;

    @Getter
    @Builder
    public static class EmployeeOption {
        private final Integer employeeId;
        private final String fullName;
    }

    @Getter
    @Builder
    public static class AttendanceRow {
        private final Integer attendanceId;
        private final Integer employeeId;
        private final String employeeName;
        private final LocalDate workDate;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final String totalHoursLabel;
        private final String statusLabel;
        private final String statusCss;
        private final String shiftLabel;
    }
}
