package com.example.coffee_hrm.common.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public final class VietnamTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private VietnamTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalTime currentTime() {
        return LocalTime.now(ZONE);
    }

    public static boolean isBeforeNow(LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            return false;
        }
        LocalDateTime start = LocalDateTime.of(date, time.withSecond(0).withNano(0));
        LocalDateTime currentMinute = now().withSecond(0).withNano(0);
        return start.isBefore(currentMinute);
    }
}
