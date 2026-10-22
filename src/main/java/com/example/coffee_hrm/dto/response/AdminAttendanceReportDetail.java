package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.report.AttendanceReportWorkbook;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminAttendanceReportDetail {

    private final int reportId;
    private final int storeId;
    private final String storeName;
    private final String managerName;
    private final int year;
    private final int month;
    private final String periodLabel;
    private final boolean submitted;
    private final boolean stale;
    private final String statusCode;
    private final String statusLabel;
    private final String statusCss;
    private final String reviewNote;
    private final String senderName;
    private final String authorName;
    private final LocalDateTime submittedAt;
    private final Integer versionNumber;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final ManagerAttendanceReportPage.AttendanceReportPreview snapshot;
    private final ManagerAttendanceReportPage.AttendanceReportPreview live;
    private final List<AttendanceReportWorkbook.SummaryRow> employees;
    private final Integer selectedEmployeeId;
    private final String selectedEmployeeName;
    private final String historyKind;
    private final List<AttendanceReportWorkbook.ShiftRow> days;
    private final List<AttendanceReportWorkbook.TrainingRow> trainingDays;
    private final List<VersionItem> versions;
    private final int versionPage;
    private final int versionPageCount;

    @Getter
    @Builder
    public static class VersionItem {
        private final int versionNumber;
        private final LocalDateTime submittedAt;
        private final String senderName;
    }
}
