package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.service.RecruitmentRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

@Controller
@RequiredArgsConstructor
public class RecruitmentRequestController {
    private final RecruitmentRequestService recruitmentRequestService;

    // Admin xem danh sách đề xuất
    @GetMapping("/admin/recruitment")
    public String adminRecruitment(Model model){
        model.addAttribute(
                "requests",
                recruitmentRequestService.getAllRequests()
        );
        return "admin/recruitment-list";
    }
    // Admin approve
    @PostMapping("/admin/recruitment/{id}/approve")
    public String approve(
            @PathVariable Integer id
    ){
        recruitmentRequestService.approveRequest(id);
        return "redirect:/admin/recruitment";
    }
    // Admin reject
    @PostMapping("/admin/recruitment/{id}/reject")
    public String reject(
            @PathVariable Integer id
    ){
        recruitmentRequestService.rejectRequest(id);
        return "redirect:/admin/recruitment";
    }
    // Manager mở form tạo request
    @GetMapping("/manager/recruitment/create")
    public String createPage(){
        return "manager/recruitment-create";
    }

    // Manager gửi request
    @PostMapping("/manager/recruitment/create")
    public String create(
            @RequestParam Integer storeId,
            @RequestParam Integer requestedNumber,
            @RequestParam String reason
    ){
        recruitmentRequestService.createRequest(
                storeId,
                requestedNumber,
                reason
        );
        return "redirect:/manager/recruitment/create";
    }
}