package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.*;
import com.example.coffee_hrm.entity.*;
import com.example.coffee_hrm.repository.*;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.LeaveReplacementService;
import com.example.coffee_hrm.service.support.AvailabilityCoverage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveReplacementServiceImpl implements LeaveReplacementService {
    private static final int PAGE_SIZE = 20;
    private final StoreRepository storeRepository;
    private final LeaveWorkspaceRepository leaveRepository;
    private final LeaveReplacementAssignmentRepository gapRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final WorkAvailabilityRepository workAvailabilityRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public LeaveWorkspaceSummary summary(AuthenticatedUser manager) {
        Store store = managedStore(manager);
        return new LeaveWorkspaceSummary(
                leaveRepository.countByEmployee_Store_IdAndStatus(store.getId(), ApprovalStatus.PENDING),
                leaveRepository.countByEmployee_Store_IdAndStatus(store.getId(), ApprovalStatus.APPROVED),
                leaveRepository.countByEmployee_Store_IdAndStatus(store.getId(), ApprovalStatus.REJECTED),
                storeGapsForStore(store).size());
    }

    @Override
    public LeaveWorkspacePage search(AuthenticatedUser manager, int page, ApprovalStatus status,
                                     String keyword) {
        Store store = managedStore(manager);
        if (page < 0 || page > 100_000) {
            throw new BusinessException("Trang danh sách không hợp lệ.");
        }
        String search = keyword == null ? "" : keyword.trim();
        if (search.length() > 100) {
            throw new BusinessException("Tên tìm kiếm không được vượt quá 100 ký tự.");
        }
        Page<LeaveRequest> result = leaveRepository.findInStore(store.getId(), status, search,
                PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        List<LeaveWorkspaceRow> rows = result.getContent().stream()
                .map(r -> new LeaveWorkspaceRow(r.getId(), r.getEmployee().getFullName(),
                        r.getStartDate(), r.getEndDate(), r.getReason(), r.getStatus(), r.getCreatedAt()))
                .toList();
        return new LeaveWorkspacePage(rows, result.getTotalElements(), result.getTotalPages(), page);
    }

    @Override
    public List<LeaveReplacementGap> storeGaps(AuthenticatedUser manager) {
        return storeGapsForStore(managedStore(manager));
    }

    private List<LeaveReplacementGap> storeGapsForStore(Store store) {
        List<ShiftAssignment> cancelled = gapRepository.findStoreLeaveCancelledAssignments(
                store.getId(), VietnamTime.today(), AssignmentStatus.CANCELLED,
                ApprovalStatus.APPROVED);
        if (cancelled.isEmpty()) return List.of();
        Map<Integer, List<LeaveRequest>> approved = leaveRepository.findApprovedSince(
                store.getId(), ApprovalStatus.APPROVED, VietnamTime.today()).stream()
                .collect(Collectors.groupingBy(lr -> lr.getEmployee().getId()));
        return cancelled.stream().map(assignment -> {
            // Stage 2 has no LeaveRequestId on ShiftAssignments. Correlate by staff/date.
            LeaveRequest request = approved.getOrDefault(assignment.getEmployee().getId(), List.of())
                    .stream().filter(lr -> !assignment.getWorkDate().isBefore(lr.getStartDate())
                            && !assignment.getWorkDate().isAfter(lr.getEndDate()))
                    .findFirst().orElse(null);
            return request == null ? null : toGap(assignment, request.getId());
        }).filter(Objects::nonNull).toList();
    }

    @Override
    public List<LeaveReplacementGap> leaveGaps(AuthenticatedUser manager, Integer requestId) {
        Store store = managedStore(manager);
        LeaveRequest leave = approvedLeaveInStore(store, requestId);
        return gapRepository.findCancelledForLeave(leave.getEmployee().getId(), store.getId(),
                        leave.getStartDate(), leave.getEndDate(), AssignmentStatus.CANCELLED).stream()
                .map(a -> toGap(a, leave.getId())).toList();
    }

    @Override
    public LeaveReplacementSuggestion suggest(AuthenticatedUser manager, Integer requestId,
                                              Integer assignmentId) {
        Store store = managedStore(manager);
        LeaveRequest leave = approvedLeaveInStore(store, requestId);
        ShiftAssignment gap = gapRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy ca cần bố trí."));
        if (!Objects.equals(gap.getEmployee().getId(), leave.getEmployee().getId())
                || gap.getShift() == null
                || !Objects.equals(gap.getShift().getStore().getId(), store.getId())
                || gap.getStatus() != AssignmentStatus.CANCELLED
                || gap.getWorkDate().isBefore(leave.getStartDate())
                || gap.getWorkDate().isAfter(leave.getEndDate())) {
            throw new BusinessException("Ca này không thuộc đơn nghỉ đã duyệt của cửa hàng.");
        }
        LeaveReplacementGap gapView = toGap(gap, leave.getId());
        if (!gapView.upcoming()) {
            return new LeaveReplacementSuggestion(gapView, List.of());
        }
        return new LeaveReplacementSuggestion(gapView, rankCandidates(store, leave, gap));
    }

    private List<LeaveReplacementCandidate> rankCandidates(Store store, LeaveRequest leave,
                                                             ShiftAssignment gap) {
        LocalDate date = gap.getWorkDate();
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        Integer storeId = store.getId();
        List<ShiftAssignment> weekly = shiftAssignmentRepository.findWeeklyAssignmentsForStore(
                storeId, monday, sunday, AssignmentStatus.ASSIGNED);
        List<WorkAvailability> available = workAvailabilityRepository.findApprovedCovering(
                storeId, date, date, ApprovalStatus.APPROVED);
        Set<Integer> onLeave = leaveRepository.findApprovedOnDate(storeId, ApprovalStatus.APPROVED, date)
                .stream().map(r -> r.getEmployee().getId()).collect(Collectors.toSet());

        Map<Integer, Long> weeklyShiftCounts = weekly.stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId(), Collectors.counting()));
        List<ShiftAssignment> assignedToday = weekly.stream()
                .filter(a -> date.equals(a.getWorkDate())).toList();
        List<WorkAvailability> coveringToday = available.stream()
                .filter(a -> AvailabilityCoverage.appliesOn(a, date)).toList();
        var requesterRoleId = leave.getEmployee().getUser() != null
                && leave.getEmployee().getUser().getRole() != null
                ? leave.getEmployee().getUser().getRole().getRoleName() : null;
        List<LeaveReplacementCandidate> result = new ArrayList<>();

        for (Employee candidate : employeeRepository.findByStore_IdAndStatus(storeId, EmployeeStatus.ACTIVE)) {
            Integer id = candidate.getId();
            if (Objects.equals(id, leave.getEmployee().getId()) || onLeave.contains(id)) {
                continue;
            }
            if (assignedToday.stream().anyMatch(a -> Objects.equals(a.getEmployee().getId(), id)
                    && overlaps(a.getShift(), gap.getShift()))) {
                continue;
            }
            boolean hasApprovedAvailability = coveringToday.stream().anyMatch(a ->
                    Objects.equals(a.getEmployee().getId(), id)
                    && Objects.equals(a.getShift().getId(), gap.getShift().getId()));
            // Approved registrations are displayed as real occupied slots by ScheduleService.
            boolean registeredOverlap = coveringToday.stream().anyMatch(a ->
                    Objects.equals(a.getEmployee().getId(), id)
                    && !Objects.equals(a.getShift().getId(), gap.getShift().getId())
                    && overlaps(a.getShift(), gap.getShift()));
            if (registeredOverlap) {
                continue;
            }
            var candidateRoleId = candidate.getUser() != null && candidate.getUser().getRole() != null
                    ? candidate.getUser().getRole().getRoleName() : null;
            boolean sameRole = requesterRoleId != null && requesterRoleId.equals(candidateRoleId);
            result.add(new LeaveReplacementCandidate(id, candidate.getFullName(),
                    hasApprovedAvailability ? 1 : 2, hasApprovedAvailability, sameRole,
                    weeklyShiftCounts.getOrDefault(id, 0L)));
        }
        result.sort(Comparator.comparingInt(LeaveReplacementCandidate::priorityLevel)
                .thenComparing(LeaveReplacementCandidate::sameRole, Comparator.reverseOrder())
                .thenComparingLong(LeaveReplacementCandidate::assignedShiftsThisWeek)
                .thenComparing(LeaveReplacementCandidate::employeeName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(LeaveReplacementCandidate::employeeId));
        return result;
    }

    private boolean overlaps(Shift first, Shift second) {
        return first.getStartTime().isBefore(second.getEndTime())
                && first.getEndTime().isAfter(second.getStartTime());
    }

    private LeaveRequest approvedLeaveInStore(Store store, Integer requestId) {
        LeaveRequest request = leaveRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn nghỉ phép."));
        if (request.getStatus() != ApprovalStatus.APPROVED || request.getEmployee() == null
                || request.getEmployee().getStore() == null
                || !Objects.equals(request.getEmployee().getStore().getId(), store.getId())) {
            throw new BusinessException("Đơn không được duyệt hoặc không thuộc cửa hàng này.");
        }
        return request;
    }

    private Store managedStore(AuthenticatedUser manager) {
        if (manager == null || manager.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản không có quyền quản lý cửa hàng.");
        }
        Store store = storeRepository.findByManager_Id(manager.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Chưa được phân công quản lý cửa hàng."));
        if (!Boolean.TRUE.equals(store.getIsActive())) {
            throw new BusinessException("Cửa hàng không hoạt động.");
        }
        return store;
    }

    private LeaveReplacementGap toGap(ShiftAssignment assignment, Integer requestId) {
        Shift shift = assignment.getShift();
        boolean upcoming = !VietnamTime.isBeforeNow(assignment.getWorkDate(), shift.getStartTime());
        return new LeaveReplacementGap(assignment.getId(), requestId,
                assignment.getEmployee().getFullName(), assignment.getWorkDate(),
                shift.getShiftName(), shift.getStartTime(), shift.getEndTime(), upcoming);
    }
}
