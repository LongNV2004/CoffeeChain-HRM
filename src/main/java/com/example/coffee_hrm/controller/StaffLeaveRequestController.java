package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.LeaveType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateLeaveRequest;
import com.example.coffee_hrm.dto.response.ApiResponse;
import com.example.coffee_hrm.dto.response.LeaveHistoryPage;
import com.example.coffee_hrm.dto.response.LeavePreviewResponse;
import com.example.coffee_hrm.dto.response.LeaveRequestResponse;
import com.example.coffee_hrm.dto.response.LeaveSlotPage;
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
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/staff/leave")
@PreAuthorize("hasRole('STAFF')")
public class StaffLeaveRequestController {
    private final LeaveRequestService leaveRequestService;

    @GetMapping
    public String page(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("leaveTypes", LeaveType.values());
        try {
            model.addAttribute("context", leaveRequestService.formContext(user));
        } catch (BusinessException ex) {
            model.addAttribute("pageError", ex.getMessage());
        }
        return "staff/StaffLeave";
    }

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
        return "redirect:/staff/leave";
    }

    private void restoreForm(CreateLeaveRequest form, RedirectAttributes redirect) {
        if (form == null) {
            return;
        }
        redirect.addFlashAttribute("leaveFormType", form.getLeaveType());
        redirect.addFlashAttribute("leaveFormSlots", form.getAssignmentIds());
        redirect.addFlashAttribute("leaveFormReason", form.getReason());
        redirect.addFlashAttribute("leaveFormPhone", form.getContactPhone());
    }

    @GetMapping("/slots")
    @ResponseBody
    public ResponseEntity<ApiResponse<LeaveSlotPage>> slots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.listSlots(from, to, user)));
        } catch (BusinessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/preview")
    @ResponseBody
    public ResponseEntity<ApiResponse<LeavePreviewResponse>> preview(
            @RequestParam List<Integer> assignmentIds,
            @RequestParam LeaveType leaveType,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    leaveRequestService.preview(assignmentIds, leaveType, user)));
        } catch (BusinessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/history")
    @ResponseBody
    public ResponseEntity<ApiResponse<LeaveHistoryPage>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    leaveRequestService.history(page, status, fromDate, toDate, user)));
        } catch (BusinessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> detail(
            @PathVariable Integer id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.detail(id, user)));
        } catch (BusinessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    @ResponseBody
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> cancel(
            @PathVariable Integer id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("Đã hủy đơn nghỉ phép.", leaveRequestService.cancel(id, user)));
        } catch (BusinessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }
}
