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

    /** Số phút sau giờ vào ca thì nhân viên mới được Check-out. */
    private int minimumShiftMinutesBeforeCheckout = 120;

    /** Check-out sau giờ kết thúc ca từ số phút này thì tính check-out muộn. */
    private int lateCheckoutThresholdMinutes = 120;

    /** Đào tạo: check-in sau giờ bắt đầu từ số phút này thì tính check-in muộn. */
    private int trainingLateThresholdMinutes = 10;

    /** Đào tạo: check-in sau giờ bắt đầu từ số phút này thì tính không tham gia. */
    private int trainingAbsentThresholdMinutes = 20;

    /** Đào tạo: Check-out mở trước giờ kết thúc buổi số phút này. */
    private int trainingCheckoutLeadMinutes = 5;

    private int radiusMeters = 500;
}
