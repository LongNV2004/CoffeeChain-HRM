package com.example.coffee_hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OvertimePageController {

    @GetMapping("/schedule/manager/overtime")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerOvertime(Model model) {
        model.addAttribute("navRole", "manager");
        model.addAttribute("eyebrow", "Cửa hàng — Quản lý");
        return "overtime/OvertimePage";
    }

    @GetMapping("/schedule/staff/overtime")
    @PreAuthorize("hasRole('STAFF')")
    public String staffOvertime(Model model) {
        model.addAttribute("navRole", "staff");
        model.addAttribute("eyebrow", "Cá nhân — Nhân viên");
        return "overtime/OvertimePage";
    }
}
