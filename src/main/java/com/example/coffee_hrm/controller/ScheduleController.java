package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.CreateShiftChangeRequestDto;
import com.example.coffee_hrm.dto.response.WeeklyScheduleView;
import com.example.coffee_hrm.security.AuthenticatedUser;
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

@Controller
@RequiredArgsConstructor
@RequestMapping("/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

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
        return "ManagerSchedule";
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

    @PostMapping("/manager/publish")
    @PreAuthorize("hasRole('MANAGER')")
    public String publishWeeklySchedule(
            @RequestParam("weekStartDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        try {
            scheduleService.publishWeeklySchedule(weekStartDate, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã công bố lịch làm việc tuần này cho toàn bộ nhân viên.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/schedule/manager?date=" + weekStartDate;
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

    // ==========================================
    // STAFF ENDPOINTS
    // ==========================================

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public String staffSchedulePage(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model) {

        WeeklyScheduleView scheduleView = scheduleService.getWeeklyScheduleForStaff(user, date);
        model.addAttribute("schedule", scheduleView);
        model.addAttribute("currentEmployeeId", user.getEmployeeId());
        if (!model.containsAttribute("changeForm")) {
            model.addAttribute("changeForm", new CreateShiftChangeRequestDto());
        }
        return "StaffSchedule";
    }

    @PostMapping("/staff/request-change")
    @PreAuthorize("hasRole('STAFF')")
    public String requestShiftChange(
            @Valid @ModelAttribute("changeForm") CreateShiftChangeRequestDto changeForm,
            BindingResult bindingResult,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        String returnDateParam = currentWeek != null ? "?date=" + currentWeek : "";

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().getFirst().getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", errorMsg != null ? errorMsg : "Dữ liệu yêu cầu đổi ca không hợp lệ.");
            return "redirect:/schedule/staff" + returnDateParam;
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

        return "redirect:/schedule/staff" + returnDateParam;
    }

    @PostMapping("/staff/requests/{id}/respond")
    @PreAuthorize("hasRole('STAFF')")
    public String respondToIncomingRequest(
            @PathVariable("id") Integer id,
            @RequestParam("agreed") boolean agreed,
            @RequestParam(value = "currentWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentWeek,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes) {

        String returnDateParam = currentWeek != null ? "?date=" + currentWeek : "";

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

        return "redirect:/schedule/staff" + returnDateParam;
    }
}
