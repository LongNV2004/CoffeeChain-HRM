package com.example.coffee_hrm.dto.response;

import java.util.List;

public record LeavePreviewResponse(int requestedWorkDays, List<YearDetail> years,
                                   boolean deductsAnnualLeave, String leaveTypeLabel,
                                   String processNote) {
    public record YearDetail(int year, int requestedDays, int totalDays,
                             int usedDays, int pendingDays, int availableDays) {
    }
}
