package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.LeaveType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.ReviewLeaveRequest;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.LeaveBalance;
import com.example.coffee_hrm.entity.LeaveRequest;
import com.example.coffee_hrm.entity.LeaveRequestSlot;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.LeaveBalanceRepository;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.LeaveRequestSlotRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
import com.example.coffee_hrm.security.AuthenticatedUser;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerLeaveServiceImplTest {
    @Mock private StoreRepository stores;
    @Mock private UserRepository users;
    @Mock private EmployeeRepository employees;
    @Mock private LeaveRequestRepository requests;
    @Mock private LeaveRequestSlotRepository slots;
    @Mock private LeaveBalanceRepository balances;
    @Mock private ShiftAssignmentRepository assignments;
    @Mock private AttendanceRepository attendance;
    @Mock private ApprovedLeaveCoverage coverage;
    @Mock private ShiftChangeRequestRepository changes;
    @Mock private NotificationService notifications;
    @Mock private AuthenticatedUser manager;
    @InjectMocks private ManagerLeaveServiceImpl service;

    private Store store;
    private Employee employee;
    private LeaveRequest leave;
    private User reviewer;
    private LocalDate date;

    @BeforeEach
    void setup() {
        date = VietnamTime.today().plusDays(2);
        store = Store.builder().id(3).totalLeaveDays(12).totalLeaveSlots(20).isActive(true).build();
        employee = Employee.builder().id(7).fullName("Nhân viên A").store(store).build();
        leave = LeaveRequest.builder().id(19).employee(employee).startDate(date)
                .endDate(date).status(ApprovalStatus.PENDING).reason("Nghỉ cá nhân").build();
        reviewer = User.builder().id(12).build();
    }

    private void stubManager() {
        when(manager.getEmployeeId()).thenReturn(10);
        when(manager.getUserId()).thenReturn(12);
        when(stores.findByManager_Id(10)).thenReturn(Optional.of(store));
    }

    private void stubPendingReview() {
        stubManager();
        when(requests.findEmployeeIdForStore(19, 3)).thenReturn(Optional.of(7));
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        when(requests.lockById(19)).thenReturn(Optional.of(leave));
        when(users.findById(12)).thenReturn(Optional.of(reviewer));
    }

    private ShiftAssignment shiftAssignment(int id, LocalTime start, LocalDate workDate) {
        Shift shift = Shift.builder().id(id).shiftName("Ca " + id)
                .startTime(start).endTime(start.plusHours(4))
                .store(store).build();
        return ShiftAssignment.builder().id(id).employee(employee).shift(shift)
                .workDate(workDate).status(AssignmentStatus.ASSIGNED).build();
    }

    private void link(ShiftAssignment... rows) {
        when(slots.findDetailedByRequestIds(List.of(19))).thenReturn(java.util.Arrays.stream(rows)
                .map(row -> LeaveRequestSlot.builder().leaveRequest(leave).assignment(row).build())
                .toList());
    }

    @Test
    void approvingOneOfThreeSlotsChargesOneSlotAndLeavesTheOthersAssigned() {
        stubPendingReview();
        ShiftAssignment morning = shiftAssignment(81, LocalTime.of(7, 0), date);
        ShiftAssignment afternoon = shiftAssignment(82, LocalTime.of(12, 0), date);
        ShiftAssignment evening = shiftAssignment(83, LocalTime.of(17, 0), date);
        link(morning);
        LeaveBalance existing = LeaveBalance.builder().id(44).employee(employee).year(date.getYear())
                .totalDays(12).usedDays(4).remainingDays(8).usedSlots(0).build();
        when(balances.lockByEmployeeIdAndYear(7, date.getYear())).thenReturn(Optional.of(existing));
        when(changes.findPendingForAffectedAssignments(List.of(81), ApprovalStatus.PENDING)).thenReturn(List.of());

        var result = service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager);

        assertEquals(1, result.chargedWorkDays());
        assertEquals(1, result.releasedShifts());
        assertEquals(AssignmentStatus.CANCELLED, morning.getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, afternoon.getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, evening.getStatus());
        assertEquals(1, existing.getUsedSlots());
        assertEquals(4, existing.getUsedDays());
        assertEquals(12, existing.getTotalDays());
        assertEquals(8, existing.getRemainingDays());
    }

    @Test
    void approvingTwoSlotsSameDateChargesTwoSlots() {
        stubPendingReview();
        ShiftAssignment first = shiftAssignment(81, LocalTime.of(7, 0), date);
        ShiftAssignment second = shiftAssignment(82, LocalTime.of(17, 0), date);
        link(first, second);
        when(balances.lockByEmployeeIdAndYear(7, date.getYear())).thenReturn(Optional.empty());
        ShiftChangeRequest change = ShiftChangeRequest.builder().id(22).status(ApprovalStatus.PENDING).build();
        when(changes.findPendingForAffectedAssignments(List.of(81, 82), ApprovalStatus.PENDING))
                .thenReturn(List.of(change));

        var result = service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager);

        assertEquals(2, result.chargedWorkDays());
        assertEquals(2, result.releasedShifts());
        assertEquals(AssignmentStatus.CANCELLED, first.getStatus());
        assertEquals(AssignmentStatus.CANCELLED, second.getStatus());
        assertEquals(ApprovalStatus.REJECTED, change.getStatus());
        ArgumentCaptor<LeaveBalance> saved = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(balances).save(saved.capture());
        assertEquals(2, saved.getValue().getUsedSlots());
        assertEquals(0, saved.getValue().getUsedDays());
        assertEquals(12, saved.getValue().getTotalDays());
        assertEquals(18, saved.getValue().getRemainingSlots());
    }

    @Test
    void approvalIsBlockedWhenASelectedSlotAlreadyStarted() {
        LocalDate today = VietnamTime.today().minusDays(1);
        leave.setStartDate(today);
        leave.setEndDate(today);
        stubPendingReview();
        link(shiftAssignment(81, LocalTime.of(8, 0), today));

        assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager));
        assertEquals(ApprovalStatus.PENDING, leave.getStatus());
        verify(assignments, never()).saveAll(any());
        verify(balances, never()).save(any());
    }

    @Test
    void requestWithoutSlotsCannotBeApproved() {
        stubPendingReview();
        link();
        assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager));
        assertEquals(ApprovalStatus.PENDING, leave.getStatus());
        verifyNoInteractions(balances);
    }

    @Test
    void unsetSlotQuotaDoesNotFallBackToLeaveDays() {
        store.setTotalLeaveSlots(null);
        stubPendingReview();
        link(shiftAssignment(81, LocalTime.of(7, 0), date));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager));
        assertEquals(true, error.getMessage().contains("chưa được admin thiết lập"));
        verify(balances, never()).save(any());
    }

    @Test
    void lateRejectionIsAllowedAndPersistsReason() {
        stubPendingReview();
        var result = service.review(19,
                new ReviewLeaveRequest(ApprovalStatus.REJECTED, " Lịch làm việc không đủ người "), manager);
        assertEquals(ApprovalStatus.REJECTED, result.status());
        assertEquals("Lịch làm việc không đủ người", leave.getRejectReason());
        verifyNoInteractions(balances, changes);
    }

    @Test
    void cannotProcessSomeoneElsesStoreLeave() {
        stubManager();
        Store other = Store.builder().id(8).isActive(true).build();
        leave.getEmployee().setStore(other);
        when(requests.findEmployeeIdForStore(19, 3)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager));
        verify(employees, never()).lockByIdForLeaveRequest(anyInt());
    }

    @Test
    void cannotReviewAlreadyResolvedLeave() {
        leave.setStatus(ApprovalStatus.APPROVED);
        stubManager();
        when(requests.findEmployeeIdForStore(19, 3)).thenReturn(Optional.of(7));
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        when(requests.lockById(19)).thenReturn(Optional.of(leave));
        assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.REJECTED, "Đổi ý"), manager));
        verifyNoInteractions(balances, assignments, changes);
    }

    @Test
    void rejectingWithoutReasonFailsBeforeMutation() {
        stubPendingReview();
        assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.REJECTED, "  "), manager));
        assertEquals(ApprovalStatus.PENDING, leave.getStatus());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void approvalAcrossYearBoundaryUpdatesSlotCountersOnly() {
        stubPendingReview();
        LocalDate firstDate = LocalDate.of(VietnamTime.today().getYear() + 1, 12, 31);
        LocalDate secondDate = firstDate.plusDays(1);
        leave.setStartDate(firstDate);
        leave.setEndDate(secondDate);
        ShiftAssignment first = shiftAssignment(91, LocalTime.of(7, 0), firstDate);
        ShiftAssignment second = shiftAssignment(92, LocalTime.of(7, 0), secondDate);
        link(first, second);
        when(balances.lockByEmployeeIdAndYear(7, firstDate.getYear())).thenReturn(Optional.empty());
        when(balances.lockByEmployeeIdAndYear(7, secondDate.getYear())).thenReturn(Optional.empty());
        when(changes.findPendingForAffectedAssignments(List.of(91, 92), ApprovalStatus.PENDING)).thenReturn(List.of());

        var result = service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager);

        assertEquals(2, result.chargedWorkDays());
        verify(balances, times(2)).save(any(LeaveBalance.class));
    }

    @Test
    void quotaExhaustedPreventsApprovalAndCancellation() {
        stubPendingReview();
        ShiftAssignment shift = shiftAssignment(81, LocalTime.of(7, 0), date);
        link(shift);
        LeaveBalance used = LeaveBalance.builder().id(44).employee(employee)
                .year(date.getYear()).totalDays(12).usedDays(1).remainingDays(11)
                .totalSlots(20).usedSlots(20).remainingSlots(0).build();
        when(balances.lockByEmployeeIdAndYear(7, date.getYear())).thenReturn(Optional.of(used));

        assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager));
        assertEquals(ApprovalStatus.PENDING, leave.getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, shift.getStatus());
        assertEquals(1, used.getUsedDays());
        assertEquals(20, used.getUsedSlots());
        verify(assignments, never()).saveAll(any());
    }

    @Test
    void unpaidApprovalReleasesOnlyTheSelectedShiftWithoutDeductingSlots() {
        leave.setLeaveType(LeaveType.UNPAID);
        stubPendingReview();
        ShiftAssignment selected = shiftAssignment(81, LocalTime.of(7, 0), date);
        ShiftAssignment other = shiftAssignment(82, LocalTime.of(13, 0), date);
        link(selected);
        when(changes.findPendingForAffectedAssignments(List.of(81), ApprovalStatus.PENDING)).thenReturn(List.of());

        var result = service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager);

        assertEquals(0, result.chargedWorkDays());
        assertEquals(1, result.releasedShifts());
        assertEquals(AssignmentStatus.CANCELLED, selected.getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, other.getStatus());
        verify(balances, never()).save(any());
        verify(balances, never()).lockByEmployeeIdAndYear(anyInt(), any());
    }

    @Test
    void rejectedLeaveDoesNotChangeBalance() {
        stubPendingReview();
        service.review(19, new ReviewLeaveRequest(ApprovalStatus.REJECTED, "Thiếu người"), manager);
        assertEquals(ApprovalStatus.REJECTED, leave.getStatus());
        verifyNoInteractions(balances);
    }
}
