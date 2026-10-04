package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.AttendanceClockView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface AttendanceService {

    AttendanceClockView getClock(AuthenticatedUser user, String requestIp);

    void checkIn(AuthenticatedUser user, String requestIp);

    void checkOut(AuthenticatedUser user, String requestIp);

    void updateStoreIp(AuthenticatedUser user, String requestIp);

    void markAbsences(LocalDate workDate);
}
