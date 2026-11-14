package com.example.coffee_hrm.common.net;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpAddressesTest {

    @Test
    void normalizesIpv4AndIpv6Spellings() {
        assertEquals("203.113.10.20", IpAddresses.normalize(" 203.113.10.20 "));
        assertEquals("2001:db8:0:0:0:0:0:10", IpAddresses.normalize("2001:0DB8::10"));
        assertEquals("2001:db8:0:0:0:0:0:10", IpAddresses.normalize("[2001:db8:0:0:0:0:0:10]"));
        assertEquals("203.113.10.20", IpAddresses.normalize("::ffff:203.113.10.20"));
        assertEquals("fe80:0:0:0:0:0:0:1", IpAddresses.normalize("fe80::1%eth0"));
    }

    @Test
    void rejectsHostnamesCidrAndMalformedValuesWithoutDnsLookup() {
        assertNull(IpAddresses.normalize("localhost"));
        assertNull(IpAddresses.normalize("cuahang.example.com"));
        assertNull(IpAddresses.normalize("10.0.0.0/24"));
        assertNull(IpAddresses.normalize("256.1.1.1"));
        assertNull(IpAddresses.normalize("1.2.3"));
        assertNull(IpAddresses.normalize("01.2.3.4"));
        assertNull(IpAddresses.normalize(""));
        assertNull(IpAddresses.normalize(null));
    }

    @Test
    void parsesAListSeparatedByNewlinesCommasAndSpaces() {
        assertEquals(Set.of("203.113.10.20", "113.161.1.1", "14.160.5.6"),
                IpAddresses.parseList("203.113.10.20\n113.161.1.1, 14.160.5.6 ;203.113.10.20"));
        assertTrue(IpAddresses.parseList("   ").isEmpty());
        assertTrue(IpAddresses.parseList(null).isEmpty());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> IpAddresses.parseList("203.113.10.20, abc, 1.2.3.999"));
        assertTrue(ex.getMessage().contains("abc"));
        assertTrue(ex.getMessage().contains("1.2.3.999"));
    }

    @Test
    void matchesOnlyAConfiguredValidIp() {
        List<String> allowed = List.of("203.113.10.20", "2001:db8::10", "garbage");
        assertTrue(IpAddresses.matches("203.113.10.20", allowed));
        assertTrue(IpAddresses.matches("2001:0db8:0:0:0:0:0:0010", allowed));
        assertFalse(IpAddresses.matches("203.113.10.21", allowed));
        assertFalse(IpAddresses.matches("garbage", allowed));
        assertFalse(IpAddresses.matches(null, allowed));
        assertTrue(IpAddresses.hasValid(allowed));
        assertFalse(IpAddresses.hasValid(List.of("garbage", "x.y")));
        assertFalse(IpAddresses.hasValid(List.of()));
    }

    @Test
    void classifiesPublicAndInternalAddresses() {
        assertTrue(IpAddresses.isPublic("203.113.10.20"));
        assertTrue(IpAddresses.isPublic("2001:4860:4860::8888"));
        for (String internal : new String[]{"10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.10",
                "127.0.0.1", "169.254.1.1", "100.64.0.1", "0.0.0.0", "::1", "fe80::1", "fd00::1", "224.0.0.1"}) {
            assertFalse(IpAddresses.isPublic(internal), internal);
        }
        assertTrue(IpAddresses.isPublic("172.32.0.1"));
    }

    @Test
    void clientIpUsesTheConnectionAddressNotForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.113.10.20");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        request.addHeader("X-Real-IP", "8.8.8.8");

        assertEquals("203.113.10.20", IpAddresses.clientIp(request));
    }
}
