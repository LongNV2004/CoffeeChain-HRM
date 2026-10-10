package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.dto.response.AttendanceClockView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface AttendanceService {

    AttendanceClockView getClock(AuthenticatedUser user);

    void checkIn(AuthenticatedUser user, AttendanceLocation location);

    void checkOut(AuthenticatedUser user, AttendanceLocation location);

    void markAbsences(LocalDate workDate);
}
