package com.example.coffee_hrm.dto.request;

/**
 * IP mạng của người đang chấm công. Chỉ được tạo phía máy chủ bởi
 * {@link com.example.coffee_hrm.common.net.AttendanceNetworkResolver}; không nhận IP do trình duyệt gửi lên.
 */
public record AttendanceNetwork(String clientIp) {
}
