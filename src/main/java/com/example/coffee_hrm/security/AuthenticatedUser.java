package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class AuthenticatedUser implements UserDetails {

    private final Integer userId;
    private final String username;
    private final String password;
    private final RoleName roleName;
    private final Integer employeeId;
    private final String displayName;
    private final Integer storeId;
    private final String storeName;
    private final boolean active;

    private AuthenticatedUser(Integer userId,
                              String username,
                              String password,
                              RoleName roleName,
                              Integer employeeId,
                              String displayName,
                              Integer storeId,
                              String storeName,
                              boolean active) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.roleName = roleName;
        this.employeeId = employeeId;
        this.displayName = displayName;
        this.storeId = storeId;
        this.storeName = storeName;
        this.active = active;
    }

    public static AuthenticatedUser from(User user) {
        Employee employee = user.getEmployee();
        Store store = employee != null ? employee.getStore() : null;
        String displayName = employee != null && employee.getFullName() != null
                ? employee.getFullName()
                : user.getUsername();
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole().getRoleName(),
                employee != null ? employee.getId() : null,
                displayName,
                store != null ? store.getId() : null,
                store != null ? store.getStoreName() : null,
                Boolean.TRUE.equals(user.getIsActive())
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleName.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
