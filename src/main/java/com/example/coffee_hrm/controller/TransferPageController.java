package com.example.coffee_hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TransferPageController {

    @GetMapping("/admin/transfer")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminTransfer(Model model) {
        model.addAttribute("navRole", "admin");
        model.addAttribute("eyebrow", "Trụ sở — Admin");
        model.addAttribute("summary", "Theo dõi và xử lý điều chuyển nhân sự giữa các cửa hàng trong chuỗi.");
        return "transfer/TransferPage";
    }

    @GetMapping("/manager/transfer")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerTransfer(Model model) {
        model.addAttribute("navRole", "manager");
        model.addAttribute("eyebrow", "Cửa hàng — Quản lý");
        model.addAttribute("summary", "Đề xuất và theo dõi điều chuyển nhân viên của cửa hàng.");
        return "transfer/TransferPage";
    }

    @GetMapping("/staff/transfer")
    @PreAuthorize("hasRole('STAFF')")
    public String staffTransfer(Model model) {
        model.addAttribute("navRole", "staff");
        model.addAttribute("eyebrow", "Cá nhân — Nhân viên");
        model.addAttribute("summary", "Xem và gửi yêu cầu điều chuyển sang cửa hàng khác.");
        return "transfer/TransferPage";
    }
}
