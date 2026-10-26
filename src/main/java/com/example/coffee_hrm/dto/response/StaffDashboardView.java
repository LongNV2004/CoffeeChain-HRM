package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class StaffDashboardView {

    private final String greeting;
    private final String storeName;
    private final LocalDate today;
    private final String todayLabel;
    private final long todaySlotCount;
    private final long upcomingSlotCount;
    private final String upcomingWindowLabel;
    private final String scheduleWindowLabel;
    private final long pendingRegistrationCount;
    private final long submittedRegistrationCount;
    private final long approvedRegistrationCount;
    private final String registrationPeriodLabel;
    private final long pendingPersonalCount;
    private final long pendingLeaveCount;
    private final long pendingShiftChangeCount;
    private final long incomingShiftChangeCount;
    private final boolean canRegister;
    private final String registerBlockedMessage;
    private final boolean certificationKnown;
    private final boolean certified;
    private final String certificationLabel;
    private final String attendanceMessage;
    @Builder.Default
    private final List<ScheduleItem> upcomingShifts = List.of();
    @Builder.Default
    private final List<AttendanceSlot> todayAttendance = List.of();
    @Builder.Default
    private final List<SkillItem> skills = List.of();
    @Builder.Default
    private final List<TrainingItem> upcomingClasses = List.of();

    public boolean isStoreAssigned() {
        return storeName != null && !storeName.isBlank();
    }

    public boolean isPersonalEmpty() {
        return pendingLeaveCount == 0 && pendingShiftChangeCount == 0 && incomingShiftChangeCount == 0;
    }

    @Getter
    @Builder
    public static class ScheduleItem {
        private final LocalDate workDate;
        private final String dateLabel;
        private final String shiftName;
        private final String timeLabel;
        private final String storeName;
        private final String statusLabel;
    }

    @Getter
    @Builder
    public static class AttendanceSlot {
        private final String shiftName;
        private final String scheduleLabel;
        private final String statusKey;
        private final String statusLabel;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final String lateNote;
        private final String earlyNote;
        private final String workedLabel;
    }

    @Getter
    @Builder
    public static class SkillItem {
        private final String skillName;
        private final String certificationLabel;
        private final String latestSummary;
    }

    @Getter
    @Builder
    public static class TrainingItem {
        private final String className;
        private final String dateLabel;
        private final String timeLabel;
        private final String placeLabel;
        private final String statusLabel;
    }
}
