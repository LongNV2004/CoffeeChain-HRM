package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/leaves")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLeaveController {
    private final LeaveRequestService leaveRequestService;

    @GetMapping
    public String page(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) ApprovalStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                       Model model) {
        model.addAttribute("statuses", ApprovalStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("page", page);
        try {
            model.addAttribute("report", leaveRequestService.adminReport(page, status, fromDate, toDate));
        } catch (BusinessException ex) {
            model.addAttribute("pageError", ex.getMessage());
        }
        return "admin/AdminLeaveReport";
    }
}
