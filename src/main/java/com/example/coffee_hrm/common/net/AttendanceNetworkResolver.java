package com.example.coffee_hrm.common.net;

import com.example.coffee_hrm.dto.request.AttendanceNetwork;
import com.example.coffee_hrm.service.AttendancePolicyProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Xác định IP mạng của người đang chấm công (Staff, Manager; ca làm và đào tạo) — người dùng không phải nhập gì.
 *
 * <ul>
 *   <li>Request đến từ IP công khai: dùng đúng IP mà máy chủ nhận được.</li>
 *   <li>Request đến từ IP nội bộ/loopback (192.168.x.x, 10.x.x.x, 127.0.0.1, ::1...): IP nội bộ không đi qua
 *       Internet được, nên người dùng đang ở cùng mạng với máy chủ. IP mạng của họ là IP public của máy chủ
 *       ({@link ServerPublicIp}), do máy chủ tự tra — không lấy từ trình duyệt.</li>
 *   <li>Request có forwarding header (proxy, tunnel như ngrok/Cloudflare): người ở xa cũng hiện ra là loopback,
 *       nên không áp dụng quy tắc mạng nội bộ; dùng nguyên IP nhận được (sẽ không khớp IP cửa hàng).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AttendanceNetworkResolver {

    private static final List<String> FORWARDING_HEADERS = List.of(
            "Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Real-IP",
            "CF-Connecting-IP", "True-Client-IP", "X-Client-IP", "Via");

    private final ServerPublicIp serverPublicIp;
    private final AttendancePolicyProperties policy;

    public AttendanceNetwork resolve(HttpServletRequest request) {
        return new AttendanceNetwork(effectiveIp(request));
    }

    /** IP dùng để so khớp với danh sách IP cửa hàng (đã chuẩn hóa), hoặc {@code null}. */
    public String effectiveIp(HttpServletRequest request) {
        String connection = IpAddresses.clientIp(request);
        if (connection == null || IpAddresses.isPublic(connection)
                || !policy.isLocalNetworkUsesServerPublicIp() || proxied(request)) {
            return connection;
        }
        String publicIp = serverPublicIp.current();
        return publicIp != null ? publicIp : connection;
    }

    private static boolean proxied(HttpServletRequest request) {
        for (String header : FORWARDING_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
