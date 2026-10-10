package com.example.coffee_hrm.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "coffee-hrm.attendance")
public class AttendancePolicyProperties {

    private int checkInLeadMinutes = 60;

    private int lateThresholdMinutes = 20;

    /**
     * true: cho Check-out sớm và lưu số phút thiếu.
     * false: không cho Check-out trước giờ kết thúc ca.
     */
    private boolean allowEarlyCheckout = true;

    private int radiusMeters = 200;
}
