package com.example.coffee_hrm.dto.response;

import java.util.List;

public record LeaveFormContext(
        Integer employeeId,
        String employeeName,
        String storeName,
        String managerName,
        int annualTotalDays,
        int annualUsedDays,
        int annualPendingDays,
        int annualAvailableDays,
        boolean halfDaySupported,
        boolean quotaConfigured,
        String scheduleNote,
        List<ColleagueOption> colleagues) {

    public record ColleagueOption(Integer id, String fullName) {
    }
}
