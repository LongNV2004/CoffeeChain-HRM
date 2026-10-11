package com.example.coffee_hrm.dto.response;
public record LeaveReplacementCandidate(Integer employeeId, String employeeName,
                                        int priorityLevel, boolean approvedAvailability,
                                        boolean sameRole, long assignedShiftsThisWeek) { }
