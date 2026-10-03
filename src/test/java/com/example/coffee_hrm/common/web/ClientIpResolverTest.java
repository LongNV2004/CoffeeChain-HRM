package com.example.coffee_hrm.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void usesRemoteAddressAndIgnoresForwardedHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("113.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        assertEquals("113.0.0.1", ClientIpResolver.resolve(request));
    }

    @Test
    void normalizesMappedIpv4() {
        assertTrue(ClientIpResolver.matches("::ffff:113.0.0.1", "113.0.0.1"));
        assertFalse(ClientIpResolver.matches("10.0.0.1", "113.0.0.1"));
        assertFalse(ClientIpResolver.matches("113.0.0.1", null));
    }
}
