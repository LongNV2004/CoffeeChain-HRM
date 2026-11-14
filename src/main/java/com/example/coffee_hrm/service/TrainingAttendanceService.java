package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AttendanceNetwork;
import com.example.coffee_hrm.dto.response.TrainingClockView;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface TrainingAttendanceService {

    TrainingClockView getClock(AuthenticatedUser user);

    void checkIn(AuthenticatedUser user, Integer classId, AttendanceNetwork network);

    void checkOut(AuthenticatedUser user, Integer classId, AttendanceNetwork network);

    void previewCheckIn(AuthenticatedUser user, Integer classId, AttendanceNetwork network);

    void previewCheckOut(AuthenticatedUser user, Integer classId, AttendanceNetwork network);

    void checkInWithPhoto(AuthenticatedUser user, Integer classId, AttendanceNetwork network, byte[] photo);

    void checkOutWithPhoto(AuthenticatedUser user, Integer classId, AttendanceNetwork network, byte[] photo);

    byte[] readPhoto(AuthenticatedUser user, Integer attendanceId, boolean checkIn);
}
