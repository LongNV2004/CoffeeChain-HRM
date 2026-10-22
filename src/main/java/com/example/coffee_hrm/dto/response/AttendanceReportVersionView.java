package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AttendanceReportVersionView {

    private final int reportId;
    private final String storeName;
    private final String periodLabel;
    private final int versionNumber;
    private final LocalDateTime submittedAt;
    private final String senderName;
    private final boolean current;
    private final boolean fileMissing;
    private final int employeeCount;
    private final int shiftRecordCount;
    private final int trainingRecordCount;
    private final int lateCount;
    private final int absentCount;
    private final List<EmployeeCard> employees;

    @Getter
    @Builder
    public static class EmployeeCard {
        private final int employeeId;
        private final String code;
        private final String fullName;
        private final String roleKey;
        private final String roleText;
        private final String attendedDays;
        private final String hours;
        private final String lateRecords;
        private final String lateMinutes;
        private final String earlyRecords;
        private final String earlyMinutes;
        private final String absentRecords;
        private final List<DayLine> shifts;
        private final List<DayLine> trainings;
    }

    @Getter
    @Builder
    public static class DayLine {
        private final String title;
        private final String kindLabel;
        private final String schedule;
        private final String checkIn;
        private final String checkOut;
        private final String lateText;
        private final String earlyText;
        private final String hours;
        private final String status;
        private final String badgeClass;
        private final String note;
        private final boolean shift;
        private final boolean training;
        private final boolean late;
        private final boolean early;
        private final boolean absent;
    }
}
