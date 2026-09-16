package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.RoleName;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class RoleBasedRedirector {

    public String resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return resolve(authenticatedUser.getRoleName());
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role)) {
                return resolve(RoleName.ADMIN);
            }
            if ("ROLE_MANAGER".equals(role)) {
                return resolve(RoleName.MANAGER);
            }
            if ("ROLE_STAFF".equals(role)) {
                return resolve(RoleName.STAFF);
            }
        }
        return null;
    }

    public String resolve(RoleName roleName) {
        if (roleName == null) {
            return null;
        }
        return switch (roleName) {
            case ADMIN -> "/dashboard/admin";
            case MANAGER -> "/dashboard/manager";
            case STAFF -> "/dashboard/staff";
        };
    }
}
