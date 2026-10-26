package com.example.coffee_hrm.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record LeaveSlotOption(
        Integer assignmentId,
        LocalDate workDate,
        String weekday,
        String shiftName,
        LocalTime startTime,
        LocalTime endTime,
        String storeName,
        String scheduleStatus,
        String leaveStatus,
        boolean selectable,
        String blockedReason) {
}
