package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.DeclineReason;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.InvitationStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.CoverInvitationView;
import com.example.coffee_hrm.entity.*;
import com.example.coffee_hrm.repository.*;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ReliabilityService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShiftCoverInvitationServiceImplTest {

    @Mock private ShiftCoverInvitationRepository invitationRepository;
    @Mock private ShiftChangeRequestRepository shiftChangeRequestRepository;
    @Mock private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApprovedLeaveCoverage approvedLeaveCoverage;
    @Mock private ReliabilityService reliabilityService;
    @Mock private ScheduleService scheduleService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ShiftCoverInvitationServiceImpl service;

    private Store store;
    private Employee manager;
    private Employee requester;
    private Employee thu;
    private Employee duc;
    private Employee uncertified;
    private Shift shift;
    private ShiftChangeRequest openRequest;
    private AuthenticatedUser managerUser;
    private AuthenticatedUser thuUser;

    @BeforeEach
    void setUp() {
        manager = employee(1, "Quản lý", CertificationStatus.CERTIFIED);
        store = Store.builder().id(10).storeName("CoffeeHRM").manager(manager).build();
        manager.setStore(store);
        requester = employee(20, "Trần Văn Tuấn", CertificationStatus.CERTIFIED);
        thu = employee(30, "Lê Thị Thu", CertificationStatus.CERTIFIED);
        duc = employee(40, "Phạm Minh Đức", CertificationStatus.CERTIFIED);
        uncertified = employee(50, "Nhân viên mới", CertificationStatus.NOTCERTIFIED);

        LocalDateTime start = VietnamTime.today().plusDays(1).atTime(14, 0);
        shift = Shift.builder().id(2).shiftName("Ca Chiều").store(store)
                .startTime(start.toLocalTime()).endTime(start.toLocalTime().plusHours(3)).build();
        ShiftAssignment assignment = ShiftAssignment.builder().id(700).employee(requester).shift(shift)
                .workDate(start.toLocalDate()).status(AssignmentStatus.ASSIGNED).build();
        openRequest = ShiftChangeRequest.builder().id(77).assignment(assignment).employee(requester)
                .status(ApprovalStatus.PENDING).build();

        managerUser = AuthenticatedUser.from(user(100, RoleName.MANAGER, manager));
        thuUser = AuthenticatedUser.from(user(300, RoleName.STAFF, thu));

        when(storeRepository.findByManager_Id(1)).thenReturn(Optional.of(store));
        when(shiftChangeRequestRepository.findByIdWithDetails(77)).thenReturn(Optional.of(openRequest));
        when(reliabilityService.isWithin24Hours(any(), any(), any())).thenReturn(true);
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(anyInt(), any(), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(List.of());
        when(invitationRepository.save(any(ShiftCoverInvitation.class))).thenAnswer(inv -> {
            ShiftCoverInvitation i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(900);
            }
            return i;
        });
    }

    @Test
    void managerPanelListsOnlyEligibleCandidates() {
        // Đức trùng giờ ca khác, nhân viên mới chưa có chứng chỉ -> bị loại
        ShiftAssignment ducOther = ShiftAssignment.builder().id(800).employee(duc)
                .shift(Shift.builder().id(3).startTime(shift.getStartTime().minusHours(1))
                        .endTime(shift.getStartTime().plusHours(1)).build())
                .workDate(openRequest.getAssignment().getWorkDate()).status(AssignmentStatus.ASSIGNED).build();
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(eq(40), any(), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(List.of(ducOther));
        when(shiftChangeRequestRepository.findOpenRescueRequestsForStore(10)).thenReturn(List.of(openRequest));
        when(invitationRepository.findForRequests(any())).thenReturn(List.of());
        when(employeeRepository.findByStore_IdAndStatus(10, EmployeeStatus.ACTIVE))
                .thenReturn(List.of(manager, requester, thu, duc, uncertified));
        when(reliabilityService.getCurrentMonthScores(10)).thenReturn(Map.of(30, 108));

        Map<Integer, CoverInvitationView.ManagerPanel> panels = service.getManagerPanels(managerUser);

        CoverInvitationView.ManagerPanel panel = panels.get(77);
        assertNotNull(panel);
        assertTrue(panel.isUrgent());
        assertEquals(1, panel.getCandidates().size());
        assertEquals(30, panel.getCandidates().getFirst().getEmployeeId());
        assertEquals(108, panel.getCandidates().getFirst().getMonthScore());
    }

    @Test
    void inviteCreatesPendingUrgentInvitationWithTwoHourDeadline() {
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(thu));
        when(invitationRepository.existsByRequest_IdAndEmployee_Id(77, 30)).thenReturn(false);
        when(userRepository.findByEmployee_Id(30)).thenReturn(Optional.of(user(300, RoleName.STAFF, thu)));

        int sent = service.invite(77, List.of(30), managerUser);

        assertEquals(1, sent);
        ArgumentCaptor<ShiftCoverInvitation> captor = ArgumentCaptor.forClass(ShiftCoverInvitation.class);
        verify(invitationRepository).save(captor.capture());
        ShiftCoverInvitation saved = captor.getValue();
        assertEquals(InvitationStatus.PENDING, saved.getStatus());
        assertTrue(saved.isUrgent());
        assertFalse(saved.getExpiresAt().isAfter(saved.getSentAt().plusHours(2)));
        verify(notificationService).notifyUsers(any(), any(), any(), any(), any(), any());
    }

    @Test
    void inviteRejectsIneligibleEmployee() {
        when(employeeRepository.findByIdWithStore(50)).thenReturn(Optional.of(uncertified));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.invite(77, List.of(50), managerUser));
        assertTrue(ex.getMessage().contains("không đủ điều kiện"));
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void inviteRejectsRequestThatAlreadyHasTaker() {
        openRequest.setTargetEmployee(duc);

        assertThrows(BusinessException.class, () -> service.invite(77, List.of(30), managerUser));
    }

    @Test
    void acceptDelegatesToVolunteerFlow() {
        ShiftCoverInvitation invitation = pendingInvitation();
        when(invitationRepository.findByIdWithDetails(900)).thenReturn(Optional.of(invitation));

        service.accept(900, thuUser);

        verify(scheduleService).volunteerRescueShift(77, thuUser);
    }

    @Test
    void acceptRejectsExpiredInvitation() {
        ShiftCoverInvitation invitation = pendingInvitation();
        invitation.setExpiresAt(VietnamTime.now().minusMinutes(1));
        when(invitationRepository.findByIdWithDetails(900)).thenReturn(Optional.of(invitation));

        assertThrows(BusinessException.class, () -> service.accept(900, thuUser));
        verify(scheduleService, never()).volunteerRescueShift(any(), any());
    }

    @Test
    void declineRequiresReasonAndRecordsDecline() {
        ShiftCoverInvitation invitation = pendingInvitation();
        when(invitationRepository.findByIdWithDetails(900)).thenReturn(Optional.of(invitation));
        when(reliabilityService.recordInvitationDecline(invitation, false)).thenReturn(3);

        assertThrows(BusinessException.class, () -> service.decline(900, null, null, thuUser));
        assertThrows(BusinessException.class, () -> service.decline(900, "OTHER", "  ", thuUser));

        int count = service.decline(900, "STUDY", null, thuUser);

        assertEquals(3, count);
        assertEquals(InvitationStatus.DECLINED, invitation.getStatus());
        assertEquals(DeclineReason.STUDY, invitation.getDeclineReason());
        verify(reliabilityService).recordInvitationDecline(invitation, false);
    }

    @Test
    void declineByOtherEmployeeIsForbidden() {
        ShiftCoverInvitation invitation = pendingInvitation();
        invitation.setEmployee(duc);
        when(invitationRepository.findByIdWithDetails(900)).thenReturn(Optional.of(invitation));

        assertThrows(BusinessException.class, () -> service.decline(900, "PERSONAL", null, thuUser));
    }

    @Test
    void expireOverdueMarksExpiredAndCountsAsDecline() {
        ShiftCoverInvitation invitation = pendingInvitation();
        when(invitationRepository.findOverduePending(any())).thenReturn(List.of(invitation));

        assertEquals(1, service.expireOverdue());

        assertEquals(InvitationStatus.EXPIRED, invitation.getStatus());
        verify(reliabilityService).recordInvitationDecline(invitation, true);
    }

    private ShiftCoverInvitation pendingInvitation() {
        return ShiftCoverInvitation.builder()
                .id(900)
                .request(openRequest)
                .employee(thu)
                .status(InvitationStatus.PENDING)
                .urgent(true)
                .sentAt(VietnamTime.now())
                .expiresAt(VietnamTime.now().plusHours(2))
                .build();
    }

    private Employee employee(int id, String name, CertificationStatus cert) {
        Employee e = Employee.builder().id(id).fullName(name).status(EmployeeStatus.ACTIVE)
                .certificationStatus(cert).reliabilityScore(100).build();
        if (store != null) {
            e.setStore(store);
        }
        return e;
    }

    private User user(int id, RoleName roleName, Employee employee) {
        Role role = Role.builder().id(roleName == RoleName.MANAGER ? 2 : 3).roleName(roleName).build();
        return User.builder().id(id).username("u" + id).passwordHash("h").role(role).employee(employee).isActive(true).build();
    }
}
