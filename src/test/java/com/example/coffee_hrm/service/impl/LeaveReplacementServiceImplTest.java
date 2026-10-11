package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.entity.*;
import com.example.coffee_hrm.repository.*;
import com.example.coffee_hrm.security.AuthenticatedUser;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveReplacementServiceImplTest {
    @Mock private StoreRepository stores;
    @Mock private LeaveWorkspaceRepository leaves;
    @Mock private LeaveReplacementAssignmentRepository gaps;
    @Mock private ShiftAssignmentRepository assignments;
    @Mock private WorkAvailabilityRepository availabilities;
    @Mock private EmployeeRepository employees;
    @Mock private AuthenticatedUser manager;
    @InjectMocks private LeaveReplacementServiceImpl service;
    private Store store;
    private Employee absent;
    private LeaveRequest leave;
    private Shift shift;
    private ShiftAssignment gap;
    private LocalDate day;

    @BeforeEach
    void setup() {
        store = Store.builder().id(3).isActive(true).build();
        absent = Employee.builder().id(7).store(store).fullName("Nghỉ phép").build();
        day = VietnamTime.today().plusDays(5);
        shift = Shift.builder().id(21).store(store).shiftName("Ca sáng")
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(12, 0)).build();
        leave = LeaveRequest.builder().id(40).employee(absent).startDate(day)
                .endDate(day).status(ApprovalStatus.APPROVED).build();
        gap = ShiftAssignment.builder().id(90).shift(shift).employee(absent)
                .workDate(day).status(AssignmentStatus.CANCELLED).build();
    }

    private void managed() {
        when(manager.getEmployeeId()).thenReturn(10);
        when(stores.findByManager_Id(10)).thenReturn(Optional.of(store));
    }

    private void suggestSetup() {
        managed();
        when(leaves.findById(40)).thenReturn(Optional.of(leave));
        when(gaps.findById(90)).thenReturn(Optional.of(gap));
    }

    private void candidateQueries(List<ShiftAssignment> roster, List<WorkAvailability> registered,
                                  List<LeaveRequest> approved, List<Employee> eligible) {
        when(assignments.findWeeklyAssignmentsForStore(eq(3), any(), any(),
                eq(AssignmentStatus.ASSIGNED))).thenReturn(roster);
        when(availabilities.findApprovedCovering(3, day, day, ApprovalStatus.APPROVED))
                .thenReturn(registered);
        when(leaves.findApprovedOnDate(3, ApprovalStatus.APPROVED, day)).thenReturn(approved);
        when(employees.findByStore_IdAndStatus(3, EmployeeStatus.ACTIVE)).thenReturn(eligible);
    }

    private Employee active(int id) {
        return Employee.builder().id(id).fullName("NV " + id).store(store)
                .status(EmployeeStatus.ACTIVE).build();
    }

    private WorkAvailability registered(Employee candidate, Shift slot) {
        return WorkAvailability.builder().employee(candidate).shift(slot)
                .dayOfWeek(day.getDayOfWeek().getValue())
                .validFrom(day.minusWeeks(2)).validTo(day.plusWeeks(2))
                .status(ApprovalStatus.APPROVED).build();
    }

    private ShiftAssignment assigned(Employee candidate, Shift slot, LocalDate date) {
        return ShiftAssignment.builder().employee(candidate).shift(slot).workDate(date)
                .status(AssignmentStatus.ASSIGNED).build();
    }

    @Test
    void registeredCandidateRanksAheadOfFreeWithoutRegistration() {
        suggestSetup();
        Employee registered = active(11);
        Employee unregistered = active(12);
        candidateQueries(List.of(), List.of(registered(registered, shift)), List.of(leave),
                List.of(absent, unregistered, registered));
        var result = service.suggest(manager, 40, 90);
        assertEquals(2, result.candidates().size());
        assertEquals(11, result.candidates().getFirst().employeeId());
        assertEquals(1, result.candidates().getFirst().priorityLevel());
        assertEquals(2, result.candidates().getLast().priorityLevel());
    }

    @Test
    void excludesClashingShiftAndApprovedLeaveAndConflictingRegistration() {
        suggestSetup();
        Employee busy = active(11);
        Employee approvedLeave = active(12);
        Employee clashRegistration = active(13);
        Employee free = active(14);
        Shift otherShift = Shift.builder().id(22).store(store).shiftName("Ca khác")
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(13, 0)).build();
        LeaveRequest otherLeave = LeaveRequest.builder().employee(approvedLeave)
                .status(ApprovalStatus.APPROVED).startDate(day).endDate(day).build();
        candidateQueries(List.of(assigned(busy, otherShift, day)),
                List.of(registered(clashRegistration, otherShift)),
                List.of(leave, otherLeave), List.of(absent, busy, approvedLeave, clashRegistration, free));
        var result = service.suggest(manager, 40, 90);
        assertEquals(1, result.candidates().size());
        assertEquals(14, result.candidates().getFirst().employeeId());
    }

    @Test
    void rejectsCrossStoreLeaveBeforeAccessingCandidateData() {
        managed();
        leave.getEmployee().setStore(Store.builder().id(99).build());
        when(leaves.findById(40)).thenReturn(Optional.of(leave));
        assertThrows(BusinessException.class, () -> service.suggest(manager, 40, 90));
        verifyNoInteractions(assignments, availabilities, employees);
    }

    @Test
    void rejectsAssignmentThatIsNotCancelled() {
        suggestSetup();
        gap.setStatus(AssignmentStatus.ASSIGNED);
        assertThrows(BusinessException.class, () -> service.suggest(manager, 40, 90));
        verifyNoInteractions(assignments, availabilities, employees);
    }

    @Test
    void noCandidatesIsAllowed() {
        suggestSetup();
        candidateQueries(List.of(), List.of(), List.of(leave), List.of(absent));
        assertTrue(service.suggest(manager, 40, 90).candidates().isEmpty());
    }

    @Test
    void weeklyAssignmentCountsAreUsedWithinTheSamePriority() {
        suggestSetup();
        Employee moreShifts = active(11);
        Employee fewerShifts = active(12);
        Shift anotherShift = Shift.builder().id(23).store(store).shiftName("Ca chiều")
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(17, 0)).build();
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        candidateQueries(List.of(assigned(moreShifts, anotherShift, monday)),
                List.of(registered(moreShifts, shift), registered(fewerShifts, shift)),
                List.of(leave), List.of(absent, moreShifts, fewerShifts));
        var result = service.suggest(manager, 40, 90);
        assertEquals(12, result.candidates().getFirst().employeeId());
        assertEquals(11, result.candidates().getLast().employeeId());
    }
}
