package com.example.coffee_hrm.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class ConnectedNetworkIpResolver {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final long CACHE_MILLIS = 60_000;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private volatile String cachedPublicIp;
    private volatile long cachedAt;

    /**
     * Khi mở web bằng localhost, socket chỉ là ::1/127.0.0.1.
     * IP mạng đang dùng là IP public của kết nối ra internet, giống trang kiểm tra IP.
     * Request từ internet thì dùng đúng IP socket, không tin header client tự gửi.
     */
    public String resolve(HttpServletRequest request) {
        String remote = ClientIpResolver.resolve(request);
        if (remote != null && !ClientIpResolver.isLocalOrPrivate(remote)) {
            return remote;
        }
        String publicIp = currentPublicIp();
        return publicIp != null ? publicIp : remote;
    }

    String currentPublicIp() {
        long now = System.currentTimeMillis();
        if (cachedPublicIp != null && now - cachedAt < CACHE_MILLIS) {
            return cachedPublicIp;
        }
        String lookedUp = lookupPublicIp();
        if (lookedUp != null) {
            cachedPublicIp = lookedUp;
            cachedAt = now;
            return lookedUp;
        }
        return cachedPublicIp;
    }

    private String lookupPublicIp() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.ipify.org"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            String ip = ClientIpResolver.normalize(response.body());
            if (ip == null || ClientIpResolver.isLocalOrPrivate(ip)) {
                return null;
            }
            return ip;
        } catch (Exception ex) {
            return null;
        }
    }
}
