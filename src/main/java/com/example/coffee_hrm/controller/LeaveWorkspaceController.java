package com.example.coffee_hrm.controller;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@PreAuthorize("hasRole('MANAGER')")
public class LeaveWorkspaceController {
    @GetMapping("/manager/leaves")
    public String page(@AuthenticationPrincipal AuthenticatedUser manager) {
        return "manager/ManagerLeavePage";
    }
}
