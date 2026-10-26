package com.example.coffee_hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SalaryPageController {

    @GetMapping("/admin/salary")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminSalary(Model model) {
        model.addAttribute("navRole", "admin");
        model.addAttribute("eyebrow", "Trụ sở — Admin");
        return "salary/SalaryPage";
    }

    @GetMapping("/manager/salary")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerSalary(Model model) {
        model.addAttribute("navRole", "manager");
        model.addAttribute("eyebrow", "Cửa hàng — Quản lý");
        return "salary/SalaryPage";
    }

    @GetMapping("/staff/salary")
    @PreAuthorize("hasRole('STAFF')")
    public String staffSalary(Model model) {
        model.addAttribute("navRole", "staff");
        model.addAttribute("eyebrow", "Cá nhân — Nhân viên");
        return "salary/SalaryPage";
    }
}
