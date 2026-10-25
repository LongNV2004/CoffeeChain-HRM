package com.example.coffee_hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ContractPageController {

    @GetMapping("/admin/contract")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminContract(Model model) {
        model.addAttribute("navRole", "admin");
        model.addAttribute("eyebrow", "Trụ sở — Admin");
        return "contract/ContractPage";
    }

    @GetMapping("/manager/contract")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerContract(Model model) {
        model.addAttribute("navRole", "manager");
        model.addAttribute("eyebrow", "Cửa hàng — Quản lý");
        return "contract/ContractPage";
    }

    @GetMapping("/staff/contract")
    @PreAuthorize("hasRole('STAFF')")
    public String staffContract(Model model) {
        model.addAttribute("navRole", "staff");
        model.addAttribute("eyebrow", "Cá nhân — Nhân viên");
        return "contract/ContractPage";
    }
}
