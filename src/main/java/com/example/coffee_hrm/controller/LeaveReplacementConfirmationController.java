package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.ApiResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.impl.ManagerLeaveServiceImpl;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
@RequestMapping("/manager/leaves/api/replacements")
public class LeaveReplacementConfirmationController {
    private final ManagerLeaveServiceImpl managerLeaveService;

    public record ConfirmRequest(Integer leaveRequestId, Integer employeeId) { }

    @GetMapping
    public ApiResponse<List<ManagerLeaveServiceImpl.ReplacementView>> list(
            @RequestParam(required = false) Integer leaveRequestId,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        return ApiResponse.ok(managerLeaveService.listReplacements(leaveRequestId, manager));
    }

    @PostMapping("/{assignmentId}/confirm")
    public ResponseEntity<ApiResponse<ManagerLeaveServiceImpl.ReplacementView>> confirm(
            @PathVariable Integer assignmentId, @RequestBody ConfirmRequest request,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        try {
            if (request == null) throw new BusinessException("Thiếu thông tin phân công.");
            return ResponseEntity.ok(ApiResponse.ok("Đã phân công người thay.",
                    managerLeaveService.confirmReplacement(request.leaveRequestId(), assignmentId,
                            request.employeeId(), manager)));
        } catch (BusinessException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/{assignmentId}/defer")
    public ResponseEntity<ApiResponse<ManagerLeaveServiceImpl.ReplacementView>> defer(
            @PathVariable Integer assignmentId, @RequestParam Integer leaveRequestId,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("Đã để lại ca để xử lý sau.",
                    managerLeaveService.deferReplacement(leaveRequestId, assignmentId, manager)));
        } catch (BusinessException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }
}
