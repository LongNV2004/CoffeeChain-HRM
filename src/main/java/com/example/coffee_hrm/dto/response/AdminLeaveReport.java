package com.example.coffee_hrm.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminLeaveReport(
        long pending,
        long approved,
        long rejected,
        long cancelled,
        List<Row> items,
        int page,
        int totalPages,
        long totalElements) {

    public record Row(
            Integer id,
            Integer employeeId,
            String employeeName,
            String storeName,
            String leaveTypeLabel,
            LocalDate startDate,
            LocalDate endDate,
            Integer requestedWorkDays,
            String statusLabel,
            LocalDateTime createdAt) {
    }
}
