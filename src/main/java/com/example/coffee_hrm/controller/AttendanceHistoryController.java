package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.AttendanceHistoryKind;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AttendanceHistoryQuery;
import com.example.coffee_hrm.dto.response.AttendanceHistoryView;
import com.example.coffee_hrm.dto.response.ManagerMonthAttendanceView;
import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceHistoryService;
import com.example.coffee_hrm.service.ManagerDayAttendanceService;
import com.example.coffee_hrm.service.PayrollService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance")
public class AttendanceHistoryController {

    private final AttendanceHistoryService attendanceHistoryService;
    private final ManagerDayAttendanceService managerDayAttendanceService;
    private final PayrollService payrollService;

    @GetMapping("/staff/history")
    @PreAuthorize("hasRole('STAFF')")
    public String staffHistory(@RequestParam(value = "date", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @AuthenticationPrincipal AuthenticatedUser user,
                               Model model) {
        // Cùng mẫu với "Danh sách chấm công và lương" của Manager, nhưng dữ liệu chỉ của chính nhân viên.
        try {
            model.addAttribute("board", managerDayAttendanceService.getOwnDayBoard(user, date));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("board", managerDayAttendanceService.empty(date));
            model.addAttribute("pageError", ex.getMessage());
        }
        return "staff/StaffAttendance";
    }

    @GetMapping("/staff/history/month")
    @PreAuthorize("hasRole('STAFF')")
    public String staffMonth(@RequestParam(value = "month", required = false) String month,
                             @AuthenticationPrincipal AuthenticatedUser user,
                             Model model) {
        ManagerMonthAttendanceView board;
        try {
            board = managerDayAttendanceService.getOwnMonth(user, month);
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            board = managerDayAttendanceService.emptyMonth();
            model.addAttribute("pageError", ex.getMessage());
        }
        model.addAttribute("board", board);
        addPayroll(user, board, model);
        return "staff/StaffMonthAttendance";
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
                                 @RequestParam(value = "self", required = false) Boolean self,
                                 @AuthenticationPrincipal AuthenticatedUser user,
                                 Model model) {
        boolean ownHistory = Boolean.TRUE.equals(self);
        Integer scopedEmployeeId = ownHistory ? user.getEmployeeId() : employeeId;
        model.addAttribute("selfHistory", ownHistory);
        model.addAttribute("historyNav", !ownHistory && "training".equals(kind)
                ? "attendance-training-history" : "attendance-history");
        try {
            model.addAttribute("history", attendanceHistoryService.getManagerHistory(user, historyQuery(
                    from, to, scopedEmployeeId, null, shiftId, status, late, early, kind)));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("history", emptyHistory(user, from, to, employeeId, null));
            model.addAttribute("pageError", ex.getMessage());
        }
        return "manager/ManagerAttendance";
    }

    @GetMapping("/manager/day")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerDay(@RequestParam(value = "date", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @AuthenticationPrincipal AuthenticatedUser user,
                             Model model) {
        try {
            model.addAttribute("board", managerDayAttendanceService.getDay(user, date));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("board", managerDayAttendanceService.empty(date));
            model.addAttribute("pageError", ex.getMessage());
        }
        return "manager/ManagerDayAttendance";
    }

    @GetMapping("/manager/month")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerMonth(@RequestParam(value = "month", required = false) String month,
                               @AuthenticationPrincipal AuthenticatedUser user,
                               Model model) {
        ManagerMonthAttendanceView board;
        try {
            board = managerDayAttendanceService.getMonth(user, month);
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            board = managerDayAttendanceService.emptyMonth();
            model.addAttribute("pageError", ex.getMessage());
        }
        model.addAttribute("board", board);
        addPayroll(user, board, model);
        return "manager/ManagerMonthAttendance";
    }

    /**
     * Bảng lương cùng tháng, hiển thị chung với chấm công. Dữ liệu và quyền hạn lấy nguyên từ
     * {@link PayrollService}; màn hình chỉ ghép theo mã nhân viên.
     */
    private void addPayroll(AuthenticatedUser user, ManagerMonthAttendanceView board, Model model) {
        PayrollBoardView payroll;
        try {
            payroll = payrollService.board(user, null, null, board.getMonthValue(), null, null, null);
            model.addAttribute("payrollError", null);
        } catch (BusinessException ex) {
            payroll = null;
            model.addAttribute("payrollError", ex.getMessage());
        }
        Map<Integer, PayrollBoardView.Row> byEmployee = new HashMap<>();
        List<PayrollBoardView.Row> payrollOnly = new ArrayList<>();
        if (payroll != null) {
            Set<Integer> listed = new HashSet<>();
            for (ManagerMonthAttendanceView.EmployeeMonth card : board.getEmployees()) {
                listed.add(card.getEmployeeId());
            }
            for (PayrollBoardView.Row row : payroll.getRows()) {
                if (user.getRoleName() == RoleName.STAFF && !Objects.equals(row.getEmployeeId(), user.getEmployeeId())) {
                    continue; // phòng thủ thêm: nhân viên không bao giờ nhận dòng lương của người khác
                }
                byEmployee.put(row.getEmployeeId(), row);
                if (!listed.contains(row.getEmployeeId())) {
                    // Đã nghỉ hoặc chuyển cửa hàng nhưng vẫn có ca trong tháng: vẫn phải trả lương.
                    payrollOnly.add(row);
                }
            }
        }
        model.addAttribute("payroll", payroll);
        model.addAttribute("payByEmployee", byEmployee);
        model.addAttribute("payrollOnly", payrollOnly);
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
