package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.store.StoreAccessMessages;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InactiveStoreInterceptorTest {

    private final StoreAccessGuard guard = mock(StoreAccessGuard.class);
    private final InactiveStoreInterceptor interceptor = new InactiveStoreInterceptor(guard);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutAndLoginStayAvailable() throws Exception {
        authenticate(RoleName.STAFF);
        when(guard.deniesBusinessAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertTrue(interceptor.preHandle(request("/logout", null), new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request("/login", null), new MockHttpServletResponse(), new Object()));
        verify(guard, never()).deniesBusinessAccess(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void htmlNavigationIsRedirectedWhenTheStoreIsInactive() throws Exception {
        authenticate(RoleName.MANAGER);
        when(guard.deniesBusinessAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        MockHttpServletRequest request = request("/dashboard/manager", "navigate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals("/store-inactive", response.getRedirectedUrl());
    }

    @Test
    void apiCallsReceiveForbiddenWithTheSupportMessage() throws Exception {
        authenticate(RoleName.STAFF);
        when(guard.deniesBusinessAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        MockHttpServletRequest request = request("/attendance/staff", "cors");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains(StoreAccessMessages.INACTIVE));
        assertTrue(response.getContentAsString().contains(StoreAccessMessages.INACTIVE_CODE));
    }

    @Test
    void preOpeningStoreIsNotRedirected() throws Exception {
        authenticate(RoleName.STAFF);
        when(guard.deniesBusinessAccess(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request("/schedule/staff", "navigate"), response, new Object()));
        assertEquals(200, response.getStatus());
    }

    private void authenticate(RoleName roleName) {
        AuthenticatedUser user = AuthenticatedUser.from(User.builder()
                .id(3)
                .username("user")
                .passwordHash("x")
                .role(Role.builder().id(1).roleName(roleName).build())
                .isActive(true)
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private MockHttpServletRequest request(String path, String fetchMode) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        if (fetchMode != null) {
            request.addHeader("Sec-Fetch-Mode", fetchMode);
        }
        return request;
    }
}
