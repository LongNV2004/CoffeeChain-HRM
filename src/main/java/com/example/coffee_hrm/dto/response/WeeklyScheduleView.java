package com.example.coffee_hrm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyScheduleView {

    private Integer storeId;
    private String storeName;
    private String storeAddress;

    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private LocalDate prevWeekDate;
    private LocalDate nextWeekDate;

    private List<DayHeader> days;
    private List<ShiftRow> shiftRows;
    private List<EmployeeOption> storeEmployees;
    private List<ColleagueShiftOption> colleagueShifts;
    private List<ShiftChangeItem> pendingShiftChanges;
    private List<ShiftChangeItem> incomingRequests;

    private long totalAssignments;
    private long publishedAssignments;
    private long draftAssignments;
    private boolean fullyPublished;

    public String getFormattedWeekRange() {
        if (weekStartDate == null || weekEndDate == null) return "";
        return String.format("Tuần: %02d/%02d/%d – %02d/%02d/%d",
                weekStartDate.getDayOfMonth(), weekStartDate.getMonthValue(), weekStartDate.getYear(),
                weekEndDate.getDayOfMonth(), weekEndDate.getMonthValue(), weekEndDate.getYear());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayHeader {
        private LocalDate date;
        private String dayOfWeekName;
        private String formattedDate;
        private boolean isToday;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftRow {
        private Integer shiftId;
        private String shiftName;
        private LocalTime startTime;
        private LocalTime endTime;
        private String formattedTime;
        private List<DayCell> dayCells;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayCell {
        private LocalDate date;
        private Integer shiftId;
        private List<AssignmentItem> assignments;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignmentItem {
        private Integer assignmentId;
        private Integer employeeId;
        private String employeeName;
        private String employeePhone;
        private String status;
        private Boolean isPublished;
        private boolean isCurrentStaff;
        private boolean canCancel;
        private boolean canRequestChange;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmployeeOption {
        private Integer employeeId;
        private String fullName;
        private String email;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColleagueShiftOption {
        private Integer assignmentId;
        private Integer employeeId;
        private String employeeName;
        private Integer shiftId;
        private String shiftName;
        private String timeRange;
        private LocalDate workDate;
        private String formattedDate;
        private String displayText;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftChangeItem {
        private Integer requestId;
        private Integer assignmentId;
        private Integer employeeId;
        private String employeeName;
        private String shiftName;
        private LocalDate workDate;
        private String timeRange;
        private String reason;
        private String status;
        private String requestDate;
        private String resolvedByName;
        private String resolvedDate;

        // Proposal details
        private Integer targetEmployeeId;
        private String targetEmployeeName;
        private Integer targetAssignmentId;
        private String targetShiftName;
        private LocalDate targetWorkDate;
        private String targetTimeRange;

        private String changeType; // SWAP, TRANSFER, MANAGER_ASSIGN
        private String changeTypeLabel;
        private String proposalSummary;

        private Boolean isTargetAgreed;
        private String targetAgreedAt;
        private String stageLabel;

        public String getRequesterName() {
            return employeeName;
        }
    }
}

