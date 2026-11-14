package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.net.IpAddresses;
import com.example.coffee_hrm.dto.request.AttendanceNetwork;

import java.util.Collection;

/**
 * Quy tắc chấm công theo IP mạng, dùng chung cho chấm công ca làm, Manager và lớp đào tạo.
 * Chỉ cho chấm công khi IP thực tế của request trùng một IP hợp lệ đã cấu hình cho cửa hàng.
 * Cửa hàng chưa cấu hình IP (hoặc không còn IP hợp lệ) thì không ai chấm công được.
 */
public final class AttendanceNetworkRule {

    public static final String STORE_IP_MISSING =
            "Cửa hàng chưa được cấu hình địa chỉ IP mạng để chấm công. Vui lòng liên hệ Admin.";
    public static final String IP_UNKNOWN = "Không xác định được địa chỉ IP mạng của bạn.";
    public static final String IP_NOT_ALLOWED =
            "Bạn cần kết nối mạng Internet của cửa hàng để chấm công.";

    private AttendanceNetworkRule() {
    }

    public static void assertAllowed(Collection<String> allowedIps, AttendanceNetwork network) {
        if (!IpAddresses.hasValid(allowedIps)) {
            throw new BusinessException(STORE_IP_MISSING);
        }
        String clientIp = network == null ? null : IpAddresses.normalize(network.clientIp());
        if (clientIp == null) {
            throw new BusinessException(IP_UNKNOWN);
        }
        if (!IpAddresses.matches(clientIp, allowedIps)) {
            throw new BusinessException(IP_NOT_ALLOWED + " Địa chỉ IP hiện tại: " + clientIp + ".");
        }
    }
}
