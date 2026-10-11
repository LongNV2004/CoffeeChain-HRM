package com.example.coffee_hrm.controller;
import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.*;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.LeaveReplacementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/manager/leaves/api")
@PreAuthorize("hasRole('MANAGER')")
public class LeaveWorkspaceApiController {
    private final LeaveReplacementService service;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<LeaveWorkspaceSummary>> summary(
            @AuthenticationPrincipal AuthenticatedUser manager) {
        return run(() -> service.summary(manager));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<LeaveWorkspacePage>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "") String keyword,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        return run(() -> service.search(manager, page, status, keyword));
    }

    @GetMapping("/gaps")
    public ResponseEntity<ApiResponse<List<LeaveReplacementGap>>> gaps(
            @RequestParam(required = false) Integer leaveRequestId,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        return run(() -> leaveRequestId == null ? service.storeGaps(manager)
                : service.leaveGaps(manager, leaveRequestId));
    }

    @GetMapping("/gaps/{assignmentId}/suggestions")
    public ResponseEntity<ApiResponse<LeaveReplacementSuggestion>> suggestions(
            @PathVariable Integer assignmentId,
            @RequestParam Integer leaveRequestId,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        return run(() -> service.suggest(manager, leaveRequestId, assignmentId));
    }

    private <T> ResponseEntity<ApiResponse<T>> run(java.util.function.Supplier<T> supplier) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(supplier.get()));
        } catch (BusinessException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
        }
    }
}
