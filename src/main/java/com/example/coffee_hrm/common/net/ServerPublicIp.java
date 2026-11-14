package com.example.coffee_hrm.common.net;

import com.example.coffee_hrm.service.AttendancePolicyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IP Internet công khai của chính máy chủ (IP mà mạng đặt máy chủ dùng để ra Internet).
 * Do máy chủ tự tra với dịch vụ công khai — không phụ thuộc dữ liệu trình duyệt gửi lên.
 * Kết quả được lưu tạm vài phút; tra lỗi thì trả về {@code null} (không cho chấm công thay vì đoán).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerPublicIp {

    private static final List<String> SOURCES = List.of(
            "https://api.ipify.org",
            "https://api64.ipify.org",
            "https://icanhazip.com");
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final Pattern IP_TEXT = Pattern.compile("^\\s*([0-9a-fA-F:.]+)\\s*$");

    private final AttendancePolicyProperties policy;

    private volatile String cachedIp;
    private volatile Instant cachedAt = Instant.EPOCH;

    /** IP public của máy chủ (đã chuẩn hóa), hoặc {@code null} nếu không xác định được. */
    public String current() {
        Instant now = Instant.now();
        long ttl = Math.max(policy.getServerPublicIpCacheSeconds(), 0);
        String cached = cachedIp;
        if (cached != null && now.isBefore(cachedAt.plusSeconds(ttl))) {
            return cached;
        }
        String fresh = lookup();
        if (fresh != null) {
            cachedIp = fresh;
            cachedAt = now;
            return fresh;
        }
        return cached; // tra lỗi tạm thời: dùng giá trị gần nhất nếu còn
    }

    /** Tra IP public, thử lần lượt các nguồn; chỉ nhận chuỗi là IP công khai hợp lệ. */
    protected String lookup() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        for (String source : SOURCES) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(source))
                        .timeout(TIMEOUT)
                        .header("Accept", "text/plain")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    continue;
                }
                Matcher matcher = IP_TEXT.matcher(response.body() == null ? "" : response.body());
                if (!matcher.matches()) {
                    continue;
                }
                String ip = IpAddresses.normalize(matcher.group(1));
                if (ip != null && IpAddresses.isPublic(ip)) {
                    return ip;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception ex) {
                log.debug("Không tra được IP public của máy chủ từ {}: {}", source, ex.getMessage());
            }
        }
        log.warn("Không xác định được IP public của máy chủ.");
        return null;
    }
}
