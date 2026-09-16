package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class StaffDashboardView {

    private final String username;
    private final String displayName;
    private final String phone;
    private final String email;
    private final String storeName;
    private final String employeeStatus;
    private final LocalDate hireDate;
    private final String todayAttendanceStatus;
    private final LocalDateTime todayCheckIn;
    private final LocalDateTime todayCheckOut;
    private final List<ScheduleItem> upcomingShifts;
    private final List<LeaveItem> leaveRequests;
    private final List<ShiftChangeItem> shiftChangeRequests;

    @Getter
    @Builder
    public static class ScheduleItem {
        private final LocalDate workDate;
        private final String shiftName;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String status;
    }

    @Getter
    @Builder
    public static class LeaveItem {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final String reason;
        private final String status;
    }

    @Getter
    @Builder
    public static class ShiftChangeItem {
        private final LocalDateTime requestDate;
        private final String reason;
        private final String status;
    }
}
