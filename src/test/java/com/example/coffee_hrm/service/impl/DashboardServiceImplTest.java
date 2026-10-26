package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.AdminDashboardView;
import com.example.coffee_hrm.dto.response.AttendanceClockView;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.EmployeeTrainingClassResponse;
import com.example.coffee_hrm.dto.response.ManagerDashboardView;
import com.example.coffee_hrm.dto.response.StaffDashboardView;
import com.example.coffee_hrm.dto.response.StaffDayAttendanceView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceService;
import com.example.coffee_hrm.service.ManagerDayAttendanceService;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private ShiftChangeRequestRepository shiftChangeRequestRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private MonthlyAttendanceReportRepository monthlyAttendanceReportRepository;
    @Mock
    private WorkAvailabilityRepository workAvailabilityRepository;
    @Mock
    private AttendanceService attendanceService;
    @Mock
    private ManagerDayAttendanceService managerDayAttendanceService;
    @Mock
    private TrainingService trainingService;
    @Mock
    private StaffAvailabilityService staffAvailabilityService;
    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    void adminDashboardUsesWorkingHeadcountAndSixMonthTrend() {
        YearMonth current = YearMonth.from(VietnamTime.today());
        YearMonth windowStart = current.minusMonths(5);
        when(storeRepository.count()).thenReturn(7L);
        when(storeRepository.countByStatus(StoreStatus.ACTIVE)).thenReturn(4L);
        when(employeeRepository.countWorkingByRole(EmployeeStatus.TERMINATED, RoleName.MANAGER)).thenReturn(3L);
        when(employeeRepository.countWorkingByRole(EmployeeStatus.TERMINATED, RoleName.STAFF)).thenReturn(6L);
        when(employeeRepository.countWorkingByRole(EmployeeStatus.TERMINATED, RoleName.ADMIN)).thenReturn(1L);
        when(employeeRepository.countWorkingWithoutAccount(EmployeeStatus.TERMINATED)).thenReturn(1L);
        when(employeeRepository.countByStatusNot(EmployeeStatus.TERMINATED)).thenReturn(11L);
        when(monthlyAttendanceReportRepository.countByReportYearAndReportMonthAndSubmittedAtIsNotNullAndStatusNotIn(
                eq(current.getYear()),
                eq(current.getMonthValue()),
                eq(EnumSet.of(AttendanceReportStatus.APPROVED, AttendanceReportStatus.REVISION))))
                .thenReturn(3L);

        List<Object[]> hires = new ArrayList<>();
        hires.add(new Object[]{current.getYear(), current.getMonthValue(), 4L});
        hires.add(new Object[]{2019, 1, 9L});
        hires.add(null);
        hires.add(new Object[]{null, 1, 1L});
        hires.add(new Object[]{current.getYear(), 13, 2L});
        when(employeeRepository.countHiresByMonth(any(LocalDate.class), any(LocalDate.class), eq(RoleName.ADMIN)))
                .thenReturn(hires);
        when(employeeRepository.countTerminationsByMonth(
                any(LocalDate.class), any(LocalDate.class), eq(EmployeeStatus.TERMINATED), eq(RoleName.ADMIN)))
                .thenReturn(List.<Object[]>of(new Object[]{windowStart.getYear(), windowStart.getMonthValue(), 2L}));

        AdminDashboardView view = dashboardService.buildAdminDashboard(admin());

        assertEquals(7, view.getTotalStores());
        assertEquals(4, view.getActiveStores());
        assertEquals(10, view.getTotalEmployees());
        assertEquals(3, view.getManagerCount());
        assertEquals(6, view.getStaffCount());
        assertEquals(1, view.getUnassignedRoleCount());
        assertEquals(0, view.getOtherRoleCount());
        assertEquals(3, view.getWaitingChainReportCount());
        assertEquals(6, view.getTrend().size());
        assertEquals(current.format(DateTimeFormatter.ofPattern("MM/yyyy")), view.getTrend().get(5).getLabel());
        assertEquals(4, view.getTrend().get(5).getHired());
        assertEquals(2, view.getTrend().get(0).getResigned());
        assertEquals(0, view.getTrend().get(5).getResigned());
        assertTrue(view.getTodayLabel().contains(VietnamTime.today().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        assertFalse(view.isTrendQuiet());
        verify(storeRepository, never()).countByIsActiveTrue();
        verify(employeeRepository, never()).count();
    }

    @Test
    void emptyWorkforceStillReturnsSixQuietMonths() {
        when(storeRepository.count()).thenReturn(0L);
        when(storeRepository.countByStatus(StoreStatus.ACTIVE)).thenReturn(0L);
        when(employeeRepository.countWorkingByRole(any(), any())).thenReturn(0L);
        when(employeeRepository.countWorkingWithoutAccount(EmployeeStatus.TERMINATED)).thenReturn(0L);
        when(employeeRepository.countByStatusNot(EmployeeStatus.TERMINATED)).thenReturn(0L);
        when(monthlyAttendanceReportRepository.countByReportYearAndReportMonthAndSubmittedAtIsNotNullAndStatusNotIn(
                anyInt(), anyInt(), any()))
                .thenReturn(0L);
        when(employeeRepository.countHiresByMonth(any(), any(), any())).thenReturn(List.of());
        when(employeeRepository.countTerminationsByMonth(any(), any(), any(), any())).thenReturn(null);

        AdminDashboardView view = dashboardService.buildAdminDashboard(admin());

        assertEquals(0, view.getTotalEmployees());
        assertEquals(6, view.getTrend().size());
        assertTrue(view.isTrendQuiet());
        assertTrue(view.getHiredJoined().startsWith("0,"));
    }

    @Test
    void managerWithoutStoreSeesEmptyDashboardForThatAccountOnly() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.empty());

        ManagerDashboardView view = dashboardService.buildManagerDashboard(manager());

        assertFalse(view.isStoreAssigned());
        assertEquals(0, view.getWorkingEmployeeCount());
        assertEquals(0, view.getTodaySlotCount());
        assertEquals(0, view.getTodayEmployeeCount());
        assertEquals(0, view.getPendingRequestCount());
        assertTrue(view.isTasksEmpty());
        assertTrue(view.isAttendanceEmpty());
        assertTrue(view.isScheduleEmpty());
        assertNull(view.getCompletionRate());
        verify(employeeRepository, never()).countActiveStaffAtStore(any(), any(), any(), any());
        verify(shiftAssignmentRepository, never()).findPublishedAssignedForStoreOnDate(any(), any(), any());
        verify(attendanceRepository, never()).findStoreDayAttendance(any(), any());
        verify(leaveRequestRepository, never()).countByEmployee_Store_IdAndStatus(any(), any());
        verify(workAvailabilityRepository, never()).findPendingRegistrationIdentities(any(), any());
        verify(shiftChangeRequestRepository, never()).countPendingForManager(any());
    }

    @Test
    void managerDashboardUsesPublishedSlotsAndManagerQueuesForOwnStore() {
        LocalDate today = VietnamTime.today();
        Store store = Store.builder().id(4).storeName("Cà phê Nguyễn Huệ").status(StoreStatus.ACTIVE).build();
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.countActiveStaffAtStore(4, EmployeeStatus.ACTIVE, 9, RoleName.MANAGER)).thenReturn(6L);
        when(leaveRequestRepository.countByEmployee_Store_IdAndStatus(4, ApprovalStatus.PENDING)).thenReturn(2L);
        when(shiftChangeRequestRepository.countPendingForManager(4)).thenReturn(1L);
        List<Object[]> registrations = new ArrayList<>();
        registrations.add(new Object[]{"reg-a", 1});
        registrations.add(new Object[]{"reg-a", 2});
        registrations.add(new Object[]{null, 5});
        registrations.add(new Object[]{"  ", 6});
        registrations.add(new Object[]{null, null});
        when(workAvailabilityRepository.findPendingRegistrationIdentities(4, ApprovalStatus.PENDING))
                .thenReturn(registrations);

        Shift morning = Shift.builder().id(1).shiftName("Ca sáng").startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(12, 0)).store(store).build();
        Shift evening = Shift.builder().id(2).shiftName("Ca tối").startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(22, 0)).store(store).build();
        Employee an = Employee.builder().id(11).fullName("An").store(store).build();
        Employee binh = Employee.builder().id(12).fullName("Bình").store(store).build();
        when(shiftAssignmentRepository.findPublishedAssignedForStoreOnDate(4, today, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(
                        assignment(102, evening, an, today),
                        assignment(101, morning, an, today),
                        assignment(103, morning, binh, today)));
        when(attendanceRepository.findStoreDayAttendance(4, today)).thenReturn(List.of(
                attendance(50, an, morning, today, true, 20, 15),
                attendance(51, an, evening, today, false, 0, 0),
                attendance(40, an, morning, today, false, 0, 0)));

        ManagerDashboardView view = dashboardService.buildManagerDashboard(manager());

        assertEquals("Cà phê Nguyễn Huệ", view.getStoreName());
        assertEquals("Hoạt động", view.getStoreStatusLabel());
        assertEquals("is-active", view.getStoreStatusKey());
        assertEquals(6, view.getWorkingEmployeeCount());
        assertEquals(3, view.getTodaySlotCount());
        assertEquals(2, view.getTodayEmployeeCount());
        assertEquals(1, view.getCompletedSlotCount());
        assertEquals("1/3", view.getCompletedRatioLabel());
        assertEquals(33, view.getCompletionRate());
        assertEquals(2, view.getRemainingSlotCount());
        assertEquals(1, view.getViolationSlotCount());
        assertEquals(2, view.getPendingLeaveCount());
        assertEquals(3, view.getPendingRegistrationCount());
        assertEquals(1, view.getPendingShiftChangeCount());
        assertEquals(6, view.getPendingRequestCount());
        assertFalse(view.isTasksEmpty());
        assertEquals(2, view.getTodayShifts().size());
        assertEquals("Ca sáng", view.getTodayShifts().get(0).getShiftName());
        assertEquals("07:00 – 12:00", view.getTodayShifts().get(0).getTimeLabel());
        assertEquals(2, view.getTodayShifts().get(0).getSlotCount());
        assertEquals(2, view.getTodayShifts().get(0).getEmployeeCount());
        assertEquals("Ca tối", view.getTodayShifts().get(1).getShiftName());
        assertEquals(1, view.getTodayShifts().get(1).getSlotCount());
        assertEquals(1, view.getTodayShifts().get(1).getEmployeeCount());
        verify(employeeRepository, never()).countByStore_IdAndStatusNot(any(), any());
        verify(shiftAssignmentRepository, never()).countByEmployee_Store_IdAndWorkDateAndStatus(any(), any(), any());
    }

    @Test
    void managerDashboardSkipsAttendanceWhenTheStoreHasNoPublishedSlots() {
        LocalDate today = VietnamTime.today();
        Store store = Store.builder().id(4).storeName("Store").status(StoreStatus.PRE_OPENING).build();
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.countActiveStaffAtStore(4, EmployeeStatus.ACTIVE, 9, RoleName.MANAGER)).thenReturn(0L);
        when(leaveRequestRepository.countByEmployee_Store_IdAndStatus(4, ApprovalStatus.PENDING)).thenReturn(0L);
        when(shiftChangeRequestRepository.countPendingForManager(4)).thenReturn(0L);
        when(workAvailabilityRepository.findPendingRegistrationIdentities(4, ApprovalStatus.PENDING)).thenReturn(List.of());
        when(shiftAssignmentRepository.findPublishedAssignedForStoreOnDate(4, today, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of());

        ManagerDashboardView view = dashboardService.buildManagerDashboard(manager());

        assertEquals("Chuẩn bị khai trương", view.getStoreStatusLabel());
        assertEquals("is-opening", view.getStoreStatusKey());
        assertTrue(view.isAttendanceEmpty());
        assertTrue(view.isScheduleEmpty());
        assertTrue(view.isTasksEmpty());
        assertNull(view.getCompletionRate());
        assertEquals("0/0", view.getCompletedRatioLabel());
        verify(attendanceRepository, never()).findStoreDayAttendance(any(), any());
    }

    @Test
    void staffDashboardCountsOnlyThisEmployeesConfirmedSlots() {
        LocalDate today = VietnamTime.today();
        LocalTime now = VietnamTime.currentTime().withSecond(0).withNano(0);
        Store store = Store.builder().id(4).storeName("Cà phê Nguyễn Huệ").build();
        Employee staff = Employee.builder().id(30).fullName("Mai Trần").store(store)
                .status(EmployeeStatus.ACTIVE).certificationStatus(CertificationStatus.CERTIFIED).build();
        Employee other = Employee.builder().id(31).fullName("Người khác").store(store).build();
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(staff));
        quietStaff(30);

        Shift morning = Shift.builder().id(1).shiftName("Ca sáng").startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(12, 0)).store(store).build();
        Shift later = Shift.builder().id(2).shiftName("Ca tối").startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(22, 0)).store(store).build();
        Shift foreign = Shift.builder().id(9).shiftName("Ca của người khác").startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(12, 0)).store(store).build();
        List<ShiftAssignment> rows = new ArrayList<>();
        rows.add(assignment(1, foreign, other, today.plusDays(1)));
        ShiftAssignment cancelled = assignment(2, morning, staff, today.plusDays(1));
        cancelled.setStatus(AssignmentStatus.CANCELLED);
        rows.add(cancelled);
        ShiftAssignment draft = assignment(3, morning, staff, today.plusDays(2));
        draft.setIsPublished(false);
        rows.add(draft);
        ShiftAssignment tomorrow = assignment(4, morning, staff, today.plusDays(1));
        rows.add(tomorrow);
        ShiftAssignment publishedByDefault = assignment(5, later, staff, today.plusDays(3));
        publishedByDefault.setIsPublished(null);
        rows.add(publishedByDefault);
        if (now.isAfter(LocalTime.of(0, 10))) {
            rows.add(assignment(6, Shift.builder().id(3).shiftName("Ca đã bắt đầu").startTime(LocalTime.of(0, 0))
                    .endTime(LocalTime.of(1, 0)).store(store).build(), staff, today));
        }
        boolean laterToday = now.isBefore(LocalTime.of(17, 0));
        if (laterToday) {
            rows.add(assignment(7, later, staff, today));
        }
        when(shiftAssignmentRepository.findAssignmentsForAttendance(
                eq(30), eq(today), eq(today.plusDays(7)), eq(AssignmentStatus.ASSIGNED))).thenReturn(rows);

        StaffDashboardView view = dashboardService.buildStaffDashboard(staffUser(staff));

        assertEquals("Xin chào, Mai Trần.", view.getGreeting());
        assertEquals("Cà phê Nguyễn Huệ", view.getStoreName());
        int expectedToday = (now.isAfter(LocalTime.of(0, 10)) ? 1 : 0) + (laterToday ? 1 : 0);
        assertEquals(expectedToday, view.getTodaySlotCount());
        assertEquals(2, view.getUpcomingSlotCount());
        assertTrue(view.getUpcomingWindowLabel().contains("không gồm hôm nay"));
        assertTrue(view.getUpcomingShifts().stream().noneMatch(item -> "Ca của người khác".equals(item.getShiftName())
                || item.getShiftName().equals("Ca đã bắt đầu")));
        assertTrue(view.getUpcomingShifts().stream().anyMatch(item -> "Ca sáng".equals(item.getShiftName())
                && "Đã phân công".equals(item.getStatusLabel())
                && "Cà phê Nguyễn Huệ".equals(item.getStoreName())));
        if (laterToday) {
            assertTrue(view.getUpcomingShifts().stream().anyMatch(item -> today.equals(item.getWorkDate())));
        }
        assertTrue(view.isCanRegister());
        verify(shiftAssignmentRepository).findAssignmentsForAttendance(eq(30), any(), any(), any());
        verify(shiftAssignmentRepository, never()).findAssignmentsForAttendance(eq(31), any(), any(), any());
    }

    @Test
    void staffDashboardKeepsPersonalQueuesSeparateFromRegistrations() {
        Employee staff = Employee.builder().id(30).fullName("Mai").status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.NOTCERTIFIED).build();
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(staff));
        quietStaff(30);
        when(leaveRequestRepository.countByEmployee_IdAndStatus(30, ApprovalStatus.PENDING)).thenReturn(2L);
        when(shiftChangeRequestRepository.countByEmployee_IdAndStatus(30, ApprovalStatus.PENDING)).thenReturn(1L);
        when(shiftChangeRequestRepository.findIncomingRequestsForEmployee(30))
                .thenReturn(List.of(new ShiftChangeRequest(), new ShiftChangeRequest()));
        LocalDate from = LocalDate.of(2026, 11, 2);
        LocalDate to = LocalDate.of(2026, 11, 8);
        when(workAvailabilityRepository.findVisibleByEmployee(eq(30), any(), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of(
                        availability(1, "reg-a", ApprovalStatus.PENDING, from, to),
                        availability(2, "reg-a", ApprovalStatus.PENDING, from, to),
                        availability(3, "reg-b", ApprovalStatus.APPROVED, from.plusDays(7), to.plusDays(7)),
                        availability(4, "reg-c", ApprovalStatus.REJECTED, from, to),
                        availability(5, null, ApprovalStatus.PENDING, from, to)));
        when(staffAvailabilityService.registrationBlockedMessage(any()))
                .thenReturn("Bạn chưa có chứng chỉ nên không thể đăng ký ca làm việc.");

        StaffDashboardView view = dashboardService.buildStaffDashboard(staffUser(staff));

        assertEquals(2, view.getPendingRegistrationCount());
        assertEquals(1, view.getApprovedRegistrationCount());
        assertEquals(3, view.getSubmittedRegistrationCount());
        assertEquals("Kỳ 02/11/2026 – 08/11/2026", view.getRegistrationPeriodLabel());
        assertEquals(2, view.getPendingLeaveCount());
        assertEquals(1, view.getPendingShiftChangeCount());
        assertEquals(2, view.getIncomingShiftChangeCount());
        assertEquals(5, view.getPendingPersonalCount());
        assertFalse(view.isPersonalEmpty());
        assertFalse(view.isCanRegister());
        assertTrue(view.getRegisterBlockedMessage().contains("chưa có chứng chỉ"));
    }

    @Test
    void staffDashboardDoesNotTreatAFutureSlotAsLate() {
        Employee staff = Employee.builder().id(30).fullName("Mai").status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED).build();
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(staff));
        quietStaff(30);
        when(attendanceService.getClock(any())).thenReturn(AttendanceClockView.builder()
                .slots(List.of(
                        AttendanceClockView.Slot.builder()
                                .shiftName("Ca sáng")
                                .scheduleLabel("07:00 – 12:00")
                                .statusKey("upcoming")
                                .statusLabel("Chưa đến giờ")
                                .checkInLabel("—")
                                .checkOutLabel("—")
                                .build(),
                        AttendanceClockView.Slot.builder()
                                .shiftName("Ca chiều")
                                .scheduleLabel("12:00 – 17:00")
                                .statusKey("done")
                                .statusLabel("Đã hoàn thành")
                                .checkInLabel("12:10")
                                .checkOutLabel("16:40")
                                .build()))
                .build());
        when(managerDayAttendanceService.getOwnDay(any(), any())).thenReturn(StaffDayAttendanceView.builder()
                .slots(List.of(
                        StaffDayAttendanceView.Slot.builder()
                                .shiftName("Ca sáng")
                                .scheduleLabel("07:00 – 12:00")
                                .late(true)
                                .lateLabel("10 phút")
                                .workedLabel("—")
                                .build(),
                        StaffDayAttendanceView.Slot.builder()
                                .shiftName("Ca chiều")
                                .scheduleLabel("12:00 – 17:00")
                                .early(true)
                                .earlyLabel("20 phút")
                                .workedLabel("4 giờ 30 phút")
                                .build()))
                .build());

        StaffDashboardView view = dashboardService.buildStaffDashboard(staffUser(staff));

        assertEquals(2, view.getTodayAttendance().size());
        assertEquals("Chưa đến giờ", view.getTodayAttendance().get(0).getStatusLabel());
        assertNull(view.getTodayAttendance().get(0).getLateNote());
        assertNull(view.getTodayAttendance().get(0).getWorkedLabel());
        assertEquals("Đã hoàn thành", view.getTodayAttendance().get(1).getStatusLabel());
        assertEquals("Về sớm · 20 phút", view.getTodayAttendance().get(1).getEarlyNote());
        assertEquals("4 giờ 30 phút", view.getTodayAttendance().get(1).getWorkedLabel());
        assertEquals("12:10", view.getTodayAttendance().get(1).getCheckInLabel());
        assertEquals("16:40", view.getTodayAttendance().get(1).getCheckOutLabel());
    }

    @Test
    void staffDashboardShowsPerSkillCertificatesAndApprovedClassesOnly() {
        Employee staff = Employee.builder().id(30).fullName("Mai").status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED).build();
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(staff));
        quietStaff(30);
        when(trainingService.listCurrentSkillStatuses(30)).thenReturn(List.of(
                EmployeeSkillStatusLine.builder()
                        .skillName("Pha chế")
                        .certificationLabel("CERTIFIED - Pha chế")
                        .latestResultLabel("PASS")
                        .build(),
                EmployeeSkillStatusLine.builder()
                        .skillName("Pha máy")
                        .certificationLabel("NOTCERTIFIED - Pha máy")
                        .latestResultLabel("NOT PASS")
                        .build()));
        when(trainingService.listClassesForEmployee(any())).thenReturn(List.of(
                EmployeeTrainingClassResponse.builder()
                        .className("Lớp pha chế")
                        .dateLabel("27/10/2026")
                        .placeLabel("Cà phê Nguyễn Huệ")
                        .participationStatus("Sắp diễn ra")
                        .ended(false)
                        .build(),
                EmployeeTrainingClassResponse.builder()
                        .className("Lớp đã kết thúc")
                        .ended(true)
                        .build()));

        StaffDashboardView view = dashboardService.buildStaffDashboard(staffUser(staff));

        assertTrue(view.isCertified());
        assertEquals("Đã chứng nhận", view.getCertificationLabel());
        assertEquals(2, view.getSkills().size());
        assertEquals("Pha chế", view.getSkills().get(0).getSkillName());
        assertEquals("CERTIFIED - Pha chế", view.getSkills().get(0).getCertificationLabel());
        assertEquals(1, view.getUpcomingClasses().size());
        assertEquals("Lớp pha chế", view.getUpcomingClasses().get(0).getClassName());
    }

    @Test
    void staffWithoutAnEmployeeDoesNotQueryAnotherPersonsData() {
        StaffDashboardView view = dashboardService.buildStaffDashboard(staffUser(null));

        assertEquals("Xin chào, tuanth.", view.getGreeting());
        assertEquals(0, view.getTodaySlotCount());
        assertEquals(0, view.getUpcomingSlotCount());
        assertEquals(0, view.getPendingRegistrationCount());
        assertEquals(0, view.getPendingPersonalCount());
        assertFalse(view.isCanRegister());
        assertFalse(view.isCertificationKnown());
        assertTrue(view.getUpcomingShifts().isEmpty());
        verify(shiftAssignmentRepository, never()).findAssignmentsForAttendance(any(), any(), any(), any());
        verify(workAvailabilityRepository, never()).findVisibleByEmployee(any(), any(), any());
        verify(attendanceService, never()).getClock(any());
        verify(trainingService, never()).listClassesForEmployee(any());
        verify(employeeRepository, never()).findByIdWithStore(any());
    }

    private void quietStaff(int employeeId) {
        lenient().when(shiftAssignmentRepository.findAssignmentsForAttendance(
                eq(employeeId), any(), any(), eq(AssignmentStatus.ASSIGNED))).thenReturn(List.of());
        lenient().when(leaveRequestRepository.countByEmployee_IdAndStatus(employeeId, ApprovalStatus.PENDING)).thenReturn(0L);
        lenient().when(shiftChangeRequestRepository.countByEmployee_IdAndStatus(employeeId, ApprovalStatus.PENDING)).thenReturn(0L);
        lenient().when(shiftChangeRequestRepository.findIncomingRequestsForEmployee(employeeId)).thenReturn(List.of());
        lenient().when(workAvailabilityRepository.findVisibleByEmployee(eq(employeeId), any(), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of());
        lenient().when(attendanceService.getClock(any())).thenReturn(AttendanceClockView.builder().slots(List.of()).build());
        lenient().when(managerDayAttendanceService.getOwnDay(any(), any()))
                .thenReturn(StaffDayAttendanceView.builder().slots(List.of()).build());
        lenient().when(trainingService.listCurrentSkillStatuses(employeeId)).thenReturn(List.of());
        lenient().when(trainingService.listClassesForEmployee(any())).thenReturn(List.of());
        lenient().when(staffAvailabilityService.registrationBlockedMessage(any())).thenReturn(null);
    }

    private WorkAvailability availability(int id, String key, ApprovalStatus status, LocalDate from, LocalDate to) {
        return WorkAvailability.builder()
                .id(id)
                .registrationKey(key)
                .status(status)
                .validFrom(from)
                .validTo(to)
                .build();
    }

    private AuthenticatedUser staffUser(Employee employee) {
        User user = User.builder()
                .id(40)
                .username("tuanth")
                .passwordHash("hash")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .employee(employee)
                .isActive(true)
                .build();
        return AuthenticatedUser.from(user);
    }

    private AuthenticatedUser manager() {
        Employee employee = Employee.builder().id(9).fullName("Lan").status(EmployeeStatus.ACTIVE).build();
        User user = User.builder()
                .id(20)
                .username("lan")
                .passwordHash("hash")
                .role(Role.builder().id(2).roleName(RoleName.MANAGER).build())
                .employee(employee)
                .isActive(true)
                .build();
        return AuthenticatedUser.from(user);
    }

    private ShiftAssignment assignment(int id, Shift shift, Employee employee, LocalDate workDate) {
        return ShiftAssignment.builder()
                .id(id)
                .shift(shift)
                .employee(employee)
                .workDate(workDate)
                .status(AssignmentStatus.ASSIGNED)
                .isPublished(true)
                .build();
    }

    private Attendance attendance(int id, Employee employee, Shift shift, LocalDate workDate,
                                  boolean checkedOut, int late, int early) {
        return Attendance.builder()
                .id(id)
                .employee(employee)
                .shift(shift)
                .workDate(workDate)
                .checkInTime(workDate.atTime(8, 0))
                .checkOutTime(checkedOut ? workDate.atTime(12, 0) : null)
                .lateMinutes(late)
                .earlyLeaveMinutes(early)
                .status(checkedOut ? AttendanceStatus.LATE_AND_EARLY : AttendanceStatus.ON_TIME)
                .build();
    }

    private AuthenticatedUser admin() {
        User user = User.builder()
                .id(1)
                .username("admin")
                .passwordHash("hash")
                .role(Role.builder().id(1).roleName(RoleName.ADMIN).build())
                .isActive(true)
                .build();
        return AuthenticatedUser.from(user);
    }
}
