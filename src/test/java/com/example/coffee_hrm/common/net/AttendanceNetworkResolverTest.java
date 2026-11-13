package com.example.coffee_hrm.common.net;

import com.example.coffee_hrm.service.AttendancePolicyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttendanceNetworkResolverTest {

    private static final String SERVER_PUBLIC_IP = "183.80.74.80";

    private AttendancePolicyProperties policy;
    private StubServerPublicIp serverPublicIp;
    private AttendanceNetworkResolver resolver;

    @BeforeEach
    void setUp() {
        policy = new AttendancePolicyProperties();
        serverPublicIp = new StubServerPublicIp(policy);
        resolver = new AttendanceNetworkResolver(serverPublicIp, policy);
    }

    @Test
    void publicConnectionIpIsUsedAsIsWithoutAskingForTheServerIp() {
        assertEquals("14.160.5.6", resolver.effectiveIp(request("14.160.5.6")));
        assertEquals(0, serverPublicIp.calls);
    }

    @Test
    void sameNetworkAsTheServerUsesTheServerPublicIp() {
        for (String local : new String[]{"0:0:0:0:0:0:0:1", "127.0.0.1", "192.168.1.25", "10.0.0.8"}) {
            assertEquals(SERVER_PUBLIC_IP, resolver.effectiveIp(request(local)), local);
        }
    }

    @Test
    void requestsThroughAProxyOrTunnelDoNotGetTheLocalNetworkRule() {
        for (String header : new String[]{"X-Forwarded-For", "Forwarded", "X-Real-IP", "CF-Connecting-IP", "Via"}) {
            MockHttpServletRequest tunnelled = request("127.0.0.1");
            tunnelled.addHeader(header, "8.8.8.8");
            // Người ở xa qua ngrok/Cloudflare hiện ra là loopback: giữ nguyên IP, không khớp IP cửa hàng.
            assertEquals("127.0.0.1", resolver.effectiveIp(tunnelled), header);
        }
    }

    @Test
    void forwardedHeadersNeverReplaceAPublicConnectionIp() {
        MockHttpServletRequest spoofed = request("14.160.5.6");
        spoofed.addHeader("X-Forwarded-For", SERVER_PUBLIC_IP);
        assertEquals("14.160.5.6", resolver.effectiveIp(spoofed));
    }

    @Test
    void ruleCanBeTurnedOffAndUnknownServerIpFallsBackToTheConnectionIp() {
        policy.setLocalNetworkUsesServerPublicIp(false);
        assertEquals("0:0:0:0:0:0:0:1", resolver.effectiveIp(request("::1")));

        policy.setLocalNetworkUsesServerPublicIp(true);
        serverPublicIp.answer = null;
        assertEquals("192.168.1.25", resolver.effectiveIp(request("192.168.1.25")));
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    /** Không gọi Internet trong test. */
    private static final class StubServerPublicIp extends ServerPublicIp {
        private String answer = SERVER_PUBLIC_IP;
        private int calls;

        private StubServerPublicIp(AttendancePolicyProperties policy) {
            super(policy);
        }

        @Override
        public String current() {
            calls++;
            return answer;
        }
    }
}
