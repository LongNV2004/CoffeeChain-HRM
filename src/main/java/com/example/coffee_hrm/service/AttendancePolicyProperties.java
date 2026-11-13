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

    /** Check-out sau giờ kết thúc ca từ số phút này thì tính check-out muộn. */
    private int lateCheckoutThresholdMinutes = 120;

    /** Đào tạo: check-in sau giờ bắt đầu từ số phút này thì tính check-in muộn. */
    private int trainingLateThresholdMinutes = 10;

    /** Đào tạo: check-in sau giờ bắt đầu từ số phút này thì tính không tham gia. */
    private int trainingAbsentThresholdMinutes = 20;

    /** Đào tạo: Check-out mở trước giờ kết thúc buổi số phút này. */
    private int trainingCheckoutLeadMinutes = 5;

    /**
     * Máy chủ đặt trong mạng cửa hàng (chạy local/LAN): request đến từ IP nội bộ hoặc loopback thì người dùng
     * đang ở cùng mạng với máy chủ, nên IP mạng của họ chính là IP public của máy chủ. true: dùng IP public của
     * máy chủ để so khớp với danh sách IP cửa hàng. Không áp dụng khi request đi qua proxy/tunnel (có forwarding header).
     */
    private boolean localNetworkUsesServerPublicIp = true;

    /** Thời gian lưu tạm IP public của máy chủ (giây) trước khi tra lại. */
    private int serverPublicIpCacheSeconds = 300;
}
