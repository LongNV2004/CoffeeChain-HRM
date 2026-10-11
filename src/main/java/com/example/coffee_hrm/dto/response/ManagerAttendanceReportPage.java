package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.report.AttendanceReportWorkbook;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ManagerAttendanceReportPage {

    private final int storeId;
    private final String storeName;
    private final String authorName;
    private final int year;
    private final int month;
    private final String periodLabel;
    private final String statusCode;
    private final String statusLabel;
    private final LocalDateTime lastGeneratedAt;
    private final LocalDateTime lastSubmittedAt;
    private final Integer versionNumber;
    private final boolean submitted;
    private final AttendanceReportPreview preview;
    private final List<AttendanceReportWorkbook.SummaryRow> employees;
    private final Integer selectedEmployeeId;
    private final String selectedEmployeeName;
    private final List<AttendanceReportWorkbook.ShiftRow> days;

    @Getter
    @Builder
    public static class AttendanceReportPreview {
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
}
