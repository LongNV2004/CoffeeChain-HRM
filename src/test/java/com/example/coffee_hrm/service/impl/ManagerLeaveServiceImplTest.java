package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.ReviewLeaveRequest;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.LeaveBalance;
import com.example.coffee_hrm.entity.LeaveRequest;
import com.example.coffee_hrm.entity.Shift;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagerLeaveServiceImplTest {
    @Mock private StoreRepository stores;
    @Mock private UserRepository users;
    @Mock private EmployeeRepository employees;
    @Mock private LeaveRequestRepository requests;
    @Mock private LeaveBalanceRepository balances;
    @Mock private ShiftAssignmentRepository assignments;
    @Mock private ShiftChangeRequestRepository changes;
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
        store = Store.builder().id(3).totalLeaveDays(12).isActive(true).build();
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

    private ShiftAssignment shiftAssignment(int id) {
        Shift shift = Shift.builder().id(id).shiftName("Ca sáng")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .store(store).build();
        return ShiftAssignment.builder().id(id).employee(employee).shift(shift)
                .workDate(date).status(AssignmentStatus.ASSIGNED).build();
    }

    @Test
    void approvingTwoShiftsSameDateConsumesOneDayAndCancelsBoth() {
        stubPendingReview();
        ShiftAssignment first = shiftAssignment(81);
        ShiftAssignment second = shiftAssignment(82);
        when(assignments.findActiveAssignmentsForLeave(7, 3, date, date, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(first, second));
        when(assignments.findPendingLeaveWorkDatesExcluding(
                eq(7), eq(3), any(), any(), eq(AssignmentStatus.ASSIGNED),
                eq(ApprovalStatus.PENDING), eq(19))).thenReturn(List.of());
        when(balances.lockByEmployeeIdAndYear(7, date.getYear())).thenReturn(Optional.empty());
        ShiftChangeRequest change = ShiftChangeRequest.builder().id(22)
                .status(ApprovalStatus.PENDING).build();
        when(changes.findPendingForAffectedAssignments(List.of(81, 82), ApprovalStatus.PENDING))
                .thenReturn(List.of(change));

        var result = service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager);

        assertEquals(1, result.chargedWorkDays());
        assertEquals(2, result.releasedShifts());
        assertEquals(ApprovalStatus.APPROVED, leave.getStatus());
        assertEquals(AssignmentStatus.CANCELLED, first.getStatus());
        assertEquals(AssignmentStatus.CANCELLED, second.getStatus());
        assertEquals(ApprovalStatus.REJECTED, change.getStatus());
        ArgumentCaptor<LeaveBalance> saved = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(balances).save(saved.capture());
        assertEquals(1, saved.getValue().getUsedDays());
        assertEquals(11, saved.getValue().getRemainingDays());
    }

    @Test
    void approvalIsBlockedStartingOnFirstLeaveDay() {
        date = VietnamTime.today();
        leave.setStartDate(date);
        leave.setEndDate(date);
        stubPendingReview();
        assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager));
        verifyNoInteractions(assignments, balances, changes);
        assertEquals(ApprovalStatus.PENDING, leave.getStatus());
    }

    @Test
    void lateRejectionIsAllowedAndPersistsReason() {
        date = VietnamTime.today().minusDays(2);
        leave.setStartDate(date);
        leave.setEndDate(date);
        stubPendingReview();
        var result = service.review(19,
                new ReviewLeaveRequest(ApprovalStatus.REJECTED, " Lịch làm việc không đủ người "), manager);
        assertEquals(ApprovalStatus.REJECTED, result.status());
        assertEquals("Lịch làm việc không đủ người", leave.getRejectReason());
        verifyNoInteractions(assignments, balances, changes);
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
    void approvedLeaveWithoutAssignmentsConsumesNoDaysButBlocksFutureScheduling() {
        stubPendingReview();
        when(assignments.findActiveAssignmentsForLeave(7, 3, date, date, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of());
        var result = service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager);
        assertEquals(0, result.chargedWorkDays());
        assertEquals(0, result.releasedShifts());
        assertEquals(ApprovalStatus.APPROVED, leave.getStatus());
        verifyNoInteractions(balances, changes);
    }

    @Test
    void approvalAcrossYearBoundaryUpdatesBothBalances() {
        stubPendingReview();
        date = LocalDate.of(VietnamTime.today().getYear() + 1, 12, 31);
        LocalDate secondDate = date.plusDays(1);
        leave.setStartDate(date);
        leave.setEndDate(secondDate);
        ShiftAssignment first = shiftAssignment(91);
        ShiftAssignment second = ShiftAssignment.builder().id(92).employee(employee)
                .shift(first.getShift()).workDate(secondDate)
                .status(AssignmentStatus.ASSIGNED).build();
        when(assignments.findActiveAssignmentsForLeave(7, 3, date, secondDate, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(first, second));
        when(assignments.findPendingLeaveWorkDatesExcluding(
                eq(7), eq(3), any(), any(), eq(AssignmentStatus.ASSIGNED),
                eq(ApprovalStatus.PENDING), eq(19))).thenReturn(List.of());
        when(balances.lockByEmployeeIdAndYear(7, date.getYear())).thenReturn(Optional.empty());
        when(balances.lockByEmployeeIdAndYear(7, secondDate.getYear())).thenReturn(Optional.empty());
        when(changes.findPendingForAffectedAssignments(List.of(91, 92), ApprovalStatus.PENDING))
                .thenReturn(List.of());
        var result = service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager);
        assertEquals(2, result.chargedWorkDays());
        verify(balances, times(2)).save(any(LeaveBalance.class));
    }

    @Test
    void quotaExhaustedPreventsApprovalAndCancellation() {
        stubPendingReview();
        ShiftAssignment shift = shiftAssignment(81);
        when(assignments.findActiveAssignmentsForLeave(7, 3, date, date, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(shift));
        when(assignments.findPendingLeaveWorkDatesExcluding(
                eq(7), eq(3), any(), any(), eq(AssignmentStatus.ASSIGNED),
                eq(ApprovalStatus.PENDING), eq(19))).thenReturn(List.of());
        LeaveBalance used = LeaveBalance.builder().id(44).employee(employee)
                .year(date.getYear()).totalDays(12).usedDays(12).remainingDays(0).build();
        when(balances.lockByEmployeeIdAndYear(7, date.getYear())).thenReturn(Optional.of(used));

        assertThrows(BusinessException.class,
                () -> service.review(19, new ReviewLeaveRequest(ApprovalStatus.APPROVED, null), manager));
        assertEquals(ApprovalStatus.PENDING, leave.getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, shift.getStatus());
        verify(assignments, never()).saveAll(any());
    }
}
