package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class StaffDayAttendanceView {

    private final LocalDate workDate;
    private final String dateLabel;
    @Builder.Default
    private final List<Slot> slots = List.of();

    @Getter
    @Builder
    public static class Slot {
        private final Integer shiftId;
        private final String shiftName;
        private final String scheduleLabel;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final String lateLabel;
        private final String earlyLabel;
        private final boolean late;
        private final String workedLabel;
        private final String statusKey;
        private final String statusLabel;
        private final String slotKey;
        private final Integer attendanceId;
        private final String photoBase;
        private final boolean hasCheckInPhoto;
        private final boolean hasCheckOutPhoto;
    }
}
