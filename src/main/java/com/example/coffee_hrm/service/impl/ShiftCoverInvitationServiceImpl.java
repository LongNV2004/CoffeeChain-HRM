package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.DeclineReason;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.InvitationStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.CoverInvitationView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.entity.ShiftCoverInvitation;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.ShiftCoverInvitationRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ReliabilityService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.ShiftCoverInvitationService;
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShiftCoverInvitationServiceImpl implements ShiftCoverInvitationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SHIFT_CHANGE_REF = "SHIFT_CHANGE";

    private final ShiftCoverInvitationRepository invitationRepository;
    private final ShiftChangeRequestRepository shiftChangeRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ApprovedLeaveCoverage approvedLeaveCoverage;
    private final ReliabilityService reliabilityService;
    private final ScheduleService scheduleService;
    private final NotificationService notificationService;

    // ==========================================================
    // QUẢN LÝ
    // ==========================================================

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, CoverInvitationView.ManagerPanel> getManagerPanels(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        List<ShiftChangeRequest> openRequests = shiftChangeRequestRepository.findOpenRescueRequestsForStore(store.getId());
        if (openRequests == null || openRequests.isEmpty()) {
            return Map.of();
        }
        List<ShiftChangeRequest> upcoming = openRequests.stream()
                .filter(r -> r.getAssignment() != null && r.getAssignment().getShift() != null)
                .filter(r -> !VietnamTime.isBeforeNow(r.getAssignment().getWorkDate(), r.getAssignment().getShift().getStartTime()))
                .toList();
        if (upcoming.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<ShiftCoverInvitation>> invitationsByRequest = new HashMap<>();
        for (ShiftCoverInvitation inv : invitationRepository.findForRequests(
                upcoming.stream().map(ShiftChangeRequest::getId).toList())) {
            invitationsByRequest.computeIfAbsent(inv.getRequest().getId(), k -> new ArrayList<>()).add(inv);
        }
        List<Employee> activeEmployees = employeeRepository.findByStore_IdAndStatus(store.getId(), EmployeeStatus.ACTIVE);
        Map<Integer, Integer> monthScores = reliabilityService.getCurrentMonthScores(store.getId());
        Integer managerEmpId = store.getManager() != null ? store.getManager().getId() : null;
        LocalDateTime now = VietnamTime.now();

        Map<Integer, CoverInvitationView.ManagerPanel> panels = new LinkedHashMap<>();
        for (ShiftChangeRequest request : upcoming) {
            List<ShiftCoverInvitation> invitations = invitationsByRequest.getOrDefault(request.getId(), List.of());
            Set<Integer> invitedIds = new HashSet<>();
            invitations.forEach(i -> invitedIds.add(i.getEmployee().getId()));

            List<CoverInvitationView.Candidate> candidates = activeEmployees.stream()
                    .filter(e -> !Objects.equals(e.getId(), managerEmpId))
                    .filter(e -> isEligible(e, request))
                    .map(e -> CoverInvitationView.Candidate.builder()
                            .employeeId(e.getId())
                            .fullName(e.getFullName())
                            .monthScore(monthScores.getOrDefault(e.getId(), ReliabilityService.INITIAL_SCORE))
                            .urgentDeclinesThisMonth(reliabilityService.countUrgentDeclinesThisMonth(e.getId()))
                            .alreadyInvited(invitedIds.contains(e.getId()))
                            .build())
                    .sorted(Comparator.comparing(CoverInvitationView.Candidate::isAlreadyInvited)
                            .thenComparing(Comparator.comparingInt(CoverInvitationView.Candidate::getMonthScore).reversed())
                            .thenComparing(CoverInvitationView.Candidate::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();

            List<CoverInvitationView.InvitationItem> items = invitations.stream()
                    .map(i -> toItem(i, now))
                    .toList();
            long pending = invitations.stream().filter(i -> i.getStatus() == InvitationStatus.PENDING).count();

            panels.put(request.getId(), CoverInvitationView.ManagerPanel.builder()
                    .requestId(request.getId())
                    .urgent(isUrgent(request, now))
                    .canInvite(candidates.stream().anyMatch(c -> !c.isAlreadyInvited()))
                    .invitations(items)
                    .candidates(candidates)
                    .pendingCount(pending)
                    .build());
        }
        return panels;
    }

    @Override
    @Transactional
    public int invite(Integer requestId, List<Integer> employeeIds, AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một nhân viên để gửi lời mời.");
        }
        ShiftChangeRequest request = shiftChangeRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn đổi ca."));
        if (request.getEmployee() == null || request.getEmployee().getStore() == null
                || !Objects.equals(request.getEmployee().getStore().getId(), store.getId())) {
            throw new BusinessException("Đơn đổi ca không thuộc cửa hàng bạn quản lý.");
        }
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Đơn đổi ca đã được xử lý, không thể gửi lời mời.");
        }
        if (request.getTargetEmployee() != null || request.getTargetAssignment() != null) {
            throw new BusinessException("Ca này đã có nhân viên nhận, không cần gửi lời mời.");
        }
        ShiftAssignment assignment = request.getAssignment();
        if (assignment == null || assignment.getShift() == null
                || VietnamTime.isBeforeNow(assignment.getWorkDate(), assignment.getShift().getStartTime())) {
            throw new BusinessException("Ca làm việc đã bắt đầu hoặc đã qua, không thể gửi lời mời.");
        }
        User invitedBy = userRepository.findById(manager.getUserId()).orElse(null);
        LocalDateTime now = VietnamTime.now();
        LocalDateTime shiftStart = LocalDateTime.of(assignment.getWorkDate(), assignment.getShift().getStartTime());
        LocalDateTime expiresAt = now.plusHours(RESPONSE_WINDOW_HOURS);
        if (expiresAt.isAfter(shiftStart)) {
            expiresAt = shiftStart;
        }
        boolean urgent = isUrgent(request, now);
        Integer managerEmpId = store.getManager() != null ? store.getManager().getId() : null;

        int sent = 0;
        for (Integer employeeId : new LinkedHashSet<>(employeeIds)) {
            if (employeeId == null || invitationRepository.existsByRequest_IdAndEmployee_Id(requestId, employeeId)) {
                continue;
            }
            Employee employee = employeeRepository.findByIdWithStore(employeeId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên được mời."));
            if (employee.getStore() == null || !Objects.equals(employee.getStore().getId(), store.getId())
                    || Objects.equals(employee.getId(), managerEmpId)) {
                throw new BusinessException("Nhân viên " + employee.getFullName() + " không thuộc cửa hàng của bạn.");
            }
            if (!isEligible(employee, request)) {
                throw new BusinessException("Nhân viên " + employee.getFullName()
                        + " không đủ điều kiện nhận ca này (trùng ca, đang nghỉ phép hoặc chưa đủ chứng chỉ).");
            }
            ShiftCoverInvitation invitation = invitationRepository.save(ShiftCoverInvitation.builder()
                    .request(request)
                    .employee(employee)
                    .invitedBy(invitedBy)
                    .status(InvitationStatus.PENDING)
                    .urgent(urgent)
                    .sentAt(now)
                    .expiresAt(expiresAt)
                    .build());
            notifyEmployee(employee.getId(),
                    urgent ? "Lời mời hỗ trợ ca gấp" : "Lời mời hỗ trợ ca",
                    "Quản lý mời bạn nhận " + shiftLabel(request) + " thay " + request.getEmployee().getFullName()
                            + ". Vui lòng phản hồi trước " + expiresAt.format(DATE_TIME_FORMATTER) + ".",
                    NotificationType.SHIFT_COVER_INVITED,
                    request.getId());
            if (invitation.getId() != null) {
                sent++;
            }
        }
        if (sent == 0) {
            throw new BusinessException("Các nhân viên được chọn đều đã được mời trước đó.");
        }
        return sent;
    }

    // ==========================================================
    // NHÂN VIÊN
    // ==========================================================

    @Override
    @Transactional(readOnly = true)
    public List<CoverInvitationView.StaffCard> getPendingForStaff(AuthenticatedUser staff) {
        if (staff == null || staff.getEmployeeId() == null) {
            return List.of();
        }
        LocalDateTime now = VietnamTime.now();
        int declinesUsed = reliabilityService.countUrgentDeclinesThisMonth(staff.getEmployeeId());
        return invitationRepository.findForEmployeeByStatus(staff.getEmployeeId(), InvitationStatus.PENDING).stream()
                .filter(i -> i.getExpiresAt() != null && i.getExpiresAt().isAfter(now))
                .map(i -> {
                    ShiftChangeRequest r = i.getRequest();
                    Shift s = r.getAssignment().getShift();
                    return CoverInvitationView.StaffCard.builder()
                            .invitationId(i.getId())
                            .requestId(r.getId())
                            .requesterName(r.getEmployee().getFullName())
                            .shiftName(s.getShiftName())
                            .timeRange(s.getStartTime().format(TIME_FORMATTER) + " – " + s.getEndTime().format(TIME_FORMATTER))
                            .workDate(r.getAssignment().getWorkDate())
                            .urgent(i.isUrgent())
                            .expiresAtLabel(i.getExpiresAt().format(DATE_TIME_FORMATTER))
                            .minutesLeft(Math.max(0, ChronoUnit.MINUTES.between(now, i.getExpiresAt())))
                            .declinesUsed(declinesUsed)
                            .freeDeclines(ReliabilityService.FREE_URGENT_DECLINES_PER_MONTH)
                            .nextDeclinePenalized(i.isUrgent() && declinesUsed >= ReliabilityService.FREE_URGENT_DECLINES_PER_MONTH)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public void accept(Integer invitationId, AuthenticatedUser staff) {
        ShiftCoverInvitation invitation = loadOwnPendingInvitation(invitationId, staff);
        // Tái sử dụng toàn bộ kiểm tra nghiệp vụ của "xung phong nhận ca" (trùng ca, nghỉ phép, chứng chỉ...).
        // Hàm này cũng chuyển lời mời của mình sang ACCEPTED và hủy lời mời của người khác.
        scheduleService.volunteerRescueShift(invitation.getRequest().getId(), staff);
        notifyManagerOfResponse(invitation, true);
    }

    @Override
    @Transactional
    public int decline(Integer invitationId, String reason, String note, AuthenticatedUser staff) {
        DeclineReason declineReason = DeclineReason.fromParam(reason);
        if (declineReason == null) {
            throw new BusinessException("Vui lòng chọn lý do từ chối.");
        }
        String trimmedNote = note == null ? null : note.trim();
        if (declineReason == DeclineReason.OTHER && (trimmedNote == null || trimmedNote.isEmpty())) {
            throw new BusinessException("Vui lòng ghi rõ lý do khi chọn \"Lý do khác\".");
        }
        if (trimmedNote != null && trimmedNote.length() > 255) {
            trimmedNote = trimmedNote.substring(0, 255);
        }
        ShiftCoverInvitation invitation = loadOwnPendingInvitation(invitationId, staff);
        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setDeclineReason(declineReason);
        invitation.setDeclineNote(trimmedNote == null || trimmedNote.isEmpty() ? null : trimmedNote);
        invitation.setRespondedAt(VietnamTime.now());
        invitationRepository.save(invitation);
        int count = reliabilityService.recordInvitationDecline(invitation, false);
        notifyManagerOfResponse(invitation, false);
        return invitation.isUrgent() ? count : 0;
    }

    @Override
    @Transactional
    public int expireOverdue() {
        List<ShiftCoverInvitation> overdue = invitationRepository.findOverduePending(VietnamTime.now());
        for (ShiftCoverInvitation invitation : overdue) {
            expire(invitation);
        }
        return overdue.size();
    }

    // ==========================================================
    // HỖ TRỢ
    // ==========================================================

    private ShiftCoverInvitation loadOwnPendingInvitation(Integer invitationId, AuthenticatedUser staff) {
        if (staff == null || staff.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa liên kết với hồ sơ nhân viên.");
        }
        ShiftCoverInvitation invitation = invitationRepository.findByIdWithDetails(invitationId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lời mời hỗ trợ ca."));
        if (invitation.getEmployee() == null || !Objects.equals(invitation.getEmployee().getId(), staff.getEmployeeId())) {
            throw new BusinessException("Lời mời này không dành cho bạn.");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException("Lời mời đã được xử lý trước đó (" + invitation.getStatus().getLabel().toLowerCase() + ").");
        }
        // Lưu ý: không đổi trạng thái lời mời ở các nhánh lỗi dưới đây vì exception sẽ rollback transaction.
        // Lời mời quá hạn do scheduler chuyển EXPIRED; lời mời của ca đã có người nhận được hủy khi đơn được nhận/duyệt.
        if (invitation.getRequest() == null || invitation.getRequest().getStatus() != ApprovalStatus.PENDING
                || invitation.getRequest().getTargetEmployee() != null) {
            throw new BusinessException("Ca này đã có người nhận hoặc đơn đã được xử lý.");
        }
        if (!invitation.getExpiresAt().isAfter(VietnamTime.now())) {
            throw new BusinessException("Lời mời đã hết hạn phản hồi.");
        }
        return invitation;
    }

    private void expire(ShiftCoverInvitation invitation) {
        invitation.setStatus(InvitationStatus.EXPIRED);
        invitation.setRespondedAt(VietnamTime.now());
        invitationRepository.save(invitation);
        reliabilityService.recordInvitationDecline(invitation, true);
    }

    /**
     * Đủ điều kiện nhận ca: đang làm việc, đã có chứng chỉ, không phải người xin đổi,
     * không có đơn nghỉ phép đã duyệt cho ca đó và không trùng giờ với ca khác trong ngày.
     */
    boolean isEligible(Employee employee, ShiftChangeRequest request) {
        if (employee == null || employee.getId() == null || request == null || request.getAssignment() == null) {
            return false;
        }
        ShiftAssignment assignment = request.getAssignment();
        Shift shift = assignment.getShift();
        if (shift == null || employee.getStatus() != EmployeeStatus.ACTIVE
                || employee.getCertificationStatus() != CertificationStatus.CERTIFIED) {
            return false;
        }
        if (request.getEmployee() != null && Objects.equals(request.getEmployee().getId(), employee.getId())) {
            return false;
        }
        if (approvedLeaveCoverage.blocksShift(employee.getId(), shift.getId(), assignment.getWorkDate())) {
            return false;
        }
        List<ShiftAssignment> sameDay = shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                employee.getId(), assignment.getWorkDate(), AssignmentStatus.ASSIGNED);
        if (sameDay != null) {
            for (ShiftAssignment other : sameDay) {
                Shift o = other.getShift();
                if (o != null && shift.getStartTime().isBefore(o.getEndTime()) && shift.getEndTime().isAfter(o.getStartTime())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isUrgent(ShiftChangeRequest request, LocalDateTime now) {
        ShiftAssignment a = request.getAssignment();
        return a != null && a.getShift() != null
                && reliabilityService.isWithin24Hours(a.getWorkDate(), a.getShift().getStartTime(), now);
    }

    private CoverInvitationView.InvitationItem toItem(ShiftCoverInvitation i, LocalDateTime now) {
        String timeLabel;
        if (i.getStatus() == InvitationStatus.PENDING) {
            long minutes = Math.max(0, ChronoUnit.MINUTES.between(now, i.getExpiresAt()));
            timeLabel = "còn " + (minutes >= 60 ? (minutes / 60) + "h" + String.format("%02d", minutes % 60) : minutes + " phút");
        } else {
            timeLabel = i.getRespondedAt() != null ? i.getRespondedAt().format(DATE_TIME_FORMATTER) : "";
        }
        return CoverInvitationView.InvitationItem.builder()
                .invitationId(i.getId())
                .employeeName(i.getEmployee().getFullName())
                .statusCode(i.getStatus().name())
                .statusLabel(i.getStatus().getLabel())
                .declineReasonLabel(i.getDeclineReason() != null ? i.getDeclineReason().getLabel() : null)
                .declineNote(i.getDeclineNote())
                .timeLabel(timeLabel)
                .build();
    }

    private void notifyManagerOfResponse(ShiftCoverInvitation invitation, boolean accepted) {
        Employee staff = invitation.getEmployee();
        Store store = staff != null ? staff.getStore() : null;
        Employee manager = store != null ? store.getManager() : null;
        if (manager == null) {
            return;
        }
        String message = accepted
                ? staff.getFullName() + " đã nhận lời mời hỗ trợ " + shiftLabel(invitation.getRequest()) + ". Đơn đang chờ bạn duyệt."
                : staff.getFullName() + " từ chối lời mời hỗ trợ " + shiftLabel(invitation.getRequest())
                  + (invitation.getDeclineReason() != null ? " (" + invitation.getDeclineReason().getLabel() + ")" : "") + ".";
        notifyEmployee(manager.getId(),
                accepted ? "Nhân viên đã nhận ca hỗ trợ" : "Nhân viên từ chối lời mời hỗ trợ",
                message,
                NotificationType.SHIFT_COVER_RESPONDED,
                invitation.getRequest().getId());
    }

    private void notifyEmployee(Integer employeeId, String title, String message, NotificationType type, Integer requestId) {
        if (employeeId == null || notificationService == null) {
            return;
        }
        userRepository.findByEmployee_Id(employeeId)
                .filter(u -> u.getId() != null && Boolean.TRUE.equals(u.getIsActive()))
                .ifPresent(u -> notificationService.notifyUsers(List.of(u), title, message, type, SHIFT_CHANGE_REF, requestId));
    }

    private String shiftLabel(ShiftChangeRequest request) {
        if (request == null || request.getAssignment() == null || request.getAssignment().getShift() == null) {
            return "ca làm việc";
        }
        ShiftAssignment a = request.getAssignment();
        return a.getShift().getShiftName() + " ngày " + a.getWorkDate().format(DATE_FORMATTER);
    }

    private Store resolveManagerStore(AuthenticatedUser user) {
        if (user == null || user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
    }
}
