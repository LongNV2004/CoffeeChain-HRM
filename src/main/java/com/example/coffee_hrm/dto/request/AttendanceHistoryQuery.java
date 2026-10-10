package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.AttendanceHistoryKind;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class AttendanceHistoryQuery {

    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final Integer employeeId;
    private final Integer storeId;
    private final Integer shiftId;
    private final AttendanceStatus status;
    private final boolean lateOnly;
    private final boolean earlyOnly;
    private final AttendanceHistoryKind kind;
}
