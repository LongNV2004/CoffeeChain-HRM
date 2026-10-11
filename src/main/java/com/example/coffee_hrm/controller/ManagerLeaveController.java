package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ReviewLeaveRequest;
import com.example.coffee_hrm.dto.response.ApiResponse;
import com.example.coffee_hrm.dto.response.ManagerLeaveDetail;
import com.example.coffee_hrm.dto.response.ManagerLeavePage;
import com.example.coffee_hrm.dto.response.ManagerLeaveReviewResult;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ManagerLeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manager/leave")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class ManagerLeaveController {
    private final ManagerLeaveService service;

    @GetMapping
    public ResponseEntity<ApiResponse<ManagerLeavePage>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) ApprovalStatus status,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.list(page, status, manager)));
        } catch (BusinessException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ManagerLeaveDetail>> detail(
            @PathVariable Integer id,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.detail(id, manager)));
        } catch (BusinessException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(exception.getMessage()));
        }
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<ManagerLeaveReviewResult>> review(
            @PathVariable Integer id,
            @Valid @RequestBody ReviewLeaveRequest input,
            @AuthenticationPrincipal AuthenticatedUser manager) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.review(id, input, manager)));
        } catch (BusinessException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
        }
    }
}
