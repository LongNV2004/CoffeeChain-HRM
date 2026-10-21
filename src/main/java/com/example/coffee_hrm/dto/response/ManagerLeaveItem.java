package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ManagerLeaveItem(
        Integer id,
        Integer employeeId,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        ApprovalStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedDate,
        String rejectReason,
        String reviewedByName,
        String leaveType,
        String leaveTypeLabel,
        Integer requestedWorkDays,
        String contactPhone,
        String handoverName,
        boolean deductsAnnualLeave) {
}
