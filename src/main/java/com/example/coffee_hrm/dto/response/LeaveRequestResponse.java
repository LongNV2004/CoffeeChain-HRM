package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestResponse {

    private Integer id;
    private Integer employeeId;
    private String employeeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LeaveType leaveType;
    private String leaveTypeLabel;
    private Integer requestedWorkDays;
    private boolean deductsAnnualLeave;
    private String contactPhone;
    private Integer handoverEmployeeId;
    private String handoverEmployeeName;
    private String reason;
    private ApprovalStatus status;
    private String statusLabel;
    private boolean cancellable;
    private Integer approvedById;
    private String approvedByName;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedDate;
    private String rejectReason;
    private List<LeaveSlotItem> slots;
}
