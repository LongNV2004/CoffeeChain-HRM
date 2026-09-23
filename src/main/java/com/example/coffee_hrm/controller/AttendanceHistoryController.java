package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.AttendanceHistoryView;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance")
public class AttendanceHistoryController {

    private final AttendanceHistoryService attendanceHistoryService;

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public String staffHistory(@RequestParam(value = "from", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(value = "to", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @AuthenticationPrincipal AuthenticatedUser user,
                               Model model) {
        try {
            model.addAttribute("history", attendanceHistoryService.getStaffHistory(user, from, to));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("history", emptyHistory(user, from, to, null));
            model.addAttribute("pageError", ex.getMessage());
        }
        return "StaffAttendance";
    }

    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerHistory(@RequestParam(value = "employeeId", required = false) Integer employeeId,
                                 @RequestParam(value = "from", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(value = "to", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @AuthenticationPrincipal AuthenticatedUser user,
                                 Model model) {
        try {
            model.addAttribute("history", attendanceHistoryService.getManagerHistory(user, employeeId, from, to));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("history", emptyHistory(user, from, to, employeeId));
            model.addAttribute("pageError", ex.getMessage());
        }
        return "ManagerAttendance";
    }

    private AttendanceHistoryView emptyHistory(AuthenticatedUser user,
                                               LocalDate from,
                                               LocalDate to,
                                               Integer employeeId) {
        LocalDate today = LocalDate.now();
        return AttendanceHistoryView.builder()
                .employeeId(user.getEmployeeId())
                .employeeName(user.getDisplayName())
                .storeName(user.getStoreName())
                .fromDate(from != null ? from : today.withDayOfMonth(1))
                .toDate(to != null ? to : today)
                .filterEmployeeId(employeeId)
                .employees(List.of())
                .rows(List.of())
                .recordCount(0)
                .totalHours(BigDecimal.ZERO)
                .totalHoursLabel("0 giờ")
                .build();
    }
}
