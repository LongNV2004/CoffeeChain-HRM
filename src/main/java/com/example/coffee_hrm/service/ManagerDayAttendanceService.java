package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.ManagerDayAttendanceView;
import com.example.coffee_hrm.dto.response.ManagerMonthAttendanceView;
import com.example.coffee_hrm.dto.response.StaffDayAttendanceView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface ManagerDayAttendanceService {

    ManagerDayAttendanceView getDay(AuthenticatedUser user, LocalDate date);

    ManagerDayAttendanceView empty(LocalDate date);

    ManagerMonthAttendanceView getMonth(AuthenticatedUser user, String monthValue);

    ManagerMonthAttendanceView emptyMonth();

    StaffDayAttendanceView getOwnDay(AuthenticatedUser user, LocalDate date);

    StaffDayAttendanceView emptyOwnDay(LocalDate date);
}
