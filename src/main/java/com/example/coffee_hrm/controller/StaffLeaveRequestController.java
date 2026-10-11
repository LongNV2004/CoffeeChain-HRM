package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateLeaveRequest;
import com.example.coffee_hrm.dto.response.ApiResponse;
import com.example.coffee_hrm.dto.response.LeaveHistoryPage;
import com.example.coffee_hrm.dto.response.LeavePreviewResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.LeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/staff/leave")
@PreAuthorize("hasRole('STAFF')")
public class StaffLeaveRequestController {
    private final LeaveRequestService leaveRequestService;

    @PostMapping
    public String submit(@Valid @ModelAttribute CreateLeaveRequest form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().getFirst().getDefaultMessage();
            redirect.addFlashAttribute("leaveError", message);
            restoreForm(form, redirect);
        } else {
            try {
                leaveRequestService.create(form, user);
                redirect.addFlashAttribute("leaveSuccess", "Đã gửi đơn xin nghỉ, đang chờ quản lý duyệt.");
            } catch (BusinessException ex) {
                redirect.addFlashAttribute("leaveError", ex.getMessage());
                restoreForm(form, redirect);
            }
        }
        return "redirect:/dashboard/staff#leave";
    }

    private void restoreForm(CreateLeaveRequest form, RedirectAttributes redirect) {
        redirect.addFlashAttribute("leaveFormStart", form.getStartDate());
        redirect.addFlashAttribute("leaveFormEnd", form.getEndDate());
        redirect.addFlashAttribute("leaveFormReason", form.getReason());
    }

    @GetMapping("/preview")
    @ResponseBody
    public ResponseEntity<ApiResponse<LeavePreviewResponse>> preview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    leaveRequestService.preview(startDate, endDate, user)));
        } catch (BusinessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/history")
    @ResponseBody
    public ResponseEntity<ApiResponse<LeaveHistoryPage>> history(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.history(page, user)));
        } catch (BusinessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }
}
