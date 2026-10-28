package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PayrollBoardView {

    private final int year;
    private final int month;
    private final String monthValue;
    private final String periodLabel;
    private final String homePath;
    private final String navRole;
    private final boolean canFilterStore;
    private final boolean canCalculate;
    private final boolean canLock;
    private final boolean canExport;
    private final boolean locked;
    private final String statusKey;
    private final String statusLabel;
    private final Integer selectedStoreId;
    private final Integer selectedEmployeeId;
    private final String selectedStatus;
    private final int employeeCount;
    private final int payableHours;
    private final String payableHoursLabel;
    private final String payrollFundLabel;
    private final int reviewShifts;
    private final String exportPath;
    @Builder.Default
    private final List<StoreOption> stores = List.of();
    @Builder.Default
    private final List<EmployeeOption> employees = List.of();
    @Builder.Default
    private final List<Row> rows = List.of();

    @Getter
    @Builder
    public static class StoreOption {
        private final Integer id;
        private final String name;
    }

    @Getter
    @Builder
    public static class EmployeeOption {
        private final Integer id;
        private final String label;
    }

    @Getter
    @Builder
    public static class Row {
        private final Integer employeeId;
        private final Integer storeId;
        private final String employeeCode;
        private final String employeeName;
        private final String storeName;
        private final int assignedShifts;
        private final int payableHours;
        private final String hourlyRateLabel;
        private final String expectedSalaryLabel;
        private final String statusKey;
        private final String statusLabel;
    }
}
