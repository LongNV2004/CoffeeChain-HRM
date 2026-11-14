package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;

import com.example.coffee_hrm.dto.request.AttendanceNetwork;
import com.example.coffee_hrm.dto.response.AttendanceClockView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendancePolicyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 10, 4);
    private static final BigDecimal LAT = new BigDecimal("21.0285110");
    private static final BigDecimal LNG = new BigDecimal("105.8541670");
    private static final String STORE_IP = "203.113.10.20";
    private static final String OTHER_IP = "14.160.5.6";

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private AttendancePhotoStore photoStore;

    private AttendancePolicyProperties policy;
    private AttendanceServiceImpl attendanceService;
    private Store store;
    private Employee staffEmployee;
    private AuthenticatedUser staffUser;
    private Shift morning;
    private ShiftAssignment morningAssignment;

    @BeforeEach
    void setUp() {
        policy = new AttendancePolicyProperties();
        policy.setCheckInLeadMinutes(60);
        policy.setLateThresholdMinutes(20);
        attendanceService = new AttendanceServiceImpl(
                attendanceRepository,
                shiftAssignmentRepository,
                employeeRepository,
                storeRepository,
                photoStore,
                policy);

        store = Store.builder().id(1).storeName("Cửa hàng 1").latitude(LAT).longitude(LNG).build();
        lenient().when(storeRepository.findAllowedIps(1)).thenReturn(List.of(STORE_IP));
        staffEmployee = Employee.builder()
                .id(20).fullName("Trần Văn Tuấn").store(store).status(EmployeeStatus.ACTIVE).build();
        staffUser = AuthenticatedUser.from(buildUser(3, "tuanth", RoleName.STAFF, staffEmployee));
        morning = Shift.builder()
                .id(1).shiftName("Ca sáng").store(store)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .build();
        morningAssignment = ShiftAssignment.builder()
                .id(9).employee(staffEmployee).shift(morning).workDate(WORK_DATE)
                .status(AssignmentStatus.ASSIGNED).isPublished(true)
                .build();
    }

    @Test
    void checkInFromTheStoreNetworkCreatesOneRecord() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(7, 30));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        Attendance saved = captor.getValue();
        assertEquals(20, saved.getEmployee().getId());
        assertEquals(WORK_DATE, saved.getWorkDate());
        assertEquals(LocalTime.of(8, 0), saved.getScheduledStartTime());
        assertEquals(LocalTime.of(17, 0), saved.getScheduledEndTime());
        // Không còn ghi tọa độ GPS cho bản ghi mới.
        assertNull(saved.getCheckInLatitude());
        assertNull(saved.getCheckInLongitude());
        assertEquals(0, saved.getLateMinutes());
        assertEquals(AttendanceStatus.PRESENT, saved.getStatus());
        assertNull(saved.getCheckOutTime());
    }

    @Test
    void checkInWithPhotoStoresTheFileName() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Attendance attendance = invocation.getArgument(0);
            attendance.setId(101);
            return attendance;
        });
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        when(photoStore.save(101, "in", jpeg)).thenReturn("101-in.jpg");

        attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(7, 30), jpeg);

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).save(captor.capture());
        assertEquals("101-in.jpg", captor.getValue().getCheckInPhotoName());
    }

    @Test
    void checkInFromAnotherNetworkIsRejectedWithoutSaving() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, otherNetwork(), WORK_DATE.atTime(7, 30)));

        assertTrue(ex.getMessage().startsWith(AttendanceServiceImpl.IP_NOT_ALLOWED));
        assertTrue(ex.getMessage().contains(OTHER_IP));
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInMatchesAnyOfTheStoreIpsIncludingEquivalentIpv6Spelling() {
        when(storeRepository.findAllowedIps(1)).thenReturn(List.of("113.161.1.1", "2001:db8::10"));
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(staffUser, new AttendanceNetwork("2001:0db8:0:0:0:0:0:0010"), WORK_DATE.atTime(7, 30));

        verify(attendanceRepository).saveAndFlush(any());
    }

    @Test
    void checkInRejectsAnUnknownClientIp() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, new AttendanceNetwork(null), WORK_DATE.atTime(7, 30)));

        assertEquals(AttendanceServiceImpl.IP_UNKNOWN, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInIsBlockedWhenTheStoreHasNoIpConfigured() {
        when(storeRepository.findAllowedIps(1)).thenReturn(List.of());
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(7, 30)));

        assertEquals(AttendanceServiceImpl.STORE_IP_MISSING, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInIsBlockedWhenTheStoreHasOnlyInvalidIpEntries() {
        when(storeRepository.findAllowedIps(1)).thenReturn(List.of("not-an-ip", "cuahang.local"));
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(7, 30)));

        assertEquals(AttendanceServiceImpl.STORE_IP_MISSING, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInRejectsWhenTooEarly() {
        stubShift(List.of(morningAssignment));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(6, 0)));

        assertEquals(AttendanceServiceImpl.TOO_EARLY, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInRejectsWhenNoShift() {
        stubShift(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(8, 0)));

        assertEquals(AttendanceServiceImpl.NO_SHIFT, ex.getMessage());
        verify(storeRepository, never()).findById(any());
    }

    @Test
    void checkInRejectsSecondCheckInForSameShift() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.of(Attendance.builder().id(5).employee(staffEmployee).build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(9, 0)));

        assertEquals(AttendanceServiceImpl.ALREADY_IN, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInRecordsLateOnlyFromTwentyMinutes() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(8, 19));
        ArgumentCaptor<Attendance> early = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(early.capture());
        assertEquals(0, early.getValue().getLateMinutes());
        assertEquals(AttendanceStatus.PRESENT, early.getValue().getStatus());

        attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(8, 20));
        ArgumentCaptor<Attendance> late = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, org.mockito.Mockito.times(2)).saveAndFlush(late.capture());
        Attendance lateRecord = late.getAllValues().get(1);
        assertEquals(20, lateRecord.getLateMinutes());
        assertEquals(AttendanceStatus.LATE, lateRecord.getStatus());
    }

    @Test
    void checkOutRightAfterCheckInIsAllowedAndRecordsTheEarlyLeave() {
        Attendance open = openAttendance(0);
        open.setCheckInTime(WORK_DATE.atTime(8, 0));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        // Không còn giới hạn 2 tiếng: Check-out ngay sau khi Check-in.
        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(8, 5));

        assertEquals(WORK_DATE.atTime(8, 5), open.getCheckOutTime());
        // Ca kết thúc 17:00 nên vẫn ghi số phút về sớm như trước.
        assertEquals(535, open.getEarlyLeaveMinutes());
        verify(attendanceRepository).save(open);
    }

    @Test
    void checkOutIsAllowedTwoHoursAfterShiftStart() {
        Attendance open = openAttendance(0);
        open.setCheckInTime(WORK_DATE.atTime(7, 0));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(10, 0));

        assertEquals(WORK_DATE.atTime(10, 0), open.getCheckOutTime());
        assertEquals(180, open.getWorkingMinutes());
        verify(attendanceRepository).save(open);
    }

    @Test
    void checkOutUpdatesTheSameRecordAndStoresEarlyLeave() {
        Attendance open = openAttendance(0);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(16, 30));

        assertEquals(WORK_DATE.atTime(16, 30), open.getCheckOutTime());
        assertNull(open.getCheckOutLatitude());
        assertNull(open.getCheckOutLongitude());
        assertEquals(510, open.getWorkingMinutes());
        assertEquals(30, open.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.EARLY_LEAVE, open.getStatus());
        verify(attendanceRepository).save(open);
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkOutOnTimeHasNoEarlyLeave() {
        Attendance open = openAttendance(0);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(17, 0));

        assertEquals(540, open.getWorkingMinutes());
        assertEquals(0, open.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.PRESENT, open.getStatus());
    }

    @Test
    void checkOutTwoHoursAfterShiftEndIsLateCheckout() {
        Attendance open = openAttendance(0);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(18, 59));
        assertEquals(AttendanceStatus.PRESENT, open.getStatus());

        open.setCheckOutTime(null);
        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(19, 0));

        assertEquals(AttendanceStatus.LATE_CHECKOUT, open.getStatus());
        assertEquals(0, open.getEarlyLeaveMinutes());
    }

    @Test
    void lateArrivalAndLateCheckoutAreBothRecorded() {
        Attendance open = openAttendance(30);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(19, 0));

        assertEquals(AttendanceStatus.LATE_AND_LATE_CHECKOUT, open.getStatus());
    }

    @Test
    void checkOutMarksLateAndEarly() {
        Attendance open = openAttendance(30);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(16, 30));

        assertEquals(AttendanceStatus.LATE_AND_EARLY, open.getStatus());
        assertEquals(30, open.getLateMinutes());
        assertEquals(30, open.getEarlyLeaveMinutes());
    }

    @Test
    void checkOutStillRequiresTheStoreNetworkAndAnOpenRecord() {
        Attendance open = openAttendance(0);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(staffUser, otherNetwork(), WORK_DATE.atTime(8, 30)));

        assertTrue(ex.getMessage().startsWith(AttendanceServiceImpl.IP_NOT_ALLOWED));
        assertNull(open.getCheckOutTime());
    }

    @Test
    void checkOutFromAnotherNetworkDoesNotSetCheckOutTime() {
        Attendance open = openAttendance(0);
        open.setCheckInLatitude(LAT);
        open.setCheckInLongitude(LNG);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(staffUser, otherNetwork(), WORK_DATE.atTime(16, 30)));

        assertTrue(ex.getMessage().startsWith(AttendanceServiceImpl.IP_NOT_ALLOWED));
        assertNull(open.getCheckOutTime());
        // Tọa độ cũ của bản ghi được giữ nguyên.
        assertEquals(LAT, open.getCheckInLatitude());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void checkOutUsesTheLatestStoreIpList() {
        Attendance open = openAttendance(0);
        open.setCheckInLatitude(LAT);
        open.setCheckInLongitude(LNG);
        // Admin vừa đổi mạng của cửa hàng: Check-out kiểm tra theo danh sách IP mới nhất.
        when(storeRepository.findAllowedIps(1)).thenReturn(List.of("27.72.100.1"));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, new AttendanceNetwork("27.72.100.1"), WORK_DATE.atTime(17, 0));

        assertEquals(LAT, open.getCheckInLatitude());
        assertNull(open.getCheckOutLatitude());
        assertEquals(WORK_DATE.atTime(17, 0), open.getCheckOutTime());
    }

    @Test
    void checkOutRejectsWhenAlreadyCheckedOut() {
        Attendance closed = openAttendance(0);
        closed.setCheckOutTime(WORK_DATE.atTime(17, 0));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of());
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.of(closed));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(17, 10)));

        assertEquals(AttendanceServiceImpl.ALREADY_OUT, ex.getMessage());
    }

    @Test
    void checkOutRejectsWhenNotCheckedIn() {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of());
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(9, 0)));

        assertEquals(AttendanceServiceImpl.NOT_CHECKED_IN, ex.getMessage());
    }

    @Test
    void secondShiftOnTheSameDayCreatesAnotherAttendance() {
        Shift afternoon = Shift.builder()
                .id(2).shiftName("Ca chiều").store(store)
                .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(18, 0))
                .build();
        ShiftAssignment afternoonAssignment = ShiftAssignment.builder()
                .id(10).employee(staffEmployee).shift(afternoon).workDate(WORK_DATE)
                .status(AssignmentStatus.ASSIGNED).isPublished(true)
                .build();
        stubShift(List.of(morningAssignment, afternoonAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.of(Attendance.builder().id(5).build()));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 2, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(13, 30));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        assertEquals(2, captor.getValue().getShift().getId());
        assertEquals(LocalTime.of(14, 0), captor.getValue().getScheduledStartTime());
    }

    @Test
    void duplicateInsertIsReportedAsAlreadyCheckedIn() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(8, 0)));

        assertEquals(AttendanceServiceImpl.ALREADY_IN, ex.getMessage());
    }

    @Test
    void managerChecksInWithoutAShiftAgainstFixedHoursOnTime() {
        Attendance saved = managerCheckInAt(WORK_DATE.atTime(8, 45));

        assertNull(saved.getShift());
        assertEquals(LocalTime.of(8, 30), saved.getScheduledStartTime());
        assertEquals(LocalTime.of(17, 30), saved.getScheduledEndTime());
        assertEquals("Giờ hành chính", saved.getScheduledShiftName());
        assertEquals(0, saved.getLateMinutes());
        assertEquals(0, saved.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.ON_TIME, saved.getStatus());
        assertEquals(WORK_DATE, saved.getWorkDate());
        verify(shiftAssignmentRepository, never()).findAssignmentsForAttendance(any(), any(), any(), any());
    }

    @Test
    void managerCheckInAfterGraceIsLate() {
        Attendance saved = managerCheckInAt(WORK_DATE.atTime(9, 10));

        assertEquals(40, saved.getLateMinutes());
        assertEquals(AttendanceStatus.LATE, saved.getStatus());
    }

    @Test
    void managerCheckInOutsideFixedHoursIsRejected() {
        Employee managerEmployee = newManagerEmployee();
        AuthenticatedUser manager = managerUser(managerEmployee);
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of());
        when(attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(10, WORK_DATE)).thenReturn(List.of());

        BusinessException early = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(manager, storeNetwork(), WORK_DATE.atTime(7, 29)));
        BusinessException closed = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(manager, storeNetwork(), WORK_DATE.atTime(17, 30)));

        assertEquals(AttendanceServiceImpl.TOO_EARLY, early.getMessage());
        assertEquals(AttendanceServiceImpl.MANAGER_CLOSED, closed.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void managerCheckInFromAnotherNetworkIsRejected() {
        Employee managerEmployee = newManagerEmployee();
        AuthenticatedUser manager = managerUser(managerEmployee);
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of());
        when(attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(10, WORK_DATE)).thenReturn(List.of());
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(manager, otherNetwork(), WORK_DATE.atTime(8, 30)));

        assertTrue(ex.getMessage().startsWith(AttendanceServiceImpl.IP_NOT_ALLOWED));
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void managerCheckoutBeforeEndIsEarlyLeaveAndOnScheduleIsOnTime() {
        Employee managerEmployee = newManagerEmployee();
        AuthenticatedUser manager = managerUser(managerEmployee);
        Attendance early = managerWorkday(WORK_DATE.atTime(8, 30), 0);
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of(early));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(manager, storeNetwork(), WORK_DATE.atTime(16, 0));

        assertEquals(90, early.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.EARLY_LEAVE, early.getStatus());

        Attendance onTime = managerWorkday(WORK_DATE.atTime(8, 30), 0);
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of(onTime));
        attendanceService.checkOut(manager, storeNetwork(), WORK_DATE.atTime(17, 35));

        assertEquals(0, onTime.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.ON_TIME, onTime.getStatus());

        Attendance lateAndEarly = managerWorkday(WORK_DATE.atTime(9, 0), 30);
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of(lateAndEarly));
        attendanceService.checkOut(manager, storeNetwork(), WORK_DATE.atTime(17, 0));

        assertEquals(AttendanceStatus.LATE_AND_EARLY, lateAndEarly.getStatus());
    }

    @Test
    void managerWithoutAttendanceIsMarkedAbsentAfterWorkingHours() {
        Employee managerEmployee = newManagerEmployee();
        managerEmployee.setStatus(EmployeeStatus.ACTIVE);
        store.setStatus(com.example.coffee_hrm.common.enums.StoreStatus.ACTIVE);
        when(storeRepository.findAllWithManager()).thenReturn(List.of(store));
        when(attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(10, WORK_DATE)).thenReturn(List.of());
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.markAbsences(WORK_DATE, WORK_DATE.atTime(17, 0));
        verify(attendanceRepository, never()).saveAndFlush(any());

        attendanceService.markAbsences(WORK_DATE, WORK_DATE.atTime(18, 5));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        Attendance absent = captor.getValue();
        assertEquals(AttendanceStatus.ABSENT, absent.getStatus());
        assertNull(absent.getShift());
        assertNull(absent.getCheckInTime());
        assertEquals(10, absent.getEmployee().getId());
        assertEquals(LocalTime.of(8, 30), absent.getScheduledStartTime());
        assertEquals(LocalTime.of(17, 30), absent.getScheduledEndTime());
    }

    @Test
    void managerWithAttendanceIsNotMarkedAbsent() {
        Employee managerEmployee = newManagerEmployee();
        managerEmployee.setStatus(EmployeeStatus.ACTIVE);
        store.setStatus(com.example.coffee_hrm.common.enums.StoreStatus.ACTIVE);
        when(storeRepository.findAllWithManager()).thenReturn(List.of(store));
        when(attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(10, WORK_DATE))
                .thenReturn(List.of(managerWorkday(WORK_DATE.atTime(8, 30), 0)));

        attendanceService.markAbsences(WORK_DATE, WORK_DATE.atTime(18, 5));

        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    private Attendance managerCheckInAt(LocalDateTime at) {
        Employee managerEmployee = newManagerEmployee();
        AuthenticatedUser manager = managerUser(managerEmployee);
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of());
        when(attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(10, at.toLocalDate())).thenReturn(List.of());
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(manager, storeNetwork(), at);

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Attendance managerWorkday(LocalDateTime checkIn, int lateMinutes) {
        Attendance attendance = managerAttendance(checkIn.toLocalDate(), checkIn, null);
        attendance.setScheduledShiftName("Giờ hành chính");
        attendance.setScheduledStartTime(LocalTime.of(8, 30));
        attendance.setScheduledEndTime(LocalTime.of(17, 30));
        attendance.setLateMinutes(lateMinutes);
        attendance.setStatus(lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.ON_TIME);
        return attendance;
    }

    @Test
    void managerCannotCheckInTwiceOnTheSameDay() {
        Employee managerEmployee = newManagerEmployee();
        AuthenticatedUser manager = managerUser(managerEmployee);
        Attendance closed = managerAttendance(WORK_DATE, WORK_DATE.atTime(8, 0), WORK_DATE.atTime(17, 0));
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of());
        when(attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(10, WORK_DATE)).thenReturn(List.of(closed));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(manager, storeNetwork(), WORK_DATE.atTime(18, 0)));

        assertEquals(AttendanceServiceImpl.MANAGER_ALREADY_IN, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void managerCheckoutAfterMidnightUpdatesTheOpenRecord() {
        Employee managerEmployee = newManagerEmployee();
        AuthenticatedUser manager = managerUser(managerEmployee);
        Attendance open = managerAttendance(WORK_DATE.minusDays(1), WORK_DATE.minusDays(1).atTime(22, 0), null);
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(manager, storeNetwork(), WORK_DATE.atTime(1, 0)));
        assertEquals(AttendanceServiceImpl.MANAGER_OPEN_RECORD, blocked.getMessage());

        attendanceService.checkOut(manager, storeNetwork(), WORK_DATE.atTime(1, 0));

        assertEquals(WORK_DATE.atTime(1, 0), open.getCheckOutTime());
        assertEquals(180, open.getWorkingMinutes());
        assertEquals(0, open.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.PRESENT, open.getStatus());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void managerSecondCheckoutIsRejected() {
        Employee managerEmployee = newManagerEmployee();
        AuthenticatedUser manager = managerUser(managerEmployee);
        Attendance closed = managerAttendance(WORK_DATE, WORK_DATE.atTime(8, 0), WORK_DATE.atTime(12, 0));
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of());
        when(attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(10, WORK_DATE)).thenReturn(List.of(closed));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(manager, storeNetwork(), WORK_DATE.atTime(13, 0)));

        assertEquals(AttendanceServiceImpl.MANAGER_ALREADY_OUT, ex.getMessage());
        assertEquals(WORK_DATE.atTime(12, 0), closed.getCheckOutTime());
    }

    @Test
    void simultaneousManagerInsertIsRejectedAsAlreadyCheckedIn() {
        Employee managerEmployee = newManagerEmployee();
        AuthenticatedUser manager = managerUser(managerEmployee);
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(attendanceRepository.findOpenByEmployeeId(10)).thenReturn(List.of());
        when(attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(10, WORK_DATE)).thenReturn(List.of());
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(manager, storeNetwork(), WORK_DATE.atTime(9, 0)));

        assertEquals(AttendanceServiceImpl.MANAGER_ALREADY_IN, ex.getMessage());
    }

    @Test
    void endedShiftWithoutAttendanceIsMarkedAbsent() {
        when(shiftAssignmentRepository.findPublishedAssignedOnDate(WORK_DATE, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.markAbsences(WORK_DATE, WORK_DATE.atTime(18, 0));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        assertEquals(AttendanceStatus.ABSENT, captor.getValue().getStatus());
        assertEquals(LocalTime.of(8, 0), captor.getValue().getScheduledStartTime());
        assertNull(captor.getValue().getCheckInTime());
    }

    @Test
    void runningShiftIsNotMarkedAbsent() {
        when(shiftAssignmentRepository.findPublishedAssignedOnDate(eq(WORK_DATE), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(List.of(morningAssignment));

        attendanceService.markAbsences(WORK_DATE, WORK_DATE.atTime(10, 0));

        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInTargetsTheRequestedSlotWhenAnotherSlotIsAlsoEligible() {
        Shift afternoon = Shift.builder()
                .id(2).shiftName("Ca chiều").store(store)
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(17, 0))
                .build();
        ShiftAssignment afternoonAssignment = ShiftAssignment.builder()
                .id(10).employee(staffEmployee).shift(afternoon).workDate(WORK_DATE)
                .status(AssignmentStatus.ASSIGNED).isPublished(true)
                .build();
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftAssignmentRepository.findByIdWithDetails(10)).thenReturn(Optional.of(afternoonAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 2, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(13, 30), null, 10);

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        Attendance saved = captor.getValue();
        assertEquals(2, saved.getShift().getId());
        assertEquals("Ca chiều", saved.getScheduledShiftName());
        assertEquals(LocalTime.of(13, 0), saved.getScheduledStartTime());
        assertEquals(LocalTime.of(17, 0), saved.getScheduledEndTime());
        assertEquals(30, saved.getLateMinutes());
        assertEquals(AttendanceStatus.LATE, saved.getStatus());
    }

    @Test
    void checkInRejectsASlotThatBelongsToSomeoneElse() {
        Employee other = Employee.builder().id(99).fullName("Người khác").store(store).build();
        ShiftAssignment foreign = ShiftAssignment.builder()
                .id(11).employee(other).shift(morning).workDate(WORK_DATE)
                .status(AssignmentStatus.ASSIGNED).isPublished(true)
                .build();
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftAssignmentRepository.findByIdWithDetails(11)).thenReturn(Optional.of(foreign));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(8, 0), null, 11));

        assertEquals(AttendanceServiceImpl.SLOT_FORBIDDEN, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInRejectsAnUnpublishedSlot() {
        morningAssignment.setIsPublished(false);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftAssignmentRepository.findByIdWithDetails(9)).thenReturn(Optional.of(morningAssignment));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, storeNetwork(), WORK_DATE.atTime(8, 0), null, 9));

        assertEquals(AttendanceServiceImpl.NO_SHIFT, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkOutClosesOnlyTheChosenSlot() {
        Shift afternoon = Shift.builder()
                .id(2).shiftName("Ca chiều").store(store)
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(17, 0))
                .build();
        ShiftAssignment afternoonAssignment = ShiftAssignment.builder()
                .id(10).employee(staffEmployee).shift(afternoon).workDate(WORK_DATE)
                .status(AssignmentStatus.ASSIGNED).isPublished(true)
                .build();
        Attendance morningOpen = openAttendance(0);
        Attendance afternoonOpen = Attendance.builder()
                .id(102).employee(staffEmployee).store(store).shift(afternoon).workDate(WORK_DATE)
                .scheduledShiftName("Ca chiều")
                .scheduledStartTime(LocalTime.of(13, 0))
                .scheduledEndTime(LocalTime.of(17, 0))
                .checkInTime(WORK_DATE.atTime(13, 10))
                .lateMinutes(0).earlyLeaveMinutes(0).status(AttendanceStatus.PRESENT)
                .build();
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftAssignmentRepository.findByIdWithDetails(10)).thenReturn(Optional.of(afternoonAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 2, WORK_DATE))
                .thenReturn(Optional.of(afternoonOpen));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, storeNetwork(), WORK_DATE.atTime(15, 30), null, 10);

        assertNull(morningOpen.getCheckOutTime());
        assertEquals(WORK_DATE.atTime(15, 30), afternoonOpen.getCheckOutTime());
        assertEquals(140, afternoonOpen.getWorkingMinutes());
        verify(attendanceRepository).save(afternoonOpen);
    }

    @Test
    void staffClockShowsShiftWorkDate() {
        Attendance open = Attendance.builder()
                .id(4)
                .employee(staffEmployee)
                .workDate(LocalDate.of(2026, 10, 10))
                .scheduledShiftName("Ca Tối")
                .scheduledStartTime(LocalTime.of(17, 0))
                .scheduledEndTime(LocalTime.of(22, 0))
                .status(AttendanceStatus.PRESENT)
                .checkInTime(LocalDateTime.of(2026, 10, 10, 17, 5))
                .build();
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(attendanceRepository.findRecentByEmployee(eq(20), any())).thenReturn(List.of());

        AttendanceClockView clock = attendanceService.getClock(staffUser);

        assertEquals("Ca Tối", clock.getShiftName());
        assertEquals("10/10/2026", clock.getWorkDateLabel());
        assertEquals("17:00", clock.getScheduledStartLabel());
        assertEquals("22:00", clock.getScheduledEndLabel());
    }

    private void stubShift(List<ShiftAssignment> assignments) {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftAssignmentRepository.findAssignmentsForAttendance(
                eq(20), eq(WORK_DATE.minusDays(1)), eq(WORK_DATE.plusDays(1)), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(assignments);
    }

    private Attendance openAttendance(int lateMinutes) {
        return Attendance.builder()
                .id(101)
                .employee(staffEmployee)
                .store(store)
                .shift(morning)
                .workDate(WORK_DATE)
                .scheduledShiftName("Ca sáng")
                .scheduledStartTime(LocalTime.of(8, 0))
                .scheduledEndTime(LocalTime.of(17, 0))
                .checkInTime(WORK_DATE.atTime(8, 0))
                .lateMinutes(lateMinutes)
                .earlyLeaveMinutes(0)
                .status(lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
                .build();
    }

    private Attendance managerAttendance(LocalDate workDate, LocalDateTime checkIn, LocalDateTime checkOut) {
        Employee managerEmployee = Employee.builder().id(10).fullName("Manager A").store(store).build();
        return Attendance.builder()
                .id(202)
                .employee(managerEmployee)
                .store(store)
                .workDate(workDate)
                .checkInTime(checkIn)
                .checkOutTime(checkOut)
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .status(AttendanceStatus.PRESENT)
                .build();
    }

    private Employee newManagerEmployee() {
        Employee managerEmployee = Employee.builder().id(10).fullName("Manager A").store(store).build();
        store.setManager(managerEmployee);
        return managerEmployee;
    }

    private AuthenticatedUser managerUser(Employee managerEmployee) {
        return AuthenticatedUser.from(buildUser(2, "manager1", RoleName.MANAGER, managerEmployee));
    }

    /** Request đến từ mạng của cửa hàng. */
    private AttendanceNetwork storeNetwork() {
        return new AttendanceNetwork(STORE_IP);
    }

    /** Request đến từ một mạng khác mạng của cửa hàng. */
    private AttendanceNetwork otherNetwork() {
        return new AttendanceNetwork(OTHER_IP);
    }

    private User buildUser(Integer id, String username, RoleName roleName, Employee employee) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("hash")
                .role(role)
                .employee(employee)
                .isActive(true)
                .build();
    }
}
