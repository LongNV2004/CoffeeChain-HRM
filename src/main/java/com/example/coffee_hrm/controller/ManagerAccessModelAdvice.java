package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class ManagerAccessModelAdvice {

    private final ObjectProvider<UserRepository> userRepositories;

    @ModelAttribute("managerOperationsAllowed")
    public boolean managerOperationsAllowed() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return true;
        }
        if (principal.getRoleName() != RoleName.MANAGER) {
            return true;
        }
        UserRepository userRepository = userRepositories.getIfAvailable();
        if (userRepository == null) {
            return false;
        }
        User user = userRepository.findByIdWithRole(principal.getUserId()).orElse(null);
        return user != null
                && user.getRole() != null
                && user.getRole().getRoleName() == RoleName.MANAGER
                && user.getEmployee() != null
                && Boolean.TRUE.equals(user.getEmployee().getManagementCertified());
    }
}
