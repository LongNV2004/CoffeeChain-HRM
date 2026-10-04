package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.time.VietnamTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceAbsenceScheduler {

    private final AttendanceService attendanceService;

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void markEndedShiftsAbsent() {
        attendanceService.markAbsences(VietnamTime.today().minusDays(1));
        attendanceService.markAbsences(VietnamTime.today());
    }
}
