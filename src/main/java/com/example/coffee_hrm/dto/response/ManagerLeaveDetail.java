package com.example.coffee_hrm.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ManagerLeaveDetail(
        ManagerLeaveItem request,
        boolean canApprove,
        String approvalBlockedReason,
        int workDaysToCharge,
        List<YearCharge> yearlyCharges,
        List<AffectedShift> affectedShifts) {

    public record YearCharge(int year, int chargedDays, int totalDays,
                             int usedDays, int pendingOtherDays, int remainingAfterApproval) {
    }

    public record AffectedShift(Integer assignmentId, LocalDate workDate,
                                String shiftName, LocalTime startTime, LocalTime endTime) {
    }
}
