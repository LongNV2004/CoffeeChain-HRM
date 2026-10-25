package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.ApiResponse;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmploymentContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequiredArgsConstructor
public class EmploymentContractController {

    private final EmploymentContractService employmentContractService;

    @GetMapping("/admin/contracts/api")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<EmploymentContractResponse>>> adminList(
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return run(() -> employmentContractService.list(actor));
    }

    @GetMapping("/admin/contracts/api/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmploymentContractResponse>> adminDetail(
            @PathVariable Integer id,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return run(() -> employmentContractService.detail(actor, id));
    }

    @PostMapping("/admin/contracts/api")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmploymentContractResponse>> adminCreate(
            @Valid @RequestBody CreateEmploymentContractRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return run(() -> employmentContractService.create(actor, request));
    }

    @GetMapping("/manager/contracts/api")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<EmploymentContractResponse>>> managerList(
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return run(() -> employmentContractService.list(actor));
    }

    @GetMapping("/manager/contracts/api/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<EmploymentContractResponse>> managerDetail(
            @PathVariable Integer id,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return run(() -> employmentContractService.detail(actor, id));
    }

    @PostMapping("/manager/contracts/api")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<EmploymentContractResponse>> managerCreate(
            @Valid @RequestBody CreateEmploymentContractRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return run(() -> employmentContractService.create(actor, request));
    }

    private <T> ResponseEntity<ApiResponse<T>> run(Supplier<T> supplier) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(supplier.get()));
        } catch (BusinessException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
        }
    }
}
