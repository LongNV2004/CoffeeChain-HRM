package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.ReliabilityActionType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.ReliabilityLeaderboardView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.ReliabilityScoreLog;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ReliabilityScoreLogRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ReliabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReliabilityServiceImpl implements ReliabilityService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_MONTH_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String REF_SHIFT_CHANGE = "SHIFT_CHANGE";
    private static final String REF_SHIFT_ASSIGNMENT = "SHIFT_ASSIGNMENT";
    private static final String REF_MANAGER_ADJUST = "MANAGER_ADJUST";

    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final ShiftChangeRequestRepository shiftChangeRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ReliabilityScoreLogRepository reliabilityScoreLogRepository;

    @Override
    public boolean isWithin24Hours(LocalDate workDate, LocalTime startTime, LocalDateTime referenceTime) {
        if (workDate == null || startTime == null) {
            return false;
        }
        LocalDateTime ref = referenceTime != null ? referenceTime : VietnamTime.now();
        LocalDateTime shiftStart = LocalDateTime.of(workDate, startTime);
        long minutesUntilStart = ChronoUnit.MINUTES.between(ref, shiftStart);
        return minutesUntilStart >= 0 && minutesUntilStart <= 24 * 60;
    }

    @Override
    @Transactional
    public void recordLateShiftChangeIfUrgent(Employee requester, ShiftAssignment assignment, Integer requestId) {
        if (requester == null || assignment == null || assignment.getShift() == null) {
            return;
        }
        LocalDateTime now = VietnamTime.now();
        if (!isWithin24Hours(assignment.getWorkDate(), assignment.getShift().getStartTime(), now)) {
            return;
        }
        if (requestId != null && reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                requester.getId(), ReliabilityActionType.LATE_SHIFT_CANCEL.getDbValue(), REF_SHIFT_CHANGE, requestId)) {
            return;
        }
        String reason = "Xin hủy/đổi ca sát giờ (<24h): "
                + assignment.getShift().getShiftName()
                + " ngày " + assignment.getWorkDate().format(DATE_FORMATTER);
        applyScoreDelta(
                requester,
                ReliabilityActionType.LATE_SHIFT_CANCEL,
                LATE_CANCEL_PENALTY,
                reason,
                REF_SHIFT_CHANGE,
                requestId,
                now);
    }

    @Override
    @Transactional
    public void recordEmergencyDeclineIfApplicable(Employee targetEmployee, ShiftChangeRequest request) {
        if (targetEmployee == null || request == null || request.getAssignment() == null
                || request.getAssignment().getShift() == null) {
            return;
        }
        ShiftAssignment assignment = request.getAssignment();
        LocalDateTime now = VietnamTime.now();
        if (!isWithin24Hours(assignment.getWorkDate(), assignment.getShift().getStartTime(), now)) {
            return;
        }
        YearMonth currentMonth = YearMonth.from(now);
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        long priorDeclines = reliabilityScoreLogRepository.countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
                targetEmployee.getId(),
                List.of(
                        ReliabilityActionType.EMERGENCY_DECLINE_WARNING.getDbValue(),
                        ReliabilityActionType.EMERGENCY_DECLINE_PENALTY.getDbValue()),
                monthStart,
                monthEnd);

        String shiftLabel = assignment.getShift().getShiftName() + " ngày " + assignment.getWorkDate().format(DATE_FORMATTER);
        if (priorDeclines >= 1) {
            String reason = "Từ chối ca khẩn cấp (<24h) lần thứ " + (priorDeclines + 1) + " trong tháng: " + shiftLabel;
            applyScoreDelta(
                    targetEmployee,
                    ReliabilityActionType.EMERGENCY_DECLINE_PENALTY,
                    EXCESSIVE_EMERGENCY_DECLINE_PENALTY,
                    reason,
                    REF_SHIFT_CHANGE,
                    request.getId(),
                    now);
        } else {
            String reason = "Nhắc nhở lần 1 từ chối ca khẩn cấp (<24h): " + shiftLabel + " (Từ lần 2 trong tháng trừ 5đ)";
            applyScoreDelta(
                    targetEmployee,
                    ReliabilityActionType.EMERGENCY_DECLINE_WARNING,
                    0,
                    reason,
                    REF_SHIFT_CHANGE,
                    request.getId(),
                    now);
        }
    }

    @Override
    @Transactional
    public void recordRescueShiftOnApproval(ShiftChangeRequest approvedRequest, Employee rescuer) {
        if (approvedRequest == null || rescuer == null || approvedRequest.getAssignment() == null
                || approvedRequest.getAssignment().getShift() == null) {
            return;
        }
        if (Objects.equals(rescuer.getId(), approvedRequest.getEmployee().getId())) {
            return;
        }
        ShiftAssignment assignment = approvedRequest.getAssignment();
        LocalDate workDate = assignment.getWorkDate();
        LocalTime startTime = assignment.getShift().getStartTime();
        LocalDateTime now = VietnamTime.now();

        boolean rescuedWithin24h = isWithin24Hours(workDate, startTime, now)
                || (approvedRequest.getTargetAgreedAt() != null
                    && isWithin24Hours(workDate, startTime, approvedRequest.getTargetAgreedAt()))
                || (approvedRequest.getRequestDate() != null
                    && isWithin24Hours(workDate, startTime, approvedRequest.getRequestDate()));

        if (!rescuedWithin24h) {
            return;
        }
        if (approvedRequest.getId() != null
                && reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                        rescuer.getId(), ReliabilityActionType.RESCUE_SHIFT_24H.getDbValue(),
                        REF_SHIFT_CHANGE, approvedRequest.getId())) {
            return;
        }
        String reason = "Nhận ca \"Cứu bồ\" (<24h) hỗ trợ " + approvedRequest.getEmployee().getFullName()
                + " — " + assignment.getShift().getShiftName()
                + " ngày " + workDate.format(DATE_FORMATTER);
        applyScoreDelta(
                rescuer,
                ReliabilityActionType.RESCUE_SHIFT_24H,
                RESCUE_SHIFT_BONUS,
                reason,
                REF_SHIFT_CHANGE,
                approvedRequest.getId(),
                now);
    }

    @Override
    @Transactional
    public void recordCompletedSwappedShiftOnCheckout(Attendance attendance) {
        if (attendance == null || attendance.getEmployee() == null || attendance.getShift() == null
                || attendance.getWorkDate() == null) {
            return;
        }
        int lateMinutes = attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        int earlyMinutes = attendance.getEarlyLeaveMinutes() == null ? 0 : attendance.getEarlyLeaveMinutes();
        if (lateMinutes > 0 || earlyMinutes > 0) {
            return;
        }
        Employee employee = attendance.getEmployee();
        List<ShiftAssignment> sameDayAssignments = shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                employee.getId(), attendance.getWorkDate(), AssignmentStatus.ASSIGNED);
        ShiftAssignment matchedAssignment = sameDayAssignments.stream()
                .filter(sa -> sa.getShift() != null && Objects.equals(sa.getShift().getId(), attendance.getShift().getId()))
                .findFirst()
                .orElse(null);
        if (matchedAssignment == null || matchedAssignment.getId() == null) {
            return;
        }

        boolean isLeaveReplacement = matchedAssignment.getReplacesAssignment() != null;
        List<ShiftChangeRequest> relatedChanges = shiftChangeRequestRepository.findAllByAssignmentId(matchedAssignment.getId());
        ShiftChangeRequest approvedSwap = relatedChanges == null ? null : relatedChanges.stream()
                .filter(scr -> scr.getStatus() == ApprovalStatus.APPROVED)
                .filter(scr -> (scr.getTargetEmployee() != null && Objects.equals(scr.getTargetEmployee().getId(), employee.getId()))
                        || (scr.getTargetAssignment() != null && Objects.equals(scr.getTargetAssignment().getId(), matchedAssignment.getId())))
                .findFirst()
                .orElse(null);

        if (!isLeaveReplacement && approvedSwap == null) {
            return;
        }

        String refType = approvedSwap != null ? REF_SHIFT_CHANGE : REF_SHIFT_ASSIGNMENT;
        Integer refId = approvedSwap != null ? approvedSwap.getId() : matchedAssignment.getId();

        if (refId != null && reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                employee.getId(), ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue(), refType, refId)) {
            return;
        }

        String reason = "Hoàn thành tốt ca đổi/thay thế đúng giờ: "
                + attendance.getShift().getShiftName()
                + " ngày " + attendance.getWorkDate().format(DATE_FORMATTER);
        applyScoreDelta(
                employee,
                ReliabilityActionType.COMPLETE_SWAPPED_SHIFT,
                COMPLETED_SWAP_BONUS,
                reason,
                refType,
                refId,
                VietnamTime.now());
    }

    @Override
    @Transactional
    public void confirmSwappedShiftCompletion(Integer requestId, AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        ShiftChangeRequest request = shiftChangeRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn đổi ca."));
        if (request.getEmployee() == null || request.getEmployee().getStore() == null
                || !Objects.equals(request.getEmployee().getStore().getId(), store.getId())) {
            throw new BusinessException("Đơn đổi ca không thuộc cửa hàng bạn quản lý.");
        }
        if (request.getStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException("Chỉ có thể xác nhận hoàn thành tốt cho đơn đổi ca đã được duyệt.");
        }
        Employee targetEmp = request.getTargetEmployee();
        if (targetEmp == null) {
            throw new BusinessException("Đơn đổi ca này chưa có nhân viên nhận thay thế.");
        }
        if (reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                targetEmp.getId(), ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue(),
                REF_SHIFT_CHANGE, request.getId())) {
            throw new BusinessException("Đơn đổi ca này đã được cộng điểm hoàn thành tốt (+2đ) trước đó.");
        }
        ShiftAssignment assignment = request.getAssignment();
        String shiftLabel = assignment != null && assignment.getShift() != null
                ? assignment.getShift().getShiftName() + " ngày " + assignment.getWorkDate().format(DATE_FORMATTER)
                : "Đơn #" + request.getId();
        String reason = "Hoàn thành tốt ca đổi (" + shiftLabel + ") — Quản lý xác nhận";
        applyScoreDelta(
                targetEmp,
                ReliabilityActionType.COMPLETE_SWAPPED_SHIFT,
                COMPLETED_SWAP_BONUS,
                reason,
                REF_SHIFT_CHANGE,
                request.getId(),
                VietnamTime.now());
    }

    @Override
    @Transactional
    public void adjustScoreByManager(Integer employeeId, int pointsDelta, String reason, AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        if (employeeId == null) {
            throw new BusinessException("Vui lòng chọn nhân viên cần điều chỉnh điểm uy tín.");
        }
        if (pointsDelta == 0 || pointsDelta < -50 || pointsDelta > 50) {
            throw new BusinessException("Số điểm điều chỉnh phải khác 0 và nằm trong khoảng -50 đến +50 điểm.");
        }
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new BusinessException("Vui lòng nhập lý do thưởng hoặc trừ điểm uy tín.");
        }
        if (trimmedReason.length() > 250) {
            trimmedReason = trimmedReason.substring(0, 250);
        }
        Employee employee = employeeRepository.findByIdWithStore(employeeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        if (employee.getStore() == null || !Objects.equals(employee.getStore().getId(), store.getId())) {
            throw new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý.");
        }
        ReliabilityActionType actionType = pointsDelta > 0
                ? ReliabilityActionType.MANAGER_BONUS
                : ReliabilityActionType.MANAGER_PENALTY;
        applyScoreDelta(
                employee,
                actionType,
                pointsDelta,
                trimmedReason,
                REF_MANAGER_ADJUST,
                null,
                VietnamTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public ReliabilityLeaderboardView getLeaderboardForManager(AuthenticatedUser manager, YearMonth month) {
        Store store = resolveManagerStore(manager);
        return buildLeaderboardView(store, month, null, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ReliabilityLeaderboardView getLeaderboardForStaff(AuthenticatedUser staff, YearMonth month) {
        Employee currentStaff = resolveStaffEmployee(staff);
        Store store = currentStaff.getStore();
        if (store == null) {
            throw new BusinessException("Nhân viên chưa được gắn với cửa hàng nào.");
        }
        return buildLeaderboardView(store, month, currentStaff, false);
    }

    private ReliabilityLeaderboardView buildLeaderboardView(
            Store store,
            YearMonth requestedMonth,
            Employee currentStaff,
            boolean isManagerView) {

        YearMonth targetMonth = requestedMonth != null ? requestedMonth : YearMonth.from(VietnamTime.today());
        LocalDateTime monthStart = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEndExclusive = targetMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Employee> activeEmployees = employeeRepository.findByStore_IdAndStatus(store.getId(), EmployeeStatus.ACTIVE);
        Integer managerEmpId = store.getManager() != null ? store.getManager().getId() : null;
        List<Employee> staffMembers = activeEmployees.stream()
                .filter(e -> managerEmpId == null || !Objects.equals(e.getId(), managerEmpId))
                .toList();
        if (staffMembers.isEmpty()) {
            staffMembers = activeEmployees;
        }

        List<ReliabilityScoreLog> monthlyLogs = reliabilityScoreLogRepository.findForStoreInRange(
                store.getId(), monthStart, monthEndExclusive);

        Map<Integer, List<ReliabilityScoreLog>> logsByEmployee = new HashMap<>();
        for (ReliabilityScoreLog log : monthlyLogs) {
            if (log.getEmployee() != null && log.getEmployee().getId() != null) {
                logsByEmployee.computeIfAbsent(log.getEmployee().getId(), k -> new ArrayList<>()).add(log);
            }
        }

        int totalRescueMonth = 0;
        int totalCompletedSwapsMonth = 0;
        int totalLateCancelsMonth = 0;

        List<ReliabilityLeaderboardView.RankEntry> entries = new ArrayList<>();
        for (Employee emp : staffMembers) {
            List<ReliabilityScoreLog> empLogs = logsByEmployee.getOrDefault(emp.getId(), List.of());
            int netDelta = 0;
            int posPoints = 0;
            int negPoints = 0;
            int rescueCount = 0;
            int completedSwapCount = 0;
            int lateCancelCount = 0;
            int emergencyDeclineCount = 0;

            for (ReliabilityScoreLog l : empLogs) {
                int delta = l.getPointsDelta() != null ? l.getPointsDelta() : 0;
                netDelta += delta;
                if (delta > 0) {
                    posPoints += delta;
                } else if (delta < 0) {
                    negPoints += delta;
                }
                String action = l.getActionType();
                if (ReliabilityActionType.RESCUE_SHIFT_24H.getDbValue().equalsIgnoreCase(action)) {
                    rescueCount++;
                    totalRescueMonth++;
                } else if (ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue().equalsIgnoreCase(action)) {
                    completedSwapCount++;
                    totalCompletedSwapsMonth++;
                } else if (ReliabilityActionType.LATE_SHIFT_CANCEL.getDbValue().equalsIgnoreCase(action)) {
                    lateCancelCount++;
                    totalLateCancelsMonth++;
                } else if (ReliabilityActionType.EMERGENCY_DECLINE_PENALTY.getDbValue().equalsIgnoreCase(action)
                        || ReliabilityActionType.EMERGENCY_DECLINE_WARNING.getDbValue().equalsIgnoreCase(action)) {
                    emergencyDeclineCount++;
                }
            }

            int score = emp.getEffectiveReliabilityScore();
            TierMeta tier = resolveTier(score);

            entries.add(ReliabilityLeaderboardView.RankEntry.builder()
                    .employeeId(emp.getId())
                    .fullName(emp.getFullName())
                    .email(emp.getEmail())
                    .phone(emp.getPhone())
                    .certified(emp.getCertificationStatus() == CertificationStatus.CERTIFIED)
                    .currentStaff(currentStaff != null && Objects.equals(currentStaff.getId(), emp.getId()))
                    .reliabilityScore(score)
                    .monthlyNetDelta(netDelta)
                    .monthlyPositivePoints(posPoints)
                    .monthlyNegativePoints(negPoints)
                    .rescueShiftsCount(rescueCount)
                    .completedSwapsCount(completedSwapCount)
                    .lateCancelsCount(lateCancelCount)
                    .emergencyDeclinesCount(emergencyDeclineCount)
                    .tierCode(tier.code())
                    .tierLabel(tier.label())
                    .tierBadgeClass(tier.badgeClass())
                    .build());
        }

        entries.sort(Comparator
                .comparingInt(ReliabilityLeaderboardView.RankEntry::getReliabilityScore).reversed()
                .thenComparing(Comparator.comparingInt(ReliabilityLeaderboardView.RankEntry::getMonthlyNetDelta).reversed())
                .thenComparing(Comparator.comparingInt(ReliabilityLeaderboardView.RankEntry::getRescueShiftsCount).reversed())
                .thenComparing(Comparator.comparingInt(ReliabilityLeaderboardView.RankEntry::getCompletedSwapsCount).reversed())
                .thenComparing(ReliabilityLeaderboardView.RankEntry::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        for (int i = 0; i < entries.size(); i++) {
            ReliabilityLeaderboardView.RankEntry entry = entries.get(i);
            int rank = i + 1;
            entry.setRank(rank);
            boolean top3Qualifies = rank <= 3 && entry.getReliabilityScore() >= INITIAL_SCORE;
            boolean prioritySchedule = top3Qualifies || entry.getReliabilityScore() >= 105;
            entry.setKpiRewardEligible(top3Qualifies);
            entry.setPriorityScheduleEligible(prioritySchedule);
            if (top3Qualifies && rank == 1) {
                entry.setRewardBadgeLabel("👑 Quán quân KPI Tháng + Ưu tiên lịch tuần sau");
            } else if (top3Qualifies) {
                entry.setRewardBadgeLabel("🏆 Thưởng KPI Tháng + Ưu tiên lịch tuần sau");
            } else if (prioritySchedule) {
                entry.setRewardBadgeLabel("⚡ Ưu tiên chọn lịch làm tuần sau");
            } else if (entry.getReliabilityScore() < 90) {
                entry.setRewardBadgeLabel("⚠️ Cần cải thiện kỷ luật nhận/đổi ca");
            } else {
                entry.setRewardBadgeLabel("Duy trì điểm uy tín tốt");
            }
        }

        List<ReliabilityLeaderboardView.RankEntry> podium = entries.stream()
                .limit(3)
                .toList();

        double avgScore = entries.isEmpty()
                ? INITIAL_SCORE
                : entries.stream().mapToInt(ReliabilityLeaderboardView.RankEntry::getReliabilityScore).average().orElse(INITIAL_SCORE);

        List<ReliabilityLeaderboardView.RescueShiftItem> openRescueShifts = buildOpenRescueShifts(store.getId(), currentStaff);
        List<ReliabilityLeaderboardView.CompletedSwapCandidate> swapCandidates = isManagerView
                ? buildCompletedSwapCandidates(store.getId())
                : List.of();

        List<ReliabilityLeaderboardView.ScoreLogItem> recentLogs = reliabilityScoreLogRepository
                .findRecentForStore(store.getId(), PageRequest.of(0, 25))
                .stream()
                .map(this::toScoreLogItem)
                .toList();

        ReliabilityLeaderboardView.RankEntry mySummary = null;
        String nextTierLabel = "Chiến thần cứu ca (115+ đ)";
        int pointsToNextTier = 0;
        int progressPercent = 100;
        List<ReliabilityLeaderboardView.ScoreLogItem> myRecentLogs = List.of();

        if (currentStaff != null) {
            mySummary = entries.stream()
                    .filter(e -> Objects.equals(e.getEmployeeId(), currentStaff.getId()))
                    .findFirst()
                    .orElse(null);
            int myScore = mySummary != null ? mySummary.getReliabilityScore() : currentStaff.getEffectiveReliabilityScore();
            if (myScore < 90) {
                nextTierLabel = "Ổn định (90 đ)";
                pointsToNextTier = 90 - myScore;
                progressPercent = Math.max(10, (myScore * 100) / 90);
            } else if (myScore < 105) {
                nextTierLabel = "Đáng tin cậy — Ưu tiên lịch (105 đ)";
                pointsToNextTier = 105 - myScore;
                progressPercent = Math.min(95, Math.max(15, ((myScore - 90) * 100) / 15));
            } else if (myScore < 115) {
                nextTierLabel = "Chiến thần cứu ca (115 đ)";
                pointsToNextTier = 115 - myScore;
                progressPercent = Math.min(95, Math.max(25, ((myScore - 105) * 100) / 10));
            } else {
                nextTierLabel = "Đã đạt cấp cao nhất: Chiến thần cứu ca";
                pointsToNextTier = 0;
                progressPercent = 100;
            }
            myRecentLogs = reliabilityScoreLogRepository
                    .findRecentForEmployee(currentStaff.getId(), PageRequest.of(0, 15))
                    .stream()
                    .map(this::toScoreLogItem)
                    .toList();
        }

        return ReliabilityLeaderboardView.builder()
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .storeAddress(store.getAddress())
                .selectedMonth(targetMonth.toString())
                .periodLabel("Tháng " + String.format("%02d/%d", targetMonth.getMonthValue(), targetMonth.getYear()))
                .prevMonthParam(targetMonth.minusMonths(1).toString())
                .nextMonthParam(targetMonth.plusMonths(1).toString())
                .totalStaffCount(entries.size())
                .averageReliabilityScore(String.format(Locale.US, "%.1f", avgScore))
                .totalRescueShiftsMonth(totalRescueMonth)
                .totalCompletedSwapsMonth(totalCompletedSwapsMonth)
                .totalLateCancelsMonth(totalLateCancelsMonth)
                .openRescueShiftsCount(openRescueShifts.size())
                .podiumEntries(podium)
                .leaderboardEntries(entries)
                .mySummary(mySummary)
                .nextTierLabel(nextTierLabel)
                .pointsToNextTier(pointsToNextTier)
                .progressPercent(progressPercent)
                .myRecentLogs(myRecentLogs)
                .openRescueShifts(openRescueShifts)
                .completedSwapCandidates(swapCandidates)
                .recentLogs(recentLogs)
                .build();
    }

    private List<ReliabilityLeaderboardView.RescueShiftItem> buildOpenRescueShifts(
            Integer storeId,
            Employee currentStaff) {
        List<ShiftChangeRequest> openRequests = shiftChangeRequestRepository.findOpenRescueRequestsForStore(storeId);
        if (openRequests == null || openRequests.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = VietnamTime.now();
        List<ReliabilityLeaderboardView.RescueShiftItem> items = new ArrayList<>();
        for (ShiftChangeRequest req : openRequests) {
            ShiftAssignment sa = req.getAssignment();
            if (sa == null || sa.getShift() == null || sa.getWorkDate() == null) {
                continue;
            }
            Shift shift = sa.getShift();
            if (VietnamTime.isBeforeNow(sa.getWorkDate(), shift.getStartTime())) {
                continue;
            }
            LocalDateTime shiftStart = LocalDateTime.of(sa.getWorkDate(), shift.getStartTime());
            long hoursUntil = Math.max(0, ChronoUnit.HOURS.between(now, shiftStart));
            boolean within24h = isWithin24Hours(sa.getWorkDate(), shift.getStartTime(), now);
            boolean canVolunteer = currentStaff != null
                    && !Objects.equals(currentStaff.getId(), req.getEmployee().getId())
                    && currentStaff.getCertificationStatus() == CertificationStatus.CERTIFIED;

            items.add(ReliabilityLeaderboardView.RescueShiftItem.builder()
                    .requestId(req.getId())
                    .assignmentId(sa.getId())
                    .requesterId(req.getEmployee().getId())
                    .requesterName(req.getEmployee().getFullName())
                    .shiftName(shift.getShiftName())
                    .workDate(sa.getWorkDate())
                    .formattedDate(sa.getWorkDate().format(DATE_FORMATTER))
                    .timeRange(shift.getStartTime().format(TIME_FORMATTER) + " – " + shift.getEndTime().format(TIME_FORMATTER))
                    .reason(req.getReason())
                    .urgentWithin24h(within24h)
                    .hoursUntilStart(hoursUntil)
                    .rescueBonusPoints(within24h ? RESCUE_SHIFT_BONUS : COMPLETED_SWAP_BONUS)
                    .canVolunteer(canVolunteer)
                    .build());
        }
        return items;
    }

    private List<ReliabilityLeaderboardView.CompletedSwapCandidate> buildCompletedSwapCandidates(Integer storeId) {
        List<ShiftChangeRequest> allRequests = shiftChangeRequestRepository.findAllForStore(storeId);
        if (allRequests == null || allRequests.isEmpty()) {
            return List.of();
        }
        List<ReliabilityLeaderboardView.CompletedSwapCandidate> list = new ArrayList<>();
        for (ShiftChangeRequest req : allRequests) {
            if (req.getStatus() != ApprovalStatus.APPROVED || req.getTargetEmployee() == null
                    || req.getAssignment() == null || req.getAssignment().getShift() == null) {
                continue;
            }
            Employee targetEmp = req.getTargetEmployee();
            boolean rewarded = reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                    targetEmp.getId(), ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue(),
                    REF_SHIFT_CHANGE, req.getId());

            ShiftAssignment sa = req.getAssignment();
            Shift s = sa.getShift();
            String typeLabel = req.getTargetAssignment() != null ? "Đổi chéo ca" : "Nhận ca thay / Cứu bồ";

            list.add(ReliabilityLeaderboardView.CompletedSwapCandidate.builder()
                    .requestId(req.getId())
                    .assignmentId(sa.getId())
                    .replacementEmployeeId(targetEmp.getId())
                    .replacementEmployeeName(targetEmp.getFullName())
                    .originalEmployeeName(req.getEmployee().getFullName())
                    .shiftName(s.getShiftName())
                    .workDate(sa.getWorkDate())
                    .formattedDate(sa.getWorkDate().format(DAY_MONTH_FORMATTER))
                    .timeRange(s.getStartTime().format(TIME_FORMATTER) + " – " + s.getEndTime().format(TIME_FORMATTER))
                    .changeTypeLabel(typeLabel)
                    .alreadyRewarded(rewarded)
                    .build());
            if (list.size() >= 15) {
                break;
            }
        }
        return list;
    }

    private ReliabilityLeaderboardView.ScoreLogItem toScoreLogItem(ReliabilityScoreLog log) {
        ReliabilityActionType type = ReliabilityActionType.fromDbValue(log.getActionType());
        int delta = log.getPointsDelta() != null ? log.getPointsDelta() : 0;
        return ReliabilityLeaderboardView.ScoreLogItem.builder()
                .logId(log.getId())
                .employeeId(log.getEmployee() != null ? log.getEmployee().getId() : null)
                .employeeName(log.getEmployee() != null ? log.getEmployee().getFullName() : "—")
                .actionType(log.getActionType())
                .actionLabel(type.getLabel())
                .pointsDelta(delta)
                .scoreAfter(log.getScoreAfter() != null ? log.getScoreAfter() : INITIAL_SCORE)
                .reason(log.getReason())
                .createdAtFormatted(log.getCreatedAt() != null ? log.getCreatedAt().format(DATE_TIME_FORMATTER) : "—")
                .positive(delta > 0)
                .warningOnly(delta == 0)
                .build();
    }

    private void applyScoreDelta(
            Employee employee,
            ReliabilityActionType actionType,
            int pointsDelta,
            String reason,
            String referenceType,
            Integer referenceId,
            LocalDateTime now) {

        int currentScore = employee.getEffectiveReliabilityScore();
        int newScore = Math.max(0, currentScore + pointsDelta);
        employee.setReliabilityScore(newScore);
        employeeRepository.save(employee);

        Store store = employee.getStore();
        if (store == null) {
            return;
        }
        String safeReason = reason != null && reason.length() > 300 ? reason.substring(0, 300) : (reason != null ? reason : actionType.getLabel());
        ReliabilityScoreLog scoreLog = ReliabilityScoreLog.builder()
                .employee(employee)
                .store(store)
                .actionType(actionType.getDbValue())
                .pointsDelta(pointsDelta)
                .scoreAfter(newScore)
                .reason(safeReason)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdAt(now != null ? now : VietnamTime.now())
                .build();
        reliabilityScoreLogRepository.save(scoreLog);
    }

    public record TierMeta(String code, String label, String badgeClass) {}

    public static TierMeta resolveTier(int score) {
        if (score >= 115) {
            return new TierMeta("HERO", "Chiến thần cứu ca", "tier-hero");
        }
        if (score >= 105) {
            return new TierMeta("TRUSTED", "Đáng tin cậy", "tier-trusted");
        }
        if (score >= 90) {
            return new TierMeta("STANDARD", "Ổn định", "tier-standard");
        }
        return new TierMeta("WARNING", "Cần cải thiện", "tier-warning");
    }

    private Store resolveManagerStore(AuthenticatedUser user) {
        if (user == null || user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
    }

    private Employee resolveStaffEmployee(AuthenticatedUser user) {
        if (user == null || user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa liên kết với hồ sơ nhân viên.");
        }
        return employeeRepository.findByIdWithStore(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin hồ sơ nhân viên."));
    }
}
