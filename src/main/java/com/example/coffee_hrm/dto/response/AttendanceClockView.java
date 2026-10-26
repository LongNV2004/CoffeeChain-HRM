package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AttendanceClockView {

    private final String employeeName;
    private final String storeName;
    private final String shiftName;
    private final String workDateLabel;
    private final String scheduledStartLabel;
    private final String scheduledEndLabel;
    private final String statusLabel;
    private final boolean manager;
    private final boolean canCheckIn;
    private final String checkInNotice;
    private final boolean canCheckOut;
    private final String checkOutNotice;
    private final List<RecentItem> recent;
    @Builder.Default
    private final List<Slot> slots = List.of();

    @Getter
    @Builder
    public static class Slot {
        private final Integer assignmentId;
        private final String shiftName;
        private final String scheduleLabel;
        private final String statusKey;
        private final String statusLabel;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final boolean canCheckIn;
        private final String checkInNotice;
        private final boolean canCheckOut;
        private final String checkOutNotice;
    }

    @Getter
    @Builder
    public static class RecentItem {
        private final LocalDate workDate;
        private final String timeLabel;
    }
}
