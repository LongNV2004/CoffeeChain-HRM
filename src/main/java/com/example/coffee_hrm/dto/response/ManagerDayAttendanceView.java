package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ManagerDayAttendanceView {

    private final LocalDate workDate;
    private final String dateLabel;
    private final String storeName;
    private final int assignedEmployees;
    private final int checkedInCount;
    private final int checkedOutCount;
    private final int lateCount;
    private final int missingCount;
    private final int violationSlots;
    @Builder.Default
    private final List<ShiftCount> shifts = List.of();
    @Builder.Default
    private final List<Slot> slots = List.of();

    @Getter
    @Builder
    public static class ShiftCount {
        private final Integer shiftId;
        private final String shiftName;
        private final int slots;
    }

    @Getter
    @Builder
    public static class Slot {
        private final Integer employeeId;
        private final String employeeName;
        private final String employeeCode;
        private final String searchText;
        private final Integer shiftId;
        private final String shiftName;
        private final String startLabel;
        private final String endLabel;
        private final String scheduleLabel;
        private final String paidHoursLabel;
        private final String note;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final String lateLabel;
        private final String earlyLabel;
        private final boolean late;
        private final boolean early;
        private final String statusKey;
        private final String statusLabel;
        private final String workedLabel;
        private final Integer attendanceId;
        private final String photoBase;
        private final boolean hasCheckInPhoto;
        private final boolean hasCheckOutPhoto;
    }
}
