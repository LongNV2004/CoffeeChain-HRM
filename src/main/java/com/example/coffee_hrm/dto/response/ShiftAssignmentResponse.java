package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftAssignmentResponse {

    private Integer id;
    private Integer shiftId;
    private String shiftName;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private Integer employeeId;
    private String employeeName;
    private LocalDate workDate;
    private AssignmentStatus status;
    private LocalDateTime createdAt;
}
