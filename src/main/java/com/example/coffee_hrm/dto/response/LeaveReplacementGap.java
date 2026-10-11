package com.example.coffee_hrm.dto.response;
import java.time.LocalDate;
import java.time.LocalTime;
public record LeaveReplacementGap(Integer assignmentId, Integer leaveRequestId,
                                  String absentEmployeeName, LocalDate workDate,
                                  String shiftName, LocalTime startTime, LocalTime endTime,
                                  boolean upcoming) { }
