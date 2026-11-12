package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.ManualAdjustCategory;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.ReliabilityActionType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.ReliabilityLeaderboardView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.MonthlyEmployeeAward;
import com.example.coffee_hrm.entity.ReliabilityScoreLog;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.entity.ShiftCoverInvitation;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.MonthlyEmployeeAwardRepository;
import com.example.coffee_hrm.repository.ReliabilityScoreLogRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ReliabilityService;
import lombok.RequiredArgsConstructor;
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
    private static final String REF_ATTENDANCE = "ATTENDANCE";
    private static final String REF_COVER_INVITATION = "COVER_INVITATION";
    /** Số tháng hiển thị trong mục "Lịch sử các tháng". */
    private static final int HISTORY_MONTHS = 6;

    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final ShiftChangeRequestRepository shiftChangeRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ReliabilityScoreLogRepository reliabilityScoreLogRepository;
    private final MonthlyEmployeeAwardRepository monthlyEmployeeAwardRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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
    public int recordInvitationDecline(ShiftCoverInvitation invitation, boolean expired) {
        if (invitation == null || invitation.getEmployee() == null || invitation.getId() == null) {
            return 0;
        }
        Employee employee = invitation.getEmployee();
        int prior = countUrgentDeclinesThisMonth(employee.getId());
        if (!invitation.isUrgent()) {
            return prior;
        }
        if (reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                employee.getId(), ReliabilityActionType.EMERGENCY_DECLINE_WARNING.getDbValue(), REF_COVER_INVITATION, invitation.getId())
                || reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                employee.getId(), ReliabilityActionType.EMERGENCY_DECLINE_PENALTY.getDbValue(), REF_COVER_INVITATION, invitation.getId())) {
            return prior;
        }
        int nth = prior + 1;
        String verb = expired ? "Không phản hồi (hết hạn)" : "Từ chối";
        String shiftLabel = invitationShiftLabel(invitation);
        if (nth <= FREE_URGENT_DECLINES_PER_MONTH) {
            applyScoreDelta(employee, ReliabilityActionType.EMERGENCY_DECLINE_WARNING, 0,
                    verb + " lời mời hỗ trợ ca gấp " + shiftLabel + " — lần " + nth + "/" + FREE_URGENT_DECLINES_PER_MONTH
                            + " miễn trừ trong tháng",
                    REF_COVER_INVITATION, invitation.getId(), VietnamTime.now());
        } else {
            applyScoreDelta(employee, ReliabilityActionType.EMERGENCY_DECLINE_PENALTY, URGENT_DECLINE_PENALTY,
                    verb + " lời mời hỗ trợ ca gấp " + shiftLabel + " — lần thứ " + nth + " trong tháng",
                    REF_COVER_INVITATION, invitation.getId(), VietnamTime.now());
        }
        return nth;
    }

    @Override
    @Transactional(readOnly = true)
    public int countUrgentDeclinesThisMonth(Integer employeeId) {
        if (employeeId == null) {
            return 0;
        }
        YearMonth month = YearMonth.from(VietnamTime.now());
        return (int) reliabilityScoreLogRepository.countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
                employeeId,
                List.of(ReliabilityActionType.EMERGENCY_DECLINE_WARNING.getDbValue(),
                        ReliabilityActionType.EMERGENCY_DECLINE_PENALTY.getDbValue()),
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
    }

    @Override
    @Transactional(readOnly = true)
    public int countCompletedSwapsRewardedThisMonth(Integer employeeId) {
        if (employeeId == null) {
            return 0;
        }
        YearMonth month = YearMonth.from(VietnamTime.now());
        return (int) reliabilityScoreLogRepository.countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
                employeeId,
                List.of(ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue()),
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private String invitationShiftLabel(ShiftCoverInvitation invitation) {
        ShiftChangeRequest request = invitation.getRequest();
        if (request == null || request.getAssignment() == null || request.getAssignment().getShift() == null) {
            return "";
        }
        ShiftAssignment a = request.getAssignment();
        return "(" + a.getShift().getShiftName() + " ngày " + a.getWorkDate().format(DATE_FORMATTER) + ")";
    }

    @Override
    @Transactional
    public void revokeRescueOnAbsence(Attendance absent) {
        if (absent == null || absent.getEmployee() == null || absent.getShift() == null || absent.getWorkDate() == null) {
            return;
        }
        Employee employee = absent.getEmployee();
        List<ShiftAssignment> sameDay = shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                employee.getId(), absent.getWorkDate(), AssignmentStatus.ASSIGNED);
        if (sameDay == null) {
            return;
        }
        for (ShiftAssignment assignment : sameDay) {
            if (assignment.getShift() == null || !Objects.equals(assignment.getShift().getId(), absent.getShift().getId())) {
                continue;
            }
            List<ShiftChangeRequest> changes = shiftChangeRequestRepository.findAllByAssignmentId(assignment.getId());
            if (changes == null) {
                continue;
            }
            for (ShiftChangeRequest change : changes) {
                if (change.getStatus() != ApprovalStatus.APPROVED || change.getId() == null) {
                    continue;
                }
                boolean rewarded = reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                        employee.getId(), ReliabilityActionType.RESCUE_SHIFT_24H.getDbValue(), REF_SHIFT_CHANGE, change.getId());
                boolean alreadyRevoked = reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                        employee.getId(), ReliabilityActionType.RESCUE_REVOKED.getDbValue(), REF_SHIFT_CHANGE, change.getId());
                if (!rewarded || alreadyRevoked) {
                    continue;
                }
                String label = assignment.getShift().getShiftName() + " ngày " + absent.getWorkDate().format(DATE_FORMATTER);
                applyScoreDelta(employee, ReliabilityActionType.RESCUE_REVOKED, -RESCUE_SHIFT_BONUS,
                        "Thu hồi +5đ hỗ trợ ca gấp do vắng mặt: " + label,
                        REF_SHIFT_CHANGE, change.getId(), VietnamTime.now());
                applyScoreDelta(employee, ReliabilityActionType.ABANDONED_ACCEPTED_SHIFT, ABANDONED_ACCEPTED_SHIFT_PENALTY,
                        "Đã nhận hỗ trợ ca gấp nhưng vắng mặt: " + label,
                        REF_SHIFT_CHANGE, change.getId(), VietnamTime.now());
            }
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

        // Tính "gấp" tại thời điểm nhân viên đồng ý nhận ca (chưa có thì lấy thời điểm duyệt).
        LocalDateTime agreedAt = approvedRequest.getTargetAgreedAt() != null ? approvedRequest.getTargetAgreedAt() : now;
        boolean rescuedWithin24h = isWithin24Hours(workDate, startTime, agreedAt);

        if (!rescuedWithin24h) {
            return;
        }
        if (approvedRequest.getId() != null
                && reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                        rescuer.getId(), ReliabilityActionType.RESCUE_SHIFT_24H.getDbValue(),
                        REF_SHIFT_CHANGE, approvedRequest.getId())) {
            return;
        }
        String reason = "Hỗ trợ ca gấp (<24h) hỗ trợ " + approvedRequest.getEmployee().getFullName()
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

        // Chặn cày điểm bằng cách đổi ca liên tục: Giới hạn tối đa 3 lần/tháng (+6đ)
        YearMonth targetMonth = attendance.getWorkDate() != null
                ? YearMonth.from(attendance.getWorkDate())
                : YearMonth.from(VietnamTime.now());
        long rewardedThisMonth = reliabilityScoreLogRepository.countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
                employee.getId(),
                List.of(ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue()),
                targetMonth.atDay(1).atStartOfDay(),
                targetMonth.plusMonths(1).atDay(1).atStartOfDay());
        if (rewardedThisMonth >= MAX_COMPLETED_SWAP_BONUS_PER_MONTH) {
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
    public void recordPunctualityPenaltyOnCheckout(Attendance attendance) {
        if (attendance == null || attendance.getId() == null || attendance.getEmployee() == null
                || attendance.getShift() == null || attendance.getWorkDate() == null) {
            return;
        }
        int lateMinutes = attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        int earlyMinutes = attendance.getEarlyLeaveMinutes() == null ? 0 : attendance.getEarlyLeaveMinutes();
        String shiftLabel = attendance.getShift().getShiftName() + " ngày " + attendance.getWorkDate().format(DATE_FORMATTER);
        Employee employee = attendance.getEmployee();

        if (lateMinutes > 0) {
            applyPunctualityPenalty(employee, ReliabilityActionType.LATE_ARRIVAL, lateMinutes,
                    "Đi muộn " + lateMinutes + " phút: " + shiftLabel, attendance.getId());
        }
        if (earlyMinutes >= EARLY_LEAVE_GRACE_MINUTES) {
            applyPunctualityPenalty(employee, ReliabilityActionType.EARLY_LEAVE, earlyMinutes,
                    "Về sớm " + earlyMinutes + " phút: " + shiftLabel, attendance.getId());
        }
    }

    private void applyPunctualityPenalty(Employee employee, ReliabilityActionType type, int minutes,
                                         String reason, Integer attendanceId) {
        if (reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                employee.getId(), type.getDbValue(), REF_ATTENDANCE, attendanceId)) {
            return;
        }
        applyScoreDelta(employee, type, punctualityPenalty(minutes), reason, REF_ATTENDANCE, attendanceId,
                VietnamTime.now());
    }

    /** ≤30 phút: -2đ, 31–60 phút: -4đ, trên 60 phút: -6đ. */
    static int punctualityPenalty(int minutes) {
        if (minutes <= 0) {
            return 0;
        }
        if (minutes <= 30) {
            return PUNCTUALITY_MINOR_PENALTY;
        }
        if (minutes <= 60) {
            return PUNCTUALITY_MODERATE_PENALTY;
        }
        return PUNCTUALITY_SEVERE_PENALTY;
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
        YearMonth targetMonth = assignment != null && assignment.getWorkDate() != null
                ? YearMonth.from(assignment.getWorkDate())
                : YearMonth.from(VietnamTime.now());
        long rewardedThisMonth = reliabilityScoreLogRepository.countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
                targetEmp.getId(),
                List.of(ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue()),
                targetMonth.atDay(1).atStartOfDay(),
                targetMonth.plusMonths(1).atDay(1).atStartOfDay());
        if (rewardedThisMonth >= MAX_COMPLETED_SWAP_BONUS_PER_MONTH) {
            throw new BusinessException("Nhân viên " + targetEmp.getFullName()
                    + " đã đạt giới hạn tối đa " + MAX_COMPLETED_SWAP_BONUS_PER_MONTH
                    + " lần thưởng hoàn thành ca đổi (+6đ) trong tháng "
                    + targetMonth.getMonthValue() + "/" + targetMonth.getYear() + ".");
        }
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
    public void adjustScoreByManager(Integer employeeId, String categoryParam, String reason, AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        if (employeeId == null) {
            throw new BusinessException("Vui lòng chọn nhân viên cần điều chỉnh điểm chuyên cần.");
        }
        ManualAdjustCategory category = ManualAdjustCategory.fromParam(categoryParam);
        if (category == null) {
            throw new BusinessException("Vui lòng chọn danh mục khen thưởng / nhắc nhở.");
        }
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new BusinessException("Vui lòng nhập lý do cụ thể cho lần điều chỉnh điểm.");
        }
        Employee employee = employeeRepository.findByIdWithStore(employeeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        if (employee.getStore() == null || !Objects.equals(employee.getStore().getId(), store.getId())) {
            throw new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý.");
        }
        ReliabilityActionType actionType = category.getActionType();
        LocalDateTime now = VietnamTime.now();
        if (category.isManualQuotaCategory()) {
            YearMonth month = YearMonth.from(now);
            Integer usedRaw = reliabilityScoreLogRepository.sumDeltaForEmployeeAndActionInRange(
                    employee.getId(), actionType.getDbValue(), month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
            int used = Math.abs(usedRaw != null ? usedRaw : 0);
            if (used + Math.abs(category.getPoints()) > MANUAL_ADJUST_MONTHLY_CAP) {
                throw new BusinessException("Vượt hạn mức điều chỉnh thủ công trong tháng: " + employee.getFullName()
                        + " đã được " + (category.isBonus() ? "cộng " : "trừ ") + used + "/" + MANUAL_ADJUST_MONTHLY_CAP
                        + " điểm, không thể " + (category.isBonus() ? "cộng" : "trừ") + " thêm " + Math.abs(category.getPoints()) + " điểm.");
            }
        }
        String fullReason = "[" + category.getLabel() + "] " + trimmedReason;
        applyScoreDelta(employee, actionType, category.getPoints(), fullReason, REF_MANAGER_ADJUST, null, now);
    }

    @Override
    @Transactional
    public void closeMonth(AuthenticatedUser manager, YearMonth month, Integer firstPlaceEmployeeId, String overrideReason) {
        Store store = resolveManagerStore(manager);
        YearMonth currentMonth = YearMonth.from(VietnamTime.today());
        if (month == null || !month.isBefore(currentMonth)) {
            throw new BusinessException("Chỉ có thể chốt danh hiệu cho tháng đã kết thúc.");
        }
        if (monthlyEmployeeAwardRepository.existsByStore_IdAndAwardMonth(store.getId(), month.toString())) {
            throw new BusinessException("Tháng " + monthLabel(month) + " đã được chốt danh hiệu trước đó.");
        }
        ReliabilityLeaderboardView view = buildLeaderboardView(store, month, null, true);
        List<ReliabilityLeaderboardView.RankEntry> proposals = view.getAwardProposals();
        Map<Integer, ReliabilityLeaderboardView.RankEntry> byId = new HashMap<>();
        view.getLeaderboardEntries().forEach(e -> byId.put(e.getEmployeeId(), e));

        ReliabilityLeaderboardView.RankEntry first;
        String reasonForOverride = overrideReason == null ? "" : overrideReason.trim();
        Integer proposedFirstId = proposals.isEmpty() ? null : proposals.getFirst().getEmployeeId();
        if (firstPlaceEmployeeId == null || Objects.equals(firstPlaceEmployeeId, proposedFirstId)) {
            if (proposals.isEmpty()) {
                throw new BusinessException("Tháng " + monthLabel(month)
                        + " không có nhân viên đủ điều kiện. Nếu vẫn muốn trao danh hiệu, hãy chọn nhân viên và ghi lý do.");
            }
            first = proposals.getFirst();
            reasonForOverride = "";
        } else {
            first = byId.get(firstPlaceEmployeeId);
            if (first == null) {
                throw new BusinessException("Nhân viên được chọn không thuộc bảng xếp hạng cửa hàng tháng này.");
            }
            if (reasonForOverride.isEmpty()) {
                throw new BusinessException("Bạn đang chọn người khác đề xuất của hệ thống — vui lòng ghi lý do.");
            }
            if (reasonForOverride.length() > 255) {
                reasonForOverride = reasonForOverride.substring(0, 255);
            }
        }

        List<ReliabilityLeaderboardView.RankEntry> winners = new ArrayList<>();
        winners.add(first);
        for (ReliabilityLeaderboardView.RankEntry p : proposals) {
            if (winners.size() >= 3) {
                break;
            }
            if (!Objects.equals(p.getEmployeeId(), first.getEmployeeId())) {
                winners.add(p);
            }
        }

        User decidedBy = userRepository.findById(manager.getUserId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản quản lý."));
        LocalDateTime now = VietnamTime.now();
        for (int i = 0; i < winners.size(); i++) {
            ReliabilityLeaderboardView.RankEntry w = winners.get(i);
            Employee employee = employeeRepository.findById(w.getEmployeeId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
            monthlyEmployeeAwardRepository.save(MonthlyEmployeeAward.builder()
                    .store(store)
                    .awardMonth(month.toString())
                    .employee(employee)
                    .awardRank(i + 1)
                    .monthScore(w.getReliabilityScore())
                    .overrideReason(i == 0 && !reasonForOverride.isEmpty() ? reasonForOverride : null)
                    .decidedBy(decidedBy)
                    .decidedAt(now)
                    .build());
            notifyAward(employee, i + 1, month);
        }
    }

    private void notifyAward(Employee employee, int rank, YearMonth month) {
        if (notificationService == null || employee == null) {
            return;
        }
        userRepository.findByEmployee_Id(employee.getId())
                .filter(u -> u.getId() != null && Boolean.TRUE.equals(u.getIsActive()))
                .ifPresent(u -> notificationService.notifyUsers(List.of(u),
                        rank == 1 ? "Chúc mừng Nhân viên xuất sắc tháng!" : "Chúc mừng bạn vào Top tháng!",
                        "Bạn đạt " + awardTitle(rank) + " " + monthLabel(month) + " tại cửa hàng.",
                        NotificationType.MONTHLY_AWARD_GRANTED,
                        null,
                        null));
    }

    static String awardTitle(int rank) {
        return rank == 1 ? "Nhân viên xuất sắc tháng" : "Top " + rank + " tháng";
    }

    private static String monthLabel(YearMonth m) {
        return String.format("%02d/%d", m.getMonthValue(), m.getYear());
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

        YearMonth currentMonth = YearMonth.from(VietnamTime.today());
        YearMonth targetMonth = requestedMonth != null ? requestedMonth : currentMonth;
        if (targetMonth.isAfter(currentMonth)) {
            targetMonth = currentMonth;
        }
        LocalDateTime monthStart = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEndExclusive = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
        YearMonth historyStartMonth = targetMonth.minusMonths(HISTORY_MONTHS - 1);

        List<Employee> activeEmployees = employeeRepository.findByStore_IdAndStatus(store.getId(), EmployeeStatus.ACTIVE);
        Integer managerEmpId = store.getManager() != null ? store.getManager().getId() : null;
        List<Employee> staffMembers = activeEmployees.stream()
                .filter(e -> managerEmpId == null || !Objects.equals(e.getId(), managerEmpId))
                .toList();
        if (staffMembers.isEmpty()) {
            staffMembers = activeEmployees;
        }

        // Điểm chuyên cần tính riêng từng tháng: mỗi tháng khởi đầu 100đ + tổng biến động trong tháng đó.
        List<ReliabilityScoreLog> historyLogs = reliabilityScoreLogRepository.findForStoreInRange(
                store.getId(), historyStartMonth.atDay(1).atStartOfDay(), monthEndExclusive);
        List<ReliabilityScoreLog> monthlyLogs = historyLogs.stream()
                .filter(l -> l.getCreatedAt() != null
                        && !l.getCreatedAt().isBefore(monthStart)
                        && l.getCreatedAt().isBefore(monthEndExclusive))
                .toList();

        Map<Integer, List<ReliabilityScoreLog>> logsByEmployee = new HashMap<>();
        for (ReliabilityScoreLog log : monthlyLogs) {
            if (log.getEmployee() != null && log.getEmployee().getId() != null) {
                logsByEmployee.computeIfAbsent(log.getEmployee().getId(), k -> new ArrayList<>()).add(log);
            }
        }

        int totalRescueMonth = 0;
        int totalCompletedSwapsMonth = 0;
        int totalLateCancelsMonth = 0;

        // Số ca đã làm (đã check-out) và số lần vắng trong tháng, dùng để xét danh hiệu.
        Map<Integer, Integer> shiftsWorkedByEmp = new HashMap<>();
        Map<Integer, Integer> absencesByEmp = new HashMap<>();
        if (attendanceRepository != null) {
            List<Attendance> monthAttendances = attendanceRepository.findForMonthlyReport(
                    store.getId(), targetMonth.atDay(1), targetMonth.atEndOfMonth());
            if (monthAttendances != null) {
                for (Attendance a : monthAttendances) {
                    if (a.getEmployee() == null || a.getShift() == null) {
                        continue;
                    }
                    if (a.getStatus() == AttendanceStatus.ABSENT) {
                        absencesByEmp.merge(a.getEmployee().getId(), 1, Integer::sum);
                    } else if (a.getCheckOutTime() != null && a.getStatus() != AttendanceStatus.NOT_PARTICIPATED) {
                        shiftsWorkedByEmp.merge(a.getEmployee().getId(), 1, Integer::sum);
                    }
                }
            }
        }

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
            int punctualityCount = 0;
            int abandonedCount = 0;
            int manualBonusUsed = 0;
            int manualPenaltyUsed = 0;

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
                } else if (ReliabilityActionType.LATE_ARRIVAL.getDbValue().equalsIgnoreCase(action)
                        || ReliabilityActionType.EARLY_LEAVE.getDbValue().equalsIgnoreCase(action)) {
                    punctualityCount++;
                } else if (ReliabilityActionType.ABANDONED_ACCEPTED_SHIFT.getDbValue().equalsIgnoreCase(action)) {
                    abandonedCount++;
                } else if (ReliabilityActionType.MANAGER_BONUS.getDbValue().equalsIgnoreCase(action)) {
                    manualBonusUsed += Math.max(0, delta);
                } else if (ReliabilityActionType.MANAGER_PENALTY.getDbValue().equalsIgnoreCase(action)) {
                    manualPenaltyUsed += Math.abs(Math.min(0, delta));
                }
            }

            int score = Math.max(0, INITIAL_SCORE + netDelta);
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
                    .punctualityViolationsCount(punctualityCount)
                    .shiftsWorked(shiftsWorkedByEmp.getOrDefault(emp.getId(), 0))
                    .absencesCount(absencesByEmp.getOrDefault(emp.getId(), 0))
                    .abandonedCount(abandonedCount)
                    .manualBonusUsed(manualBonusUsed)
                    .manualPenaltyUsed(manualPenaltyUsed)
                    .tierCode(tier.code())
                    .tierLabel(tier.label())
                    .tierBadgeClass(tier.badgeClass())
                    .build());
        }

        entries.sort(Comparator
                .comparingInt(ReliabilityLeaderboardView.RankEntry::getReliabilityScore).reversed()
                .thenComparing(Comparator.comparingInt(ReliabilityLeaderboardView.RankEntry::getRescueShiftsCount).reversed())
                .thenComparingInt(ReliabilityLeaderboardView.RankEntry::getPunctualityViolationsCount)
                .thenComparing(Comparator.comparingInt(ReliabilityLeaderboardView.RankEntry::getShiftsWorked).reversed())
                .thenComparing(ReliabilityLeaderboardView.RankEntry::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        List<ReliabilityLeaderboardView.RankEntry> awardProposals = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ReliabilityLeaderboardView.RankEntry entry = entries.get(i);
            entry.setRank(i + 1);
            String ineligible = ineligibleReason(entry);
            entry.setAwardEligible(ineligible == null);
            entry.setIneligibleReason(ineligible);
            if (ineligible == null && awardProposals.size() < 3) {
                awardProposals.add(entry);
            }
        }
        for (ReliabilityLeaderboardView.RankEntry entry : entries) {
            int proposedRank = awardProposals.indexOf(entry) + 1;
            boolean top3Qualifies = proposedRank > 0;
            boolean prioritySchedule = top3Qualifies || entry.getReliabilityScore() >= MIN_SCORE_FOR_AWARD;
            entry.setKpiRewardEligible(top3Qualifies);
            entry.setPriorityScheduleEligible(prioritySchedule);
            if (proposedRank == 1) {
                entry.setRewardBadgeLabel("Đề xuất Nhân viên xuất sắc tháng + Ưu tiên lịch tuần sau");
            } else if (top3Qualifies) {
                entry.setRewardBadgeLabel("Đề xuất Top tháng + Ưu tiên lịch tuần sau");
            } else if (entry.getIneligibleReason() != null && entry.getRank() <= 3) {
                entry.setRewardBadgeLabel("Chưa đủ điều kiện xét: " + entry.getIneligibleReason());
            } else if (prioritySchedule) {
                entry.setRewardBadgeLabel("Ưu tiên chọn lịch làm tuần sau");
            } else if (entry.getReliabilityScore() < 90) {
                entry.setRewardBadgeLabel("Cần cải thiện kỷ luật nhận/đổi ca");
            } else {
                entry.setRewardBadgeLabel("Duy trì điểm chuyên cần tốt");
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
                ? buildCompletedSwapCandidates(store.getId(), targetMonth)
                : List.of();

        // "Điểm sau" của từng biến động = 100đ + lũy kế trong tháng (không dùng giá trị cộng dồn cũ trong DB).
        Map<ReliabilityScoreLog, Integer> monthlyScoreAfter = new IdentityHashMap<>();
        Map<Integer, Integer> runningByEmployee = new HashMap<>();
        for (int i = monthlyLogs.size() - 1; i >= 0; i--) {
            ReliabilityScoreLog log = monthlyLogs.get(i);
            if (log.getEmployee() == null) {
                continue;
            }
            int delta = log.getPointsDelta() != null ? log.getPointsDelta() : 0;
            int running = runningByEmployee.merge(log.getEmployee().getId(), delta, Integer::sum);
            monthlyScoreAfter.put(log, Math.max(0, INITIAL_SCORE + running));
        }

        List<ReliabilityLeaderboardView.ScoreLogItem> recentLogs = monthlyLogs.stream()
                .limit(50)
                .map(l -> toScoreLogItem(l, monthlyScoreAfter.get(l)))
                .toList();

        ReliabilityLeaderboardView.RankEntry mySummary = null;
        String nextTierLabel = "Xuất sắc (115+ đ)";
        int pointsToNextTier = 0;
        int progressPercent = 100;
        List<ReliabilityLeaderboardView.ScoreLogItem> myRecentLogs = List.of();

        if (currentStaff != null) {
            mySummary = entries.stream()
                    .filter(e -> Objects.equals(e.getEmployeeId(), currentStaff.getId()))
                    .findFirst()
                    .orElse(null);
            int myScore = mySummary != null ? mySummary.getReliabilityScore() : INITIAL_SCORE;
            if (myScore < 90) {
                nextTierLabel = "Ổn định (90 đ)";
                pointsToNextTier = 90 - myScore;
                progressPercent = Math.max(10, (myScore * 100) / 90);
            } else if (myScore < 105) {
                nextTierLabel = "Đáng tin cậy — Ưu tiên lịch (105 đ)";
                pointsToNextTier = 105 - myScore;
                progressPercent = Math.min(95, Math.max(15, ((myScore - 90) * 100) / 15));
            } else if (myScore < 115) {
                nextTierLabel = "Xuất sắc (115 đ)";
                pointsToNextTier = 115 - myScore;
                progressPercent = Math.min(95, Math.max(25, ((myScore - 105) * 100) / 10));
            } else {
                nextTierLabel = "Đã đạt cấp cao nhất: Xuất sắc";
                pointsToNextTier = 0;
                progressPercent = 100;
            }
            myRecentLogs = monthlyLogs.stream()
                    .filter(l -> l.getEmployee() != null && Objects.equals(l.getEmployee().getId(), currentStaff.getId()))
                    .map(l -> toScoreLogItem(l, monthlyScoreAfter.get(l)))
                    .toList();
        }

        List<ReliabilityLeaderboardView.MonthHistoryItem> monthlyHistory = buildMonthlyHistory(
                historyLogs, staffMembers, historyStartMonth, targetMonth, currentStaff);

        // Danh hiệu đã chốt (tháng đang xem + các tháng trong lịch sử)
        List<String> monthKeys = new ArrayList<>();
        for (YearMonth m = targetMonth; !m.isBefore(historyStartMonth); m = m.minusMonths(1)) {
            monthKeys.add(m.toString());
        }
        List<MonthlyEmployeeAward> storedAwards = monthlyEmployeeAwardRepository != null
                ? monthlyEmployeeAwardRepository.findForStoreAndMonths(store.getId(), monthKeys)
                : List.of();
        Map<String, String> winnerByMonth = new HashMap<>();
        List<ReliabilityLeaderboardView.AwardItem> awards = new ArrayList<>();
        for (MonthlyEmployeeAward a : storedAwards) {
            if (a.getAwardRank() != null && a.getAwardRank() == 1 && a.getEmployee() != null) {
                winnerByMonth.put(a.getAwardMonth(), a.getEmployee().getFullName());
            }
            if (targetMonth.toString().equals(a.getAwardMonth()) && a.getEmployee() != null) {
                awards.add(ReliabilityLeaderboardView.AwardItem.builder()
                        .rank(a.getAwardRank())
                        .title(awardTitle(a.getAwardRank()))
                        .employeeId(a.getEmployee().getId())
                        .employeeName(a.getEmployee().getFullName())
                        .monthScore(a.getMonthScore())
                        .overrideReason(a.getOverrideReason())
                        .decidedAt(a.getDecidedAt() != null ? a.getDecidedAt().format(DATE_TIME_FORMATTER) : null)
                        .build());
                entries.stream()
                        .filter(e -> Objects.equals(e.getEmployeeId(), a.getEmployee().getId()))
                        .forEach(e -> {
                            e.setAwardRank(a.getAwardRank());
                            e.setAwardTitle(awardTitle(a.getAwardRank()));
                        });
            }
        }
        awards.sort(Comparator.comparingInt(ReliabilityLeaderboardView.AwardItem::getRank));
        monthlyHistory.forEach(h -> h.setAwardWinnerName(winnerByMonth.get(h.getMonthParam())));
        boolean monthClosed = !awards.isEmpty();

        return ReliabilityLeaderboardView.builder()
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .storeAddress(store.getAddress())
                .selectedMonth(targetMonth.toString())
                .periodLabel("Tháng " + String.format("%02d/%d", targetMonth.getMonthValue(), targetMonth.getYear()))
                .prevMonthParam(targetMonth.minusMonths(1).toString())
                .nextMonthParam(targetMonth.isBefore(currentMonth) ? targetMonth.plusMonths(1).toString() : null)
                .currentMonth(targetMonth.equals(currentMonth))
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
                .monthlyHistory(monthlyHistory)
                .monthClosed(monthClosed)
                .canCloseMonth(isManagerView && !monthClosed && targetMonth.isBefore(currentMonth))
                .awardProposals(awardProposals)
                .awards(awards)
                .minShiftsForAward(MIN_SHIFTS_FOR_AWARD)
                .minScoreForAward(MIN_SCORE_FOR_AWARD)
                .manualAdjustCap(MANUAL_ADJUST_MONTHLY_CAP)
                .build();
    }

    /** Lý do chưa đủ điều kiện xét danh hiệu tháng, null nếu đủ điều kiện. */
    static String ineligibleReason(ReliabilityLeaderboardView.RankEntry e) {
        List<String> reasons = new ArrayList<>();
        if (e.getShiftsWorked() < MIN_SHIFTS_FOR_AWARD) {
            reasons.add("mới làm " + e.getShiftsWorked() + "/" + MIN_SHIFTS_FOR_AWARD + " ca");
        }
        if (e.getAbsencesCount() > 0) {
            reasons.add("vắng " + e.getAbsencesCount() + " ca");
        }
        if (e.getAbandonedCount() > 0) {
            reasons.add("bỏ ca đã nhận");
        }
        if (e.getReliabilityScore() < MIN_SCORE_FOR_AWARD) {
            reasons.add("dưới " + MIN_SCORE_FOR_AWARD + "đ");
        }
        return reasons.isEmpty() ? null : String.join(", ", reasons);
    }

    /**
     * Tóm tắt điểm chuyên cần các tháng gần nhất (mới nhất trước): người dẫn đầu từng tháng
     * và điểm/hạng của nhân viên đang xem.
     */
    private List<ReliabilityLeaderboardView.MonthHistoryItem> buildMonthlyHistory(
            List<ReliabilityScoreLog> historyLogs,
            List<Employee> staffMembers,
            YearMonth fromMonth,
            YearMonth toMonth,
            Employee currentStaff) {

        Map<YearMonth, Map<Integer, Integer>> deltaByMonth = new HashMap<>();
        for (ReliabilityScoreLog log : historyLogs) {
            if (log.getEmployee() == null || log.getCreatedAt() == null) {
                continue;
            }
            int delta = log.getPointsDelta() != null ? log.getPointsDelta() : 0;
            deltaByMonth.computeIfAbsent(YearMonth.from(log.getCreatedAt()), k -> new HashMap<>())
                    .merge(log.getEmployee().getId(), delta, Integer::sum);
        }

        List<ReliabilityLeaderboardView.MonthHistoryItem> items = new ArrayList<>();
        for (YearMonth m = toMonth; !m.isBefore(fromMonth); m = m.minusMonths(1)) {
            Map<Integer, Integer> deltas = deltaByMonth.getOrDefault(m, Map.of());
            List<Employee> ranked = staffMembers.stream()
                    .sorted(Comparator
                            .comparingInt((Employee e) -> deltas.getOrDefault(e.getId(), 0)).reversed()
                            .thenComparing(Employee::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
            Employee top = deltas.isEmpty() || ranked.isEmpty() ? null : ranked.get(0);

            Integer myScore = null;
            Integer myRank = null;
            if (currentStaff != null) {
                myScore = Math.max(0, INITIAL_SCORE + deltas.getOrDefault(currentStaff.getId(), 0));
                for (int i = 0; i < ranked.size(); i++) {
                    if (Objects.equals(ranked.get(i).getId(), currentStaff.getId())) {
                        myRank = i + 1;
                        break;
                    }
                }
            }

            items.add(ReliabilityLeaderboardView.MonthHistoryItem.builder()
                    .monthParam(m.toString())
                    .label(String.format("Tháng %02d/%d", m.getMonthValue(), m.getYear()))
                    .selected(m.equals(toMonth))
                    .hasActivity(!deltas.isEmpty())
                    .topEmployeeName(top != null ? top.getFullName() : null)
                    .topScore(top != null ? Math.max(0, INITIAL_SCORE + deltas.getOrDefault(top.getId(), 0)) : null)
                    .myScore(myScore)
                    .myRank(myRank)
                    .totalStaff(ranked.size())
                    .build());
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, Integer> getCurrentMonthScores(Integer storeId) {
        Map<Integer, Integer> scores = new HashMap<>();
        if (storeId == null) {
            return scores;
        }
        YearMonth month = YearMonth.from(VietnamTime.today());
        List<ReliabilityScoreLog> logs = reliabilityScoreLogRepository.findForStoreInRange(
                storeId, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
        for (ReliabilityScoreLog log : logs) {
            if (log.getEmployee() == null) {
                continue;
            }
            int delta = log.getPointsDelta() != null ? log.getPointsDelta() : 0;
            scores.merge(log.getEmployee().getId(), delta, Integer::sum);
        }
        scores.replaceAll((id, delta) -> Math.max(0, INITIAL_SCORE + delta));
        return scores;
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

    private List<ReliabilityLeaderboardView.CompletedSwapCandidate> buildCompletedSwapCandidates(Integer storeId, YearMonth targetMonth) {
        List<ShiftChangeRequest> allRequests = shiftChangeRequestRepository.findAllForStore(storeId);
        if (allRequests == null || allRequests.isEmpty()) {
            return List.of();
        }
        YearMonth selectedMonth = targetMonth != null ? targetMonth : YearMonth.from(VietnamTime.now());
        LocalDateTime startOfMonth = selectedMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = selectedMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<ReliabilityLeaderboardView.CompletedSwapCandidate> list = new ArrayList<>();
        for (ShiftChangeRequest req : allRequests) {
            if (req.getStatus() != ApprovalStatus.APPROVED || req.getTargetEmployee() == null
                    || req.getAssignment() == null || req.getAssignment().getShift() == null
                    || req.getAssignment().getWorkDate() == null) {
                continue;
            }
            LocalDate workDate = req.getAssignment().getWorkDate();
            if (!YearMonth.from(workDate).equals(selectedMonth)) {
                continue;
            }
            Employee targetEmp = req.getTargetEmployee();
            boolean rewarded = reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                    targetEmp.getId(), ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue(),
                    REF_SHIFT_CHANGE, req.getId());

            long rewardedCount = reliabilityScoreLogRepository.countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
                    targetEmp.getId(),
                    List.of(ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue()),
                    startOfMonth,
                    endOfMonth);
            boolean monthlyCapped = rewardedCount >= MAX_COMPLETED_SWAP_BONUS_PER_MONTH;

            ShiftAssignment sa = req.getAssignment();
            Shift s = sa.getShift();
            String typeLabel = req.getTargetAssignment() != null ? "Đổi chéo ca" : "Nhận ca thay / Hỗ trợ ca gấp";

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
                    .monthlyCapped(monthlyCapped)
                    .rewardsCountThisMonth((int) rewardedCount)
                    .build());
            if (list.size() >= 15) {
                break;
            }
        }
        return list;
    }

    private ReliabilityLeaderboardView.ScoreLogItem toScoreLogItem(ReliabilityScoreLog log, Integer monthlyScoreAfter) {
        ReliabilityActionType type = ReliabilityActionType.fromDbValue(log.getActionType());
        int delta = log.getPointsDelta() != null ? log.getPointsDelta() : 0;
        return ReliabilityLeaderboardView.ScoreLogItem.builder()
                .logId(log.getId())
                .employeeId(log.getEmployee() != null ? log.getEmployee().getId() : null)
                .employeeName(log.getEmployee() != null ? log.getEmployee().getFullName() : "—")
                .actionType(log.getActionType())
                .actionLabel(type.getLabel())
                .pointsDelta(delta)
                .scoreAfter(monthlyScoreAfter != null ? monthlyScoreAfter
                        : (log.getScoreAfter() != null ? log.getScoreAfter() : INITIAL_SCORE))
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

        // Điểm chuyên cần reset mỗi tháng: 100đ + tổng biến động của tháng hiện tại.
        LocalDateTime at = now != null ? now : VietnamTime.now();
        YearMonth month = YearMonth.from(at);
        Integer monthDelta = employee.getId() == null ? null : reliabilityScoreLogRepository.sumDeltaForEmployeeInRange(
                employee.getId(), month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
        int newScore = Math.max(0, INITIAL_SCORE + (monthDelta != null ? monthDelta : 0) + pointsDelta);
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
            return new TierMeta("HERO", "Xuất sắc", "tier-hero");
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
