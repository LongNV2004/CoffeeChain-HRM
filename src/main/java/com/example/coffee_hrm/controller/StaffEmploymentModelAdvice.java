package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.StaffEmploymentGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class StaffEmploymentModelAdvice {

    private final ObjectProvider<UserRepository> userRepositories;

    @ModelAttribute("staffEmploymentEnded")
    public boolean staffEmploymentEnded() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return false;
        }
        if (principal.getRoleName() != RoleName.STAFF) {
            return false;
        }
        UserRepository userRepository = userRepositories.getIfAvailable();
        if (userRepository == null) {
            return false;
        }
        User user = userRepository.findByIdWithRole(principal.getUserId()).orElse(null);
        return StaffEmploymentGuard.assess(user) == StaffEmploymentGuard.Block.EMPLOYMENT_ENDED;
    }

    @ModelAttribute("staffPendingContract")
    public boolean staffPendingContract() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return false;
        }
        if (principal.getRoleName() != RoleName.STAFF) {
            return false;
        }
        UserRepository userRepository = userRepositories.getIfAvailable();
        if (userRepository == null) {
            return false;
        }
        User user = userRepository.findByIdWithRole(principal.getUserId()).orElse(null);
        return StaffEmploymentGuard.assess(user) == StaffEmploymentGuard.Block.PENDING_CONTRACT;
    }
}
