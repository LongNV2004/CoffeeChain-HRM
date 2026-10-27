package com.example.coffee_hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/training/management")
@PreAuthorize("hasRole('MANAGER')")
public class ManagementTrainingController {

    @GetMapping
    public String page() {
        return "redirect:/training";
    }

    @PostMapping("/{classId}/enroll")
    public String enroll(@PathVariable Integer classId,
                         RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Học viên được ghi danh khi Admin tạo lớp. Bạn xem lớp của mình trong danh sách đào tạo.");
        return "redirect:/training";
    }
}
