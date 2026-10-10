package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.dto.response.TrainingClockView;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface TrainingAttendanceService {

    TrainingClockView getClock(AuthenticatedUser user);

    void checkIn(AuthenticatedUser user, Integer classId, AttendanceLocation location);

    void checkOut(AuthenticatedUser user, Integer classId, AttendanceLocation location);
}
