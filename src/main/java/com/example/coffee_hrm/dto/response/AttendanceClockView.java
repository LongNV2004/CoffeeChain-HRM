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
    private final String scheduledStartLabel;
    private final String scheduledEndLabel;
    private final String statusLabel;
    private final boolean manager;
    private final List<RecentItem> recent;

    @Getter
    @Builder
    public static class RecentItem {
        private final LocalDate workDate;
        private final String timeLabel;
    }
}
