package com.example.coffee_hrm.common.net;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Kiểm tra, chuẩn hóa và so khớp địa chỉ IP dùng cho chấm công.
 *
 * <p>Chỉ nhận chuỗi IP dạng số (IPv4/IPv6); không bao giờ tra DNS với tên miền. IP được chuẩn hóa để
 * {@code ::ffff:1.2.3.4} khớp {@code 1.2.3.4} và các cách viết IPv6 khác nhau khớp nhau.</p>
 *
 * <p>IP của request lấy từ {@link HttpServletRequest#getRemoteAddr()} — địa chỉ kết nối TCP thực tế mà
 * máy chủ nhận được. Không tự đọc {@code X-Forwarded-For}: header đó do client gửi và giả mạo được.
 * Khi chạy sau reverse proxy, bật {@code server.forward-headers-strategy=native} và khai báo
 * {@code server.tomcat.remoteip.internal-proxies} để Tomcat chỉ tin header từ proxy đã cấu hình
 * và tự cập nhật {@code getRemoteAddr()}.</p>
 */
public final class IpAddresses {

    /** Tối đa số IP cấu hình cho một cửa hàng. */
    public static final int MAX_PER_STORE = 20;

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");
    private static final Pattern SEPARATORS = Pattern.compile("[,;\\s]+");

    private IpAddresses() {
    }

    /** IP thực tế của request (đã chuẩn hóa), hoặc {@code null} nếu không xác định được. */
    public static String clientIp(HttpServletRequest request) {
        return request == null ? null : normalize(request.getRemoteAddr());
    }

    /**
     * Chuẩn hóa một IP dạng số. Trả về {@code null} nếu chuỗi không phải IPv4/IPv6 hợp lệ
     * (tên miền, CIDR, ký tự lạ...). Không thực hiện tra DNS.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone); // bỏ zone id của IPv6 link-local (fe80::1%eth0)
        }
        if (value.isEmpty()) {
            return null;
        }
        if (IPV4.matcher(value).matches()) {
            return toAddress(value);
        }
        // Chuỗi có dấu ':' và chỉ gồm ký tự hex/':'/'.' là IPv6 dạng số: InetAddress không tra DNS.
        if (value.indexOf(':') >= 0 && IPV6_CHARS.matcher(value).matches()) {
            return toAddress(value);
        }
        return null;
    }

    private static String toAddress(String literal) {
        try {
            InetAddress address = InetAddress.getByName(literal);
            if (address instanceof Inet6Address v6) {
                return v6.getHostAddress().toLowerCase(Locale.ROOT);
            }
            return address.getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    /** Tách nội dung ô nhập (xuống dòng, dấu phẩy, chấm phẩy hoặc khoảng trắng) thành từng mục. */
    public static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return parts;
        }
        for (String part : SEPARATORS.split(text.trim())) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    /**
     * Phân tích ô nhập của Admin thành danh sách IP chuẩn hóa, bỏ trùng, giữ thứ tự.
     *
     * @throws IllegalArgumentException kèm thông báo tiếng Việt nếu có mục không hợp lệ hoặc quá nhiều IP
     */
    public static Set<String> parseList(String text) {
        Set<String> result = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String part : split(text)) {
            String ip = normalize(part);
            if (ip == null) {
                invalid.add(part);
            } else {
                result.add(ip);
            }
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Địa chỉ IP không hợp lệ: " + String.join(", ", invalid) + ".");
        }
        if (result.size() > MAX_PER_STORE) {
            throw new IllegalArgumentException("Mỗi cửa hàng được cấu hình tối đa " + MAX_PER_STORE + " địa chỉ IP.");
        }
        return result;
    }

    /** {@code clientIp} có trùng một IP hợp lệ trong danh sách cấu hình hay không. */
    public static boolean matches(String clientIp, Iterable<String> allowed) {
        String client = normalize(clientIp);
        if (client == null || allowed == null) {
            return false;
        }
        for (String candidate : allowed) {
            if (client.equals(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    /** Danh sách cấu hình có ít nhất một IP hợp lệ hay không. */
    public static boolean hasValid(Iterable<String> allowed) {
        if (allowed == null) {
            return false;
        }
        for (String candidate : allowed) {
            if (normalize(candidate) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * IP mạng Internet công khai: không phải loopback, mạng nội bộ (10/8, 172.16/12, 192.168/16, fc00::/7),
     * link-local, CGNAT (100.64/10), multicast hay địa chỉ không xác định.
     */
    public static boolean isPublic(String ip) {
        String value = normalize(ip);
        if (value == null) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                return false;
            }
            byte[] bytes = address.getAddress();
            if (address instanceof Inet4Address) {
                int first = bytes[0] & 0xff;
                int second = bytes[1] & 0xff;
                return first != 0 && !(first == 100 && second >= 64 && second <= 127);
            }
            return (bytes[0] & 0xfe) != 0xfc; // IPv6 unique local fc00::/7
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
