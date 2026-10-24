package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ManagerDashboardView {

    private final String username;
    private final String displayName;
    private final Integer storeId;
    private final String storeName;
    private final String storeStatusLabel;
    private final String storeStatusKey;
    private final LocalDate today;
    private final String todayLabel;
    private final long workingEmployeeCount;
    private final long todaySlotCount;
    private final long todayEmployeeCount;
    private final long completedSlotCount;
    private final Integer completionRate;
    private final long violationSlotCount;
    private final long pendingRequestCount;
    private final long pendingLeaveCount;
    private final long pendingRegistrationCount;
    private final long pendingShiftChangeCount;
    @Builder.Default
    private final List<ShiftSummary> todayShifts = List.of();

    public boolean isStoreAssigned() {
        return storeId != null;
    }

    public boolean isTasksEmpty() {
        return pendingRequestCount == 0;
    }

    public boolean isAttendanceEmpty() {
        return todaySlotCount <= 0;
    }

    public boolean isScheduleEmpty() {
        return todayShifts == null || todayShifts.isEmpty();
    }

    public long getRemainingSlotCount() {
        return Math.max(0, todaySlotCount - completedSlotCount);
    }

    public String getCompletedRatioLabel() {
        return completedSlotCount + "/" + todaySlotCount;
    }

    @Getter
    @Builder
    public static class ShiftSummary {
        private final Integer shiftId;
        private final String shiftName;
        private final String timeLabel;
        private final long slotCount;
        private final long employeeCount;
    }
}
