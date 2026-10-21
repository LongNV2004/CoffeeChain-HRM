package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.dto.response.AttendanceClockView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface AttendanceService {

    AttendanceClockView getClock(AuthenticatedUser user);

    void checkIn(AuthenticatedUser user, AttendanceLocation location);

    void checkOut(AuthenticatedUser user, AttendanceLocation location);

    void previewCheckIn(AuthenticatedUser user, AttendanceLocation location);

    void previewCheckOut(AuthenticatedUser user, AttendanceLocation location);

    void checkInWithPhoto(AuthenticatedUser user, AttendanceLocation location, byte[] photo);

    void checkOutWithPhoto(AuthenticatedUser user, AttendanceLocation location, byte[] photo);

    byte[] readShiftPhoto(AuthenticatedUser user, Integer attendanceId, boolean checkIn);

    void markAbsences(LocalDate workDate);
}
