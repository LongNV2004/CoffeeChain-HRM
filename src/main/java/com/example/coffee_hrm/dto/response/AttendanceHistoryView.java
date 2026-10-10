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
    private final Integer filterStoreId;
    private final Integer filterShiftId;
    private final String filterStatus;
    private final boolean lateOnly;
    private final boolean earlyOnly;
    private final String filterKind;
    private final List<EmployeeOption> employees;
    private final List<StoreOption> stores;
    private final List<ShiftOption> shifts;
    private final List<StatusOption> statuses;
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
    public static class StoreOption {
        private final Integer storeId;
        private final String storeName;
    }

    @Getter
    @Builder
    public static class ShiftOption {
        private final Integer shiftId;
        private final String label;
    }

    @Getter
    @Builder
    public static class StatusOption {
        private final String value;
        private final String label;
    }

    @Getter
    @Builder
    public static class AttendanceRow {
        private final Integer attendanceId;
        private final Integer employeeId;
        private final String employeeName;
        private final LocalDate workDate;
        private final String storeName;
        private final String scheduledStartLabel;
        private final String scheduledEndLabel;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final String totalHoursLabel;
        private final String lateLabel;
        private final String earlyLabel;
        private final String missingLabel;
        private final String checkInLocation;
        private final String checkOutLocation;
        private final String statusLabel;
        private final String statusCss;
        private final String shiftLabel;
        private final String kindLabel;
    }
}
