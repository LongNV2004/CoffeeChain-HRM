package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.ManagerContractAccess;
import com.example.coffee_hrm.security.StaffEmploymentGuard;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
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
    private final ObjectProvider<EmploymentContractRepository> contractRepositories;

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

    @ModelAttribute("managerContractLimited")
    public boolean managerContractLimited() {
        return managerLimit() != ManagerContractAccess.Limit.NONE;
    }

    @ModelAttribute("managerContractLimitedMessage")
    public String managerContractLimitedMessage() {
        return ManagerContractAccess.message(managerLimit());
    }

    private ManagerContractAccess.Limit managerLimit() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object cached = attributes.getAttribute(ManagerContractAccess.REQUEST_LIMIT, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof ManagerContractAccess.Limit limit) {
                return limit;
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ManagerContractAccess.Limit.NONE;
        }
        if (principal.getRoleName() != RoleName.MANAGER) {
            return ManagerContractAccess.Limit.NONE;
        }
        UserRepository userRepository = userRepositories.getIfAvailable();
        EmploymentContractRepository contracts = contractRepositories.getIfAvailable();
        if (userRepository == null || contracts == null || principal.getEmployeeId() == null) {
            return ManagerContractAccess.Limit.NO_CONTRACT;
        }
        User user = userRepository.findByIdWithRole(principal.getUserId()).orElse(null);
        if (user == null || user.getRole() == null || user.getRole().getRoleName() != RoleName.MANAGER) {
            return ManagerContractAccess.Limit.NONE;
        }
        Integer employeeId = user.getEmployee() == null ? null : user.getEmployee().getId();
        if (employeeId == null) {
            return ManagerContractAccess.Limit.NO_CONTRACT;
        }
        return ManagerContractAccess.assess(contracts.findByEmployeeId(employeeId), VietnamTime.today());
    }
}
