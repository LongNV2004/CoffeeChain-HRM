package com.example.coffee_hrm.report;

import com.example.coffee_hrm.common.time.VietnamTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AttendanceReportClockConfig {

    @Bean(name = "attendanceReportClock")
    Clock attendanceReportClock() {
        return Clock.system(VietnamTime.ZONE);
    }
}
