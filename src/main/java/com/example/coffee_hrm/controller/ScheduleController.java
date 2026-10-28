package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.CreateShiftChangeRequestDto;
import com.example.coffee_hrm.dto.response.ReliabilityLeaderboardView;
import com.example.coffee_hrm.dto.response.WeeklyScheduleView;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ReliabilityService;
import com.example.coffee_hrm.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ReliabilityService reliabilityService;

    // ==========================================
    // MANAGER ENDPOINTS
    // ==========================================

    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerSchedulePage(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model) {

        WeeklyScheduleView scheduleView = scheduleService.getWeeklyScheduleForManager(user, date);
        model.addAttribute("schedule", scheduleView);
        if (!model.containsAttribute("assignForm")) {
            model.addAttribute("assignForm", new AssignShiftRequest());
        }
        return "manager/ManagerSchedule";
    }

    @PostMapping("/manager/assign")
    @PreAuthorize("hasRole('MANAGER')")
    public String assignShift(
            @Valid @ModelAttribute("assignForm") AssignShiftRequest assignForm,
            BindingResult bindingResult,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        String returnDateParam = currentWeek != null ? "?date=" + currentWeek : "";

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().getFirst().getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", errorMsg != null ? errorMsg : "Dữ liệu phân ca không hợp lệ.");
            return "redirect:/schedule/manager" + returnDateParam;
        }

        try {
            scheduleService.assignShift(assignForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phân công ca làm việc thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/schedule/manager" + returnDateParam;
    }

    @PostMapping("/manager/cancel/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public String cancelAssignment(
            @PathVariable("id") Integer id,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        String returnDateParam = currentWeek != null ? "?date=" + currentWeek : "";

        try {
            scheduleService.cancelAssignment(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy phân công ca làm việc.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/schedule/manager" + returnDateParam;
    }

    @PostMapping("/manager/requests/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public String approveShiftChange(
            @PathVariable("id") Integer id,
            @RequestParam(value = "replacementEmployeeId", required = false) Integer replacementEmployeeId,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        String returnDateParam = currentWeek != null ? "?date=" + currentWeek : "";

        try {
            scheduleService.resolveShiftChange(id, true, replacementEmployeeId, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt yêu cầu đổi ca.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/schedule/manager" + returnDateParam;
    }

    @PostMapping("/manager/requests/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public String rejectShiftChange(
            @PathVariable("id") Integer id,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        String returnDateParam = currentWeek != null ? "?date=" + currentWeek : "";

        try {
            scheduleService.resolveShiftChange(id, false, null, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối yêu cầu đổi ca.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/schedule/manager" + returnDateParam;
    }

    @GetMapping("/manager/leaderboard")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerLeaderboardPage(
            @RequestParam(value = "month", required = false) String monthParam,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model) {

        YearMonth month = parseYearMonth(monthParam);
        ReliabilityLeaderboardView leaderboard = reliabilityService.getLeaderboardForManager(user, month);
        model.addAttribute("leaderboard", leaderboard);
        return "manager/ManagerLeaderboard";
    }

    @PostMapping("/manager/reliability/confirm-completed/{requestId}")
    @PreAuthorize("hasRole('MANAGER')")
    public String confirmCompletedSwappedShift(
            @PathVariable("requestId") Integer requestId,
            @RequestParam(value = "month", required = false) String monthParam,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        try {
            reliabilityService.confirmSwappedShiftCompletion(requestId, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận nhân viên hoàn thành tốt ca đổi (+2 điểm chuyên cần).");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/schedule/manager/leaderboard" + buildMonthQuery(monthParam);
    }

    @PostMapping("/manager/reliability/adjust")
    @PreAuthorize("hasRole('MANAGER')")
    public String adjustReliabilityScore(
            @RequestParam(value = "employeeId", required = false) Integer employeeId,
            @RequestParam(value = "pointsDelta", defaultValue = "0") int pointsDelta,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "month", required = false) String monthParam,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        try {
            reliabilityService.adjustScoreByManager(employeeId, pointsDelta, reason, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật điểm chuyên cần cho nhân viên thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/schedule/manager/leaderboard" + buildMonthQuery(monthParam);
    }

    // ==========================================
    // STAFF ENDPOINTS
    // ==========================================

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public String staffSchedulePage(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model) {

        prepareStaffSchedule(user, date, model, false);
        return "staff/StaffSchedule";
    }

    @GetMapping("/staff/shift-change")
    @PreAuthorize("hasRole('STAFF')")
    public String staffShiftChangePage(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model) {

        prepareStaffSchedule(user, date, model, true);
        return "staff/StaffSchedule";
    }

    private void prepareStaffSchedule(AuthenticatedUser user, LocalDate date, Model model, boolean shiftChangePage) {
        WeeklyScheduleView scheduleView = scheduleService.getWeeklyScheduleForStaff(user, date);
        model.addAttribute("schedule", scheduleView);
        model.addAttribute("currentEmployeeId", user.getEmployeeId());
        model.addAttribute("shiftChangePage", shiftChangePage);
        model.addAttribute("schedulePath", shiftChangePage ? "/schedule/staff/shift-change" : "/schedule/staff");
        if (!model.containsAttribute("changeForm")) {
            model.addAttribute("changeForm", new CreateShiftChangeRequestDto());
        }
    }

    private String staffScheduleRedirect(String origin, LocalDate currentWeek) {
        String path = "shift-change".equals(origin) ? "/schedule/staff/shift-change" : "/schedule/staff";
        return "redirect:" + path + (currentWeek != null ? "?date=" + currentWeek : "");
    }

    @PostMapping("/staff/request-change")
    @PreAuthorize("hasRole('STAFF')")
    public String requestShiftChange(
            @Valid @ModelAttribute("changeForm") CreateShiftChangeRequestDto changeForm,
            BindingResult bindingResult,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @RequestParam(value = "origin", required = false) String origin,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().getFirst().getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", errorMsg != null ? errorMsg : "Dữ liệu yêu cầu đổi ca không hợp lệ.");
            return staffScheduleRedirect(origin, currentWeek);
        }

        try {
            scheduleService.requestShiftChange(changeForm, user);
            if ("MANAGER_ASSIGN".equalsIgnoreCase(changeForm.getChangeType())) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã gửi yêu cầu đổi ca đến Quản lý cửa hàng xem xét.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lời mời đổi ca đến đồng nghiệp. Đơn sẽ được chuyển lên Quản lý khi đồng nghiệp đồng ý.");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return staffScheduleRedirect(origin, currentWeek);
    }

    @PostMapping("/staff/requests/{id}/respond")
    @PreAuthorize("hasRole('STAFF')")
    public String respondToIncomingRequest(
            @PathVariable("id") Integer id,
            @RequestParam("agreed") boolean agreed,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @RequestParam(value = "origin", required = false) String origin,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        try {
            scheduleService.respondToIncomingShiftChange(id, agreed, user);
            if (agreed) {
                redirectAttributes.addFlashAttribute("successMessage", "Bạn đã đồng ý đổi ca. Yêu cầu đã được chuyển lên cho Quản lý cửa hàng duyệt.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Bạn đã từ chối yêu cầu đổi ca từ đồng nghiệp.");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return staffScheduleRedirect(origin, currentWeek);
    }

    @GetMapping("/staff/leaderboard")
    @PreAuthorize("hasRole('STAFF')")
    public String staffLeaderboardPage(
            @RequestParam(value = "month", required = false) String monthParam,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model) {

        YearMonth month = parseYearMonth(monthParam);
        ReliabilityLeaderboardView leaderboard = reliabilityService.getLeaderboardForStaff(user, month);
        model.addAttribute("leaderboard", leaderboard);
        return "staff/StaffLeaderboard";
    }

    @PostMapping("/staff/requests/{id}/rescue")
    @PreAuthorize("hasRole('STAFF')")
    public String volunteerRescueShift(
            @PathVariable("id") Integer id,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @RequestParam(value = "origin", required = false) String origin,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        try {
            scheduleService.volunteerRescueShift(id, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Bạn đã xung phong nhận ca \"Hỗ trợ ca gấp\"! Đơn đã chuyển lên Quản lý duyệt (+5 điểm chuyên cần nếu ca trong vòng 24h).");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        if ("leaderboard".equalsIgnoreCase(origin)) {
            return "redirect:/schedule/staff/leaderboard";
        }
        return staffScheduleRedirect(origin, currentWeek);
    }

    private YearMonth parseYearMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(monthParam.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String buildMonthQuery(String monthParam) {
        YearMonth parsed = parseYearMonth(monthParam);
        return parsed != null ? "?month=" + parsed : "";
    }
}
