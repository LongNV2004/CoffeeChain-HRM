package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.LeaveType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateLeaveRequest;
import com.example.coffee_hrm.dto.response.LeaveRequestResponse;
import com.example.coffee_hrm.dto.response.LeaveSlotPage;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.LeaveBalance;
import com.example.coffee_hrm.entity.LeaveRequest;
import com.example.coffee_hrm.entity.LeaveRequestSlot;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.LeaveBalanceRepository;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.LeaveRequestSlotRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceImplTest {
    @Mock private EmployeeRepository employees;
    @Mock private LeaveRequestRepository requests;
    @Mock private LeaveRequestSlotRepository slots;
    @Mock private LeaveBalanceRepository balances;
    @Mock private ShiftAssignmentRepository assignments;
    @Mock private AttendanceRepository attendance;
    @Mock private UserRepository users;
    @Mock private NotificationService notifications;
    @Mock private AuthenticatedUser user;
    @InjectMocks private LeaveRequestServiceImpl service;

    private Store store;
    private Employee employee;
    private LocalDate day;

    @BeforeEach
    void setup() {
        day = VietnamTime.today().plusDays(3);
        store = Store.builder().id(3).storeName("Cửa hàng 1").totalLeaveDays(12)
                .totalLeaveSlots(8).isActive(true).build();
        employee = Employee.builder().id(7).fullName("Nhân viên A").store(store)
                .status(EmployeeStatus.ACTIVE).build();
        lenient().when(user.getEmployeeId()).thenReturn(7);
        lenient().when(user.getStoreId()).thenReturn(3);
        lenient().when(user.isActive()).thenReturn(true);
        lenient().when(slots.findBlockedAssignmentIds(anyCollection(), anyCollection())).thenReturn(List.of());
        lenient().when(balances.findUsedSlots(eq(7), anyInt())).thenReturn(Optional.of(0));
        lenient().when(slots.countReservedSlots(eq(7), eq(ApprovalStatus.PENDING), eq(LeaveType.ANNUAL),
                any(), any(), isNull())).thenReturn(0L);
    }

    @Test
    void staffCreatesPendingAnnualLeaveWithoutDeductingBalance() {
        ShiftAssignment morning = slot(81, LocalTime.of(7, 0));
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        when(assignments.lockByIds(anyCollection())).thenReturn(List.of(morning));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(21);
            return saved;
        });

        LeaveRequestResponse response = service.create(request(LeaveType.ANNUAL, List.of(81), "Về quê"), user);

        assertEquals(ApprovalStatus.PENDING, response.getStatus());
        assertEquals(1, response.getRequestedWorkDays());
        assertTrue(response.isDeductsAnnualLeave());
        verify(balances, never()).save(any());
        verify(slots, times(1)).save(any());
        ArgumentCaptor<LeaveRequest> saved = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(requests).saveAndFlush(saved.capture());
        assertEquals(day, saved.getValue().getStartDate());
        assertEquals(day, saved.getValue().getEndDate());
    }

    @Test
    void twoSlotsOnTheSameDayCountAsTwoSlots() {
        ShiftAssignment morning = slot(81, LocalTime.of(7, 0));
        ShiftAssignment evening = slot(82, LocalTime.of(17, 0));
        evening.getShift().setShiftName("Ca tối");
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        when(assignments.lockByIds(anyCollection())).thenReturn(List.of(morning, evening));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponse response = service.create(
                request(LeaveType.ANNUAL, List.of(81, 82), "Nghỉ hai ca"), user);

        assertEquals(2, response.getRequestedWorkDays());
        assertEquals(2, response.getSlots().size());
        verify(slots, times(2)).save(any());
        verify(balances, never()).save(any());
    }

    @Test
    void dayBalanceIsNotUsedAsSlotQuota() {
        store.setTotalLeaveSlots(null);
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        when(assignments.lockByIds(anyCollection())).thenReturn(List.of(slot(81, LocalTime.of(7, 0))));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(81), "Nghỉ"), user));

        assertTrue(error.getMessage().contains("chưa được admin thiết lập"));
        assertFalse(error.getMessage().contains("12"));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void missingReasonAndEmptySelectionAreRejected() {
        BusinessException missing = assertThrows(BusinessException.class, () -> service.create(
                request(LeaveType.ANNUAL, List.of(81), "   "), user));
        assertEquals("Vui lòng nhập lý do xin nghỉ.", missing.getMessage());

        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        BusinessException empty = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(), "Nghỉ"), user));
        assertEquals("Vui lòng chọn ít nhất một ca làm việc.", empty.getMessage());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void duplicateForeignPastAndOverlappingSlotsAreRejected() {
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(81, 81), "Nghỉ"), user));
        assertTrue(duplicate.getMessage().contains("trùng"));

        Employee other = Employee.builder().id(99).fullName("Người khác").store(store)
                .status(EmployeeStatus.ACTIVE).build();
        ShiftAssignment foreign = slot(81, LocalTime.of(7, 0));
        foreign.setEmployee(other);
        when(assignments.lockByIds(anyCollection())).thenReturn(List.of(foreign));
        BusinessException notYours = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(81), "Nghỉ"), user));
        assertEquals("Một hoặc nhiều ca không thuộc lịch làm việc của bạn.", notYours.getMessage());
        assertFalse(notYours.getMessage().contains("Người khác"));

        ShiftAssignment past = slot(81, LocalTime.of(7, 0));
        past.setWorkDate(VietnamTime.today().minusDays(1));
        when(assignments.lockByIds(anyCollection())).thenReturn(List.of(past));
        BusinessException expired = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(81), "Nghỉ"), user));
        assertTrue(expired.getMessage().contains("đã qua") || expired.getMessage().contains("đã bắt đầu"));

        when(assignments.lockByIds(anyCollection())).thenReturn(List.of(slot(81, LocalTime.of(7, 0))));
        when(slots.findBlockedAssignmentIds(anyCollection(), anyCollection())).thenReturn(List.of(81));
        BusinessException overlap = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(81), "Nghỉ"), user));
        assertTrue(overlap.getMessage().contains("đã có đơn nghỉ"));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void insufficientAnnualSlotsAreRejectedAndUnpaidLeaveIsNot() {
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        when(assignments.lockByIds(anyCollection())).thenReturn(List.of(slot(81, LocalTime.of(7, 0))));
        when(balances.findUsedSlots(7, day.getYear())).thenReturn(Optional.of(8));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(81), "Nghỉ phép"), user));
        assertTrue(error.getMessage().contains("không đủ"));

        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LeaveRequestResponse unpaid = service.create(
                request(LeaveType.UNPAID, List.of(81), "Việc riêng không lương"), user);
        assertEquals(LeaveType.UNPAID, unpaid.getLeaveType());
        assertFalse(unpaid.isDeductsAnnualLeave());
        verify(balances, never()).save(any());
    }

    @Test
    void historyAndDetailStayInsideTheSignedInEmployee() {
        LeaveRequest own = LeaveRequest.builder().id(8).employee(employee).startDate(day).endDate(day)
                .leaveType(LeaveType.SICK).reason("Ốm").status(ApprovalStatus.REJECTED)
                .rejectReason("Thiếu người").requestedWorkDays(1).build();
        when(employees.findByIdWithStoreAndManager(7)).thenReturn(Optional.of(employee));
        when(requests.searchByEmployee(eq(7), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(own)));
        when(requests.findById(8)).thenReturn(Optional.of(own));
        when(slots.findDetailedByRequestIds(anyCollection())).thenReturn(List.of());
        Employee other = Employee.builder().id(99).fullName("Người khác").store(store)
                .status(EmployeeStatus.ACTIVE).build();
        LeaveRequest foreign = LeaveRequest.builder().id(9).employee(other).startDate(day).endDate(day)
                .status(ApprovalStatus.PENDING).build();
        when(requests.findById(9)).thenReturn(Optional.of(foreign));

        assertEquals(1, service.history(0, null, null, null, user).items().size());
        assertEquals("Thiếu người", service.detail(8, user).getRejectReason());
        assertThrows(BusinessException.class, () -> service.detail(9, user));
    }

    @Test
    void pendingLeaveCanBeCancelledWithoutTouchingBalanceOrShifts() {
        when(user.getUserId()).thenReturn(4);
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        LeaveRequest pending = LeaveRequest.builder().id(8).employee(employee).startDate(day).endDate(day)
                .leaveType(LeaveType.ANNUAL).status(ApprovalStatus.PENDING).reason("Nghỉ").build();
        when(requests.lockById(8)).thenReturn(Optional.of(pending));
        when(slots.findDetailedByRequestIds(List.of(8))).thenReturn(List.of());
        when(users.findById(4)).thenReturn(Optional.of(User.builder().id(4).username("staff").build()));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponse cancelled = service.cancel(8, user);

        assertEquals(ApprovalStatus.CANCELLED, cancelled.getStatus());
        verify(balances, never()).save(any());
        verify(assignments, never()).saveAll(any());
    }

    @Test
    void approvedCancelRefundsTheSlotCountOnceAndRestoresOnlyThatShift() {
        when(user.getUserId()).thenReturn(4);
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        LeaveRequest approved = LeaveRequest.builder().id(8).employee(employee).startDate(day).endDate(day)
                .leaveType(LeaveType.ANNUAL).status(ApprovalStatus.APPROVED).requestedWorkDays(1).build();
        ShiftAssignment morning = slot(81, LocalTime.of(7, 0));
        morning.setStatus(AssignmentStatus.CANCELLED);
        morning.setCancelledByLeaveRequest(approved);
        ShiftAssignment afternoon = slot(82, LocalTime.of(13, 0));
        when(requests.lockById(8)).thenReturn(Optional.of(approved));
        when(slots.findDetailedByRequestIds(List.of(8))).thenReturn(List.of(
                LeaveRequestSlot.builder().leaveRequest(approved).assignment(morning).build()));
        LeaveBalance balance = LeaveBalance.builder().employee(employee).year(day.getYear())
                .totalDays(12).usedDays(3).remainingDays(9)
                .totalSlots(8).usedSlots(1).remainingSlots(7).build();
        when(balances.lockByEmployeeIdAndYear(7, day.getYear())).thenReturn(Optional.of(balance));
        when(users.findById(4)).thenReturn(Optional.of(User.builder().id(4).username("staff").build()));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.cancel(8, user);

        assertEquals(ApprovalStatus.CANCELLED, approved.getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, morning.getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, afternoon.getStatus());
        assertEquals(0, balance.getUsedSlots());
        assertEquals(3, balance.getUsedDays());
        assertEquals(12, balance.getTotalDays());
    }

    @Test
    void approvedCancelIsRejectedWhenAttendanceExists() {
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        LeaveRequest approved = LeaveRequest.builder().id(8).employee(employee).startDate(day).endDate(day)
                .leaveType(LeaveType.ANNUAL).status(ApprovalStatus.APPROVED).build();
        ShiftAssignment morning = slot(81, LocalTime.of(7, 0));
        morning.setStatus(AssignmentStatus.CANCELLED);
        morning.setCancelledByLeaveRequest(approved);
        when(requests.lockById(8)).thenReturn(Optional.of(approved));
        when(slots.findDetailedByRequestIds(List.of(8))).thenReturn(List.of(
                LeaveRequestSlot.builder().leaveRequest(approved).assignment(morning).build()));
        when(attendance.findByEmployee_IdAndShift_IdAndWorkDate(7, 81, day))
                .thenReturn(Optional.of(new com.example.coffee_hrm.entity.Attendance()));

        BusinessException error = assertThrows(BusinessException.class, () -> service.cancel(8, user));
        assertTrue(error.getMessage().contains("chấm công"));
        verify(requests, never()).saveAndFlush(any());
        verify(balances, never()).save(any());
    }

    @Test
    void terminatedOrLockedAccountCannotCreateLeave() {
        employee.setStatus(EmployeeStatus.TERMINATED);
        when(employees.lockByIdForLeaveRequest(7)).thenReturn(Optional.of(employee));
        BusinessException resigned = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(81), "Nghỉ"), user));
        assertTrue(resigned.getMessage().contains("nghỉ việc"));

        employee.setStatus(EmployeeStatus.ACTIVE);
        when(user.isActive()).thenReturn(false);
        BusinessException locked = assertThrows(BusinessException.class,
                () -> service.create(request(LeaveType.ANNUAL, List.of(81), "Nghỉ"), user));
        assertTrue(locked.getMessage().contains("khóa"));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void slotListOmitsShiftsThatAlreadyStarted() {
        when(employees.findByIdWithStoreAndManager(7)).thenReturn(Optional.of(employee));
        ShiftAssignment started = slot(11, LocalTime.of(7, 0));
        started.setWorkDate(VietnamTime.today().minusDays(1));
        ShiftAssignment upcoming = slot(12, LocalTime.of(17, 0));
        when(assignments.findScheduleForLeavePicker(eq(7), any(), any())).thenReturn(List.of(started, upcoming));

        LeaveSlotPage page = service.listSlots(null, null, user);

        assertEquals(1, page.slots().size());
        assertEquals(12, page.slots().get(0).assignmentId());
    }

    private CreateLeaveRequest request(LeaveType type, List<Integer> assignmentIds, String reason) {
        return CreateLeaveRequest.builder().leaveType(type).assignmentIds(assignmentIds).reason(reason).build();
    }

    private ShiftAssignment slot(int id, LocalTime start) {
        Shift shift = Shift.builder().id(id).shiftName("Ca sáng").store(store)
                .startTime(start).endTime(start.plusHours(4)).build();
        return ShiftAssignment.builder().id(id).employee(employee).shift(shift)
                .workDate(day).status(AssignmentStatus.ASSIGNED).build();
    }
}
