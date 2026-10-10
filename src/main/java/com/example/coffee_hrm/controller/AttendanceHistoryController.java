package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.AttendanceHistoryKind;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AttendanceHistoryQuery;
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

    @GetMapping("/staff/history")
    @PreAuthorize("hasRole('STAFF')")
    public String staffHistory(@RequestParam(value = "from", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(value = "to", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(value = "kind", required = false) String kind,
                               @AuthenticationPrincipal AuthenticatedUser user,
                               Model model) {
        try {
            AttendanceHistoryQuery query = AttendanceHistoryQuery.builder()
                    .fromDate(from)
                    .toDate(to)
                    .kind(AttendanceHistoryKind.parse(kind))
                    .build();
            model.addAttribute("history", attendanceHistoryService.getStaffHistory(user, query));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("history", emptyHistory(user, from, to, null, null));
            model.addAttribute("pageError", ex.getMessage());
        }
        return "staff/StaffAttendance";
    }

    @GetMapping("/manager/history")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerHistory(@RequestParam(value = "employeeId", required = false) Integer employeeId,
                                 @RequestParam(value = "shiftId", required = false) Integer shiftId,
                                 @RequestParam(value = "status", required = false) String status,
                                 @RequestParam(value = "late", required = false) Boolean late,
                                 @RequestParam(value = "early", required = false) Boolean early,
                                 @RequestParam(value = "from", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(value = "to", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(value = "kind", required = false) String kind,
                                 @AuthenticationPrincipal AuthenticatedUser user,
                                 Model model) {
        try {
            model.addAttribute("history", attendanceHistoryService.getManagerHistory(user, historyQuery(
                    from, to, employeeId, null, shiftId, status, late, early, kind)));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("history", emptyHistory(user, from, to, employeeId, null));
            model.addAttribute("pageError", ex.getMessage());
        }
        return "manager/ManagerAttendance";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminHistory(@RequestParam(value = "storeId", required = false) Integer storeId,
                               @RequestParam(value = "employeeId", required = false) Integer employeeId,
                               @RequestParam(value = "shiftId", required = false) Integer shiftId,
                               @RequestParam(value = "status", required = false) String status,
                               @RequestParam(value = "late", required = false) Boolean late,
                               @RequestParam(value = "early", required = false) Boolean early,
                               @RequestParam(value = "from", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(value = "to", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(value = "kind", required = false) String kind,
                               @AuthenticationPrincipal AuthenticatedUser user,
                               Model model) {
        try {
            model.addAttribute("history", attendanceHistoryService.getAdminHistory(user, historyQuery(
                    from, to, employeeId, storeId, shiftId, status, late, early, kind)));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("history", emptyHistory(user, from, to, employeeId, storeId));
            model.addAttribute("pageError", ex.getMessage());
        }
        return "admin/AdminAttendance";
    }

    private AttendanceHistoryQuery historyQuery(LocalDate from,
                                                LocalDate to,
                                                Integer employeeId,
                                                Integer storeId,
                                                Integer shiftId,
                                                String status,
                                                Boolean late,
                                                Boolean early,
                                                String kind) {
        return AttendanceHistoryQuery.builder()
                .fromDate(from)
                .toDate(to)
                .employeeId(employeeId)
                .storeId(storeId)
                .shiftId(shiftId)
                .status(parseStatus(status))
                .lateOnly(Boolean.TRUE.equals(late))
                .earlyOnly(Boolean.TRUE.equals(early))
                .kind(AttendanceHistoryKind.parse(kind))
                .build();
    }

    private AttendanceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AttendanceStatus.fromDbValue(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Trạng thái chấm công không hợp lệ.");
        }
    }

    private AttendanceHistoryView emptyHistory(AuthenticatedUser user,
                                               LocalDate from,
                                               LocalDate to,
                                               Integer employeeId,
                                               Integer storeId) {
        LocalDate today = VietnamTime.today();
        return AttendanceHistoryView.builder()
                .employeeId(user.getEmployeeId())
                .employeeName(user.getDisplayName())
                .storeName(user.getStoreName())
                .fromDate(from != null ? from : today.withDayOfMonth(1))
                .toDate(to != null ? to : today)
                .filterEmployeeId(employeeId)
                .filterStoreId(storeId)
                .employees(List.of())
                .stores(List.of())
                .shifts(List.of())
                .statuses(List.of())
                .rows(List.of())
                .recordCount(0)
                .totalHours(BigDecimal.ZERO)
                .totalHoursLabel("0 giờ")
                .build();
    }
}
