package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AttendanceNetwork;
import com.example.coffee_hrm.dto.response.AttendanceClockView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface AttendanceService {

    AttendanceClockView getClock(AuthenticatedUser user);

    void checkIn(AuthenticatedUser user, AttendanceNetwork network);

    void checkOut(AuthenticatedUser user, AttendanceNetwork network);

    void previewCheckIn(AuthenticatedUser user, AttendanceNetwork network);

    void previewCheckOut(AuthenticatedUser user, AttendanceNetwork network);

    void previewCheckIn(AuthenticatedUser user, AttendanceNetwork network, Integer assignmentId);

    void previewCheckOut(AuthenticatedUser user, AttendanceNetwork network, Integer assignmentId);

    void checkInWithPhoto(AuthenticatedUser user, AttendanceNetwork network, byte[] photo);

    void checkOutWithPhoto(AuthenticatedUser user, AttendanceNetwork network, byte[] photo);

    void checkInWithPhoto(AuthenticatedUser user, AttendanceNetwork network, byte[] photo, Integer assignmentId);

    void checkOutWithPhoto(AuthenticatedUser user, AttendanceNetwork network, byte[] photo, Integer assignmentId);

    byte[] readShiftPhoto(AuthenticatedUser user, Integer attendanceId, boolean checkIn);

    void markAbsences(LocalDate workDate);
}
