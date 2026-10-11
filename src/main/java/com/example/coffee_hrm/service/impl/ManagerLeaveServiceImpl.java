package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.ReviewLeaveRequest;
import com.example.coffee_hrm.dto.response.ManagerLeaveDetail;
import com.example.coffee_hrm.dto.response.ManagerLeaveItem;
import com.example.coffee_hrm.dto.response.ManagerLeavePage;
import com.example.coffee_hrm.dto.response.ManagerLeaveReviewResult;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.LeaveBalance;
import com.example.coffee_hrm.entity.LeaveRequest;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.LeaveBalanceRepository;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ManagerLeaveService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagerLeaveServiceImpl implements ManagerLeaveService {
    private static final int PAGE_SIZE = 20;

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftChangeRequestRepository shiftChangeRequestRepository;

    @Override
    public ManagerLeavePage list(int page, ApprovalStatus status, AuthenticatedUser manager) {
        Store store = requireManagedStore(manager);
        if (page < 0 || page > 100_000) {
            throw new BusinessException("Trang danh sách không hợp lệ.");
        }
        Page<LeaveRequest> result = status == null
                ? leaveRequestRepository.findByEmployee_Store_IdOrderByCreatedAtDescIdDesc(
                        store.getId(), PageRequest.of(page, PAGE_SIZE))
                : leaveRequestRepository.findByEmployee_Store_IdAndStatusOrderByCreatedAtDescIdDesc(
                        store.getId(), status, PageRequest.of(page, PAGE_SIZE));
        return new ManagerLeavePage(result.getContent().stream().map(this::toItem).toList(),
                result.hasNext());
    }

    @Override
    public ManagerLeaveDetail detail(Integer id, AuthenticatedUser manager) {
        Store store = requireManagedStore(manager);
        LeaveRequest request = requireStoreRequest(id, store);
        List<ShiftAssignment> assignments = findActiveShifts(request, store.getId());
        return buildDetail(request, store, assignments);
    }

    @Override
    @Transactional
    public ManagerLeaveReviewResult review(Integer id, ReviewLeaveRequest input,
                                           AuthenticatedUser manager) {
        Store store = requireManagedStore(manager);
        if (input == null || input.getStatus() == null) {
            throw new BusinessException("Vui lòng chọn Duyệt hoặc Từ chối.");
        }
        if (input.getStatus() != ApprovalStatus.APPROVED
                && input.getStatus() != ApprovalStatus.REJECTED) {
            throw new BusinessException("Chỉ hỗ trợ duyệt hoặc từ chối đơn.");
        }

        Integer employeeId = leaveRequestRepository.findEmployeeIdForStore(id, store.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn nghỉ phép của cửa hàng này."));
        // Same employee lock order as Staff LeaveRequestServiceImpl: employee, then request.
        employeeRepository.lockByIdForLeaveRequest(employeeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên xin nghỉ."));
        LeaveRequest request = leaveRequestRepository.lockById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn nghỉ phép."));
        checkSameStore(request, store);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Đơn này đã được xử lý, không thể thay đổi trạng thái.");
        }
        User reviewer = userRepository.findById(manager.getUserId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản quản lý."));
        LocalDateTime resolvedAt = VietnamTime.now();

        if (input.getStatus() == ApprovalStatus.REJECTED) {
            String reason = input.getRejectReason() == null ? "" : input.getRejectReason().trim();
            if (reason.isBlank() || reason.length() > 500) {
                throw new BusinessException("Lý do từ chối bắt buộc, tối đa 500 ký tự.");
            }
            request.setStatus(ApprovalStatus.REJECTED);
            request.setRejectReason(reason);
            request.setApprovedBy(reviewer);
            request.setResolvedDate(resolvedAt);
            leaveRequestRepository.saveAndFlush(request);
            log.info("Leave request rejected: requestId={}, storeId={}, reviewerUserId={}",
                    id, store.getId(), reviewer.getId());
            return new ManagerLeaveReviewResult(id, ApprovalStatus.REJECTED, 0, 0);
        }

        // Date is checked in the transaction too, not only by a disabled UI button.
        if (!request.getStartDate().isAfter(VietnamTime.today())) {
            throw new BusinessException("Ngày bắt đầu nghỉ đã đến hoặc đã qua; không thể duyệt đơn.");
        }

        List<ShiftAssignment> assignments = findActiveShifts(request, store.getId());
        Map<Integer, Long> chargedDays = countDistinctWorkDatesByYear(assignments);
        Map<Integer, Long> otherPending = pendingDaysExcluding(request, store.getId());
        int storeYearlyLimit = store.getTotalLeaveDays() == null ? 0 : store.getTotalLeaveDays();
        if (storeYearlyLimit < 0) {
            throw new BusinessException("Định mức phép của cửa hàng chưa hợp lệ.");
        }

        // All quota and assignment changes remain in this single database transaction.
        for (Map.Entry<Integer, Long> charge : chargedDays.entrySet()) {
            int year = charge.getKey();
            int workDays = Math.toIntExact(charge.getValue());
            LeaveBalance balance = leaveBalanceRepository.lockByEmployeeIdAndYear(
                    request.getEmployee().getId(), year).orElse(null);
            int previouslyUsed = balance == null ? 0 : balance.getUsedDays();
            int reservedByOthers = Math.toIntExact(otherPending.getOrDefault(year, 0L));
            if (previouslyUsed < 0 || previouslyUsed + reservedByOthers + workDays > storeYearlyLimit) {
                throw new BusinessException("Quỹ phép năm " + year
                        + " không đủ. Vui lòng kiểm tra lại định mức và các đơn đang chờ.");
            }
            if (balance == null) {
                balance = LeaveBalance.builder()
                        .employee(request.getEmployee())
                        .year(year)
                        .totalDays(storeYearlyLimit)
                        .usedDays(0)
                        .remainingDays(storeYearlyLimit)
                        .build();
            }
            balance.setTotalDays(storeYearlyLimit);
            balance.setUsedDays(previouslyUsed + workDays);
            balance.setRemainingDays(storeYearlyLimit - balance.getUsedDays());
            leaveBalanceRepository.save(balance);
        }

        List<Integer> assignmentIds = assignments.stream().map(ShiftAssignment::getId).toList();
        if (!assignmentIds.isEmpty()) {
            // FK-safe: preserve assignments, reject any pending swap/transfer touching them.
            List<ShiftChangeRequest> related = shiftChangeRequestRepository
                    .findPendingForAffectedAssignments(assignmentIds, ApprovalStatus.PENDING);
            for (ShiftChangeRequest change : related) {
                change.setStatus(ApprovalStatus.REJECTED);
                change.setResolvedBy(reviewer);
                change.setResolvedDate(resolvedAt);
            }
            shiftChangeRequestRepository.saveAll(related);
            for (ShiftAssignment assignment : assignments) {
                assignment.setStatus(AssignmentStatus.CANCELLED);
            }
            shiftAssignmentRepository.saveAll(assignments);
        }

        request.setStatus(ApprovalStatus.APPROVED);
        request.setRejectReason(null);
        request.setApprovedBy(reviewer);
        request.setResolvedDate(resolvedAt);
        leaveRequestRepository.saveAndFlush(request);
        int totalDays = Math.toIntExact(chargedDays.values().stream().mapToLong(Long::longValue).sum());
        log.info("Leave request approved: requestId={}, storeId={}, reviewerUserId={}, days={}, releasedShifts={}",
                id, store.getId(), reviewer.getId(), totalDays, assignments.size());
        return new ManagerLeaveReviewResult(id, ApprovalStatus.APPROVED,
                totalDays, assignments.size());
    }

    private ManagerLeaveDetail buildDetail(LeaveRequest request, Store store,
                                           List<ShiftAssignment> assignments) {
        Map<Integer, Long> chargedDays = countDistinctWorkDatesByYear(assignments);
        List<ManagerLeaveDetail.YearCharge> years = new ArrayList<>();
        boolean enoughQuota = true;
        Map<Integer, Long> otherPending = pendingDaysExcluding(request, store.getId());
        for (Map.Entry<Integer, Long> entry : chargedDays.entrySet()) {
            int year = entry.getKey();
            int charged = Math.toIntExact(entry.getValue());
            int used = leaveBalanceRepository.findUsedDays(request.getEmployee().getId(), year)
                    .orElse(0);
            int total = store.getTotalLeaveDays() == null ? 0 : store.getTotalLeaveDays();
            int reserved = Math.toIntExact(otherPending.getOrDefault(year, 0L));
            int remainingAfter = total - used - reserved - charged;
            enoughQuota &= remainingAfter >= 0;
            years.add(new ManagerLeaveDetail.YearCharge(year, charged, total, used, reserved, remainingAfter));
        }
        boolean pending = request.getStatus() == ApprovalStatus.PENDING;
        boolean future = request.getStartDate().isAfter(VietnamTime.today());
        boolean canApprove = pending && future && enoughQuota;
        String blocked = null;
        if (!pending) {
            blocked = "Đơn đã được xử lý.";
        } else if (!future) {
            blocked = "Ngày nghỉ đã bắt đầu/đã qua.";
        } else if (!enoughQuota) {
            blocked = "Quỹ phép còn lại không đủ để duyệt.";
        }
        List<ManagerLeaveDetail.AffectedShift> shifts = assignments.stream()
                .map(sa -> new ManagerLeaveDetail.AffectedShift(sa.getId(), sa.getWorkDate(),
                        sa.getShift().getShiftName(), sa.getShift().getStartTime(),
                        sa.getShift().getEndTime()))
                .toList();
        return new ManagerLeaveDetail(toItem(request), canApprove, blocked,
                Math.toIntExact(chargedDays.values().stream().mapToLong(Long::longValue).sum()),
                years, shifts);
    }

    private Map<Integer, Long> pendingDaysExcluding(LeaveRequest request, Integer storeId) {
        LocalDate yearStart = LocalDate.of(request.getStartDate().getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(request.getEndDate().getYear(), 12, 31);
        return shiftAssignmentRepository.findPendingLeaveWorkDatesExcluding(
                        request.getEmployee().getId(), storeId, yearStart, yearEnd,
                        AssignmentStatus.ASSIGNED, ApprovalStatus.PENDING, request.getId())
                .stream().distinct()
                .collect(Collectors.groupingBy(LocalDate::getYear, TreeMap::new,
                        Collectors.counting()));
    }

    private Map<Integer, Long> countDistinctWorkDatesByYear(List<ShiftAssignment> assignments) {
        return assignments.stream().map(ShiftAssignment::getWorkDate).distinct()
                .collect(Collectors.groupingBy(LocalDate::getYear, TreeMap::new,
                        Collectors.counting()));
    }

    private List<ShiftAssignment> findActiveShifts(LeaveRequest request, Integer storeId) {
        return shiftAssignmentRepository.findActiveAssignmentsForLeave(
                request.getEmployee().getId(), storeId, request.getStartDate(),
                request.getEndDate(), AssignmentStatus.ASSIGNED);
    }

    private Store requireManagedStore(AuthenticatedUser user) {
        if (user == null || user.getEmployeeId() == null || user.getUserId() == null) {
            throw new BusinessException("Tài khoản chưa được phân công quản lý cửa hàng.");
        }
        Store store = storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa quản lý cửa hàng nào."));
        if (!Boolean.TRUE.equals(store.getIsActive())) {
            throw new BusinessException("Cửa hàng đang ngừng hoạt động.");
        }
        return store;
    }

    private LeaveRequest requireStoreRequest(Integer id, Store store) {
        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn nghỉ phép."));
        checkSameStore(request, store);
        return request;
    }

    private void checkSameStore(LeaveRequest request, Store store) {
        Employee employee = request.getEmployee();
        if (employee == null || employee.getStore() == null
                || !Objects.equals(employee.getStore().getId(), store.getId())) {
            throw new BusinessException("Không tìm thấy đơn nghỉ phép của cửa hàng này.");
        }
    }

    private ManagerLeaveItem toItem(LeaveRequest request) {
        return new ManagerLeaveItem(request.getId(), request.getEmployee().getId(),
                request.getEmployee().getFullName(), request.getStartDate(), request.getEndDate(),
                request.getReason(), request.getStatus(), request.getCreatedAt(),
                request.getResolvedDate(), request.getRejectReason(),
                request.getApprovedBy() == null ? null : request.getApprovedBy().getUsername());
    }
}
