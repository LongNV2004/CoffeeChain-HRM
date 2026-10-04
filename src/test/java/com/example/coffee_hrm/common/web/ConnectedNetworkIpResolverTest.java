package com.example.coffee_hrm.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectedNetworkIpResolverTest {

    @Test
    void freshLookupSavesTheNewNetworkInsteadOfTheCachedOne() {
        SequenceResolver resolver = new SequenceResolver("118.68.6.70", "14.1.2.3");
        HttpServletRequest request = loopbackRequest();

        assertEquals("118.68.6.70", resolver.resolve(request));
        assertEquals("118.68.6.70", resolver.resolve(request));
        assertEquals("14.1.2.3", resolver.resolveFresh(request));
        assertEquals(2, resolver.calls);
    }

    @Test
    void freshLookupDoesNotFallBackToThePreviousNetwork() {
        SequenceResolver resolver = new SequenceResolver("118.68.6.70", null);
        HttpServletRequest request = loopbackRequest();

        assertEquals("118.68.6.70", resolver.resolve(request));
        assertNull(resolver.resolveFresh(request));
    }

    private static HttpServletRequest loopbackRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("::1");
        return request;
    }

    private static final class SequenceResolver extends ConnectedNetworkIpResolver {
        private final String[] values;
        private int index;
        private int calls;

        private SequenceResolver(String... values) {
            this.values = values;
        }

        @Override
        protected String lookupPublicIp() {
            calls++;
            String value = values[Math.min(index, values.length - 1)];
            if (index < values.length) {
                index++;
            }
            return value;
        }
    }
}
