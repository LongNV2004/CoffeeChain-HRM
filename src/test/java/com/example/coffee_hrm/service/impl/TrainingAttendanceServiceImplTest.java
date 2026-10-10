package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.geo.GeoDistance;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingAttendance;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingAttendanceRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingAttendanceServiceImplTest {

    private static final BigDecimal LAT = new BigDecimal("21.0285110");
    private static final BigDecimal LNG = new BigDecimal("105.8048170");

    @Mock
    private TrainingAttendanceRepository trainingAttendanceRepository;
    @Mock
    private TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StoreRepository storeRepository;

    private TrainingAttendanceServiceImpl service;
    private Store store;
    private Store otherStore;
    private Employee staffEmployee;
    private Employee managerEmployee;
    private User trainer;
    private User otherManager;
    private AuthenticatedUser staffUser;
    private AuthenticatedUser managerUser;
    private AuthenticatedUser otherManagerUser;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        AttendancePolicyProperties policy = new AttendancePolicyProperties();
        policy.setCheckInLeadMinutes(60);
        policy.setLateThresholdMinutes(20);
        policy.setAllowEarlyCheckout(true);
        policy.setRadiusMeters(200);
        service = new TrainingAttendanceServiceImpl(
                trainingAttendanceRepository,
                trainingClassEnrollmentRepository,
                employeeRepository,
                storeRepository,
                policy);
        store = Store.builder().id(1).storeName("Cửa hàng 1").latitude(LAT).longitude(LNG).build();
        otherStore = Store.builder().id(2).storeName("Cửa hàng 2").latitude(LAT).longitude(LNG).build();
        staffEmployee = Employee.builder()
                .id(20).fullName("Trần Văn Tuấn").store(store).status(EmployeeStatus.ACTIVE).build();
        managerEmployee = Employee.builder()
                .id(10).fullName("Nguyễn Văn Quản Lý").store(store).status(EmployeeStatus.ACTIVE).build();
        trainer = buildUser(2, "manager1", RoleName.MANAGER, managerEmployee);
        otherManager = buildUser(8, "manager2", RoleName.MANAGER, managerEmployee);
        staffUser = AuthenticatedUser.from(buildUser(3, "tuanth", RoleName.STAFF, staffEmployee));
        managerUser = AuthenticatedUser.from(trainer);
        otherManagerUser = AuthenticatedUser.from(otherManager);
        today = VietnamTime.today();
    }

    @Test
    void enrolledStaffCanCheckInAnApprovedClass() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        stubStaff(trainingClass);
        stubStore();
        stubNoExistingRecord(20, 7);
        when(trainingAttendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.checkIn(staffUser, 7, here(10), today.atTime(8, 10));

        ArgumentCaptor<TrainingAttendance> captor = ArgumentCaptor.forClass(TrainingAttendance.class);
        verify(trainingAttendanceRepository).saveAndFlush(captor.capture());
        TrainingAttendance saved = captor.getValue();
        assertEquals(20, saved.getEmployee().getId());
        assertEquals(7, saved.getTrainingClass().getId());
        assertEquals(today, saved.getWorkDate());
        assertEquals(LocalTime.of(8, 0), saved.getScheduledStartTime());
        assertEquals(LocalTime.of(12, 0), saved.getScheduledEndTime());
        assertEquals(0, saved.getLateMinutes());
        assertEquals(AttendanceStatus.PRESENT, saved.getStatus());
        assertEquals(LAT, saved.getCheckInLatitude());
    }

    @Test
    void staffWithoutEnrollmentIsRejected() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(trainingAttendanceRepository.findClassForAttendance(7)).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.findByClassAndEmployee(7, 20)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(10), today.atTime(8, 0)));

        assertEquals(TrainingAttendanceServiceImpl.NOT_ASSIGNED, ex.getMessage());
        verify(trainingAttendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void trainerCanCheckInAndAnotherManagerCannot() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        stubManager(trainingClass);
        stubStore();
        stubNoExistingRecord(10, 7);
        when(trainingAttendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.checkIn(managerUser, 7, here(10), today.atTime(8, 0));
        verify(trainingAttendanceRepository).saveAndFlush(any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(otherManagerUser, 7, here(10), today.atTime(8, 0)));
        assertEquals(TrainingAttendanceServiceImpl.NOT_ASSIGNED, ex.getMessage());
    }

    @Test
    void unapprovedClassIsRejected() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        trainingClass.setStatus(TrainingClassStatus.PENDING_APPROVAL);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(trainingAttendanceRepository.findClassForAttendance(7)).thenReturn(Optional.of(trainingClass));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(10), today.atTime(8, 0)));

        assertEquals(TrainingAttendanceServiceImpl.NOT_APPROVED, ex.getMessage());
        verify(trainingAttendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void dateOutsideTheClassRangeIsRejected() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        trainingClass.setStartDate(today.plusDays(2));
        trainingClass.setEndDate(today.plusDays(3));
        stubStaff(trainingClass);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(10), today.atTime(8, 0)));

        assertEquals(TrainingAttendanceServiceImpl.OUTSIDE_DATES, ex.getMessage());
    }

    @Test
    void missingOrInvalidClassHoursAreRejected() {
        TrainingClass missing = approvedClass(7, trainer);
        missing.setEndTime(null);
        stubStaff(missing);
        BusinessException missingHours = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(10), today.atTime(8, 0)));
        assertEquals(TrainingAttendanceServiceImpl.BAD_SCHEDULE, missingHours.getMessage());

        TrainingClass backwards = approvedClass(8, trainer);
        backwards.setStartTime(LocalTime.of(12, 0));
        backwards.setEndTime(LocalTime.of(8, 0));
        stubStaff(backwards);
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 8, here(10), today.atTime(12, 0)));
        assertEquals(TrainingAttendanceServiceImpl.BAD_SCHEDULE, invalid.getMessage());
        verify(trainingAttendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkOutUpdatesOnlyTheOpenRecordOfTheSelectedClass() {
        TrainingClass first = approvedClass(7, trainer);
        TrainingClass second = approvedClass(8, trainer);
        TrainingAttendance open = openRecord(first, today.atTime(8, 0));
        TrainingAttendance other = openRecord(second, today.atTime(9, 0));
        other.setId(32);
        stubStaff(first);
        stubStore();
        when(trainingAttendanceRepository.findOpenByEmployeeAndClass(20, 7)).thenReturn(List.of(open));

        service.checkOut(staffUser, 7, here(10), today.atTime(11, 0));

        assertEquals(today.atTime(11, 0), open.getCheckOutTime());
        assertEquals(180, open.getWorkingMinutes());
        assertEquals(60, open.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.EARLY_LEAVE, open.getStatus());
        assertEquals(null, other.getCheckOutTime());
        verify(trainingAttendanceRepository).save(open);
        verify(trainingAttendanceRepository, never()).save(other);
    }

    @Test
    void checkOutRequiresAnOpenRecordAndRejectsASecondCheckOut() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        stubStaff(trainingClass);
        when(trainingAttendanceRepository.findOpenByEmployeeAndClass(20, 7)).thenReturn(List.of());
        when(trainingAttendanceRepository.findByEmployee_IdAndTrainingClass_IdAndWorkDate(20, 7, today))
                .thenReturn(Optional.empty());

        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.checkOut(staffUser, 7, here(10), today.atTime(11, 0)));
        assertEquals(TrainingAttendanceServiceImpl.NOT_CHECKED_IN, missing.getMessage());

        TrainingAttendance finished = openRecord(trainingClass, today.atTime(8, 0));
        finished.setCheckOutTime(today.atTime(12, 0));
        when(trainingAttendanceRepository.findByEmployee_IdAndTrainingClass_IdAndWorkDate(20, 7, today))
                .thenReturn(Optional.of(finished));
        BusinessException again = assertThrows(BusinessException.class,
                () -> service.checkOut(staffUser, 7, here(10), today.atTime(12, 30)));
        assertEquals(TrainingAttendanceServiceImpl.ALREADY_OUT, again.getMessage());
        verify(trainingAttendanceRepository, never()).save(any());
    }

    @Test
    void duplicateCheckInIsRejectedWhenTheUniqueKeyAlreadyExists() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        stubStaff(trainingClass);
        stubStore();
        stubNoExistingRecord(20, 7);
        when(trainingAttendanceRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(10), today.atTime(8, 0)));

        assertEquals(TrainingAttendanceServiceImpl.ALREADY_IN, ex.getMessage());
    }

    @Test
    void differentClassesOnTheSameDayAreIndependent() {
        TrainingClass first = approvedClass(7, trainer);
        TrainingClass second = approvedClass(8, trainer);
        second.setStartTime(LocalTime.of(13, 0));
        second.setEndTime(LocalTime.of(17, 0));
        stubStaff(first);
        stubStaff(second);
        stubStore();
        stubNoExistingRecord(20, 7);
        stubNoExistingRecord(20, 8);
        when(trainingAttendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.checkIn(staffUser, 7, here(10), today.atTime(8, 0));
        service.checkIn(staffUser, 8, here(10), today.atTime(13, 0));

        verify(trainingAttendanceRepository, org.mockito.Mockito.times(2)).saveAndFlush(any());
    }

    @Test
    void accuracyAndDistanceBoundariesAreEnforced() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        stubStaff(trainingClass);
        stubStore();
        stubNoExistingRecord(20, 7);
        when(trainingAttendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.checkIn(staffUser, 7, north(199, 50), today.atTime(8, 0));
        service.checkIn(staffUser, 7, north(200, 50), today.atTime(8, 5));

        BusinessException tooFar = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, north(201, 10), today.atTime(8, 0)));
        assertEquals(TrainingAttendanceServiceImpl.OUTSIDE_RADIUS, tooFar.getMessage());

        BusinessException coarse = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(50.1), today.atTime(8, 0)));
        assertEquals(TrainingAttendanceServiceImpl.LOCATION_INACCURATE, coarse.getMessage());
    }

    @Test
    void missingStoreCoordinatesAreRejected() {
        store.setLatitude(null);
        store.setLongitude(null);
        TrainingClass trainingClass = approvedClass(7, trainer);
        stubStaff(trainingClass);
        stubStore();
        stubNoExistingRecord(20, 7);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(10), today.atTime(8, 0)));

        assertEquals(TrainingAttendanceServiceImpl.STORE_LOCATION_MISSING, ex.getMessage());
        verify(trainingAttendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void centralizedClassUsesTheEmployeeStoreWhenItParticipates() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        trainingClass.setTrainingType(TrainingType.CENTRALIZED_TRAINING);
        trainingClass.setStore(null);
        trainingClass.setParticipatingStores(new LinkedHashSet<>(Set.of(store)));
        stubStaff(trainingClass);
        stubStore();
        stubNoExistingRecord(20, 7);
        when(trainingAttendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.checkIn(staffUser, 7, here(10), today.atTime(8, 0));

        ArgumentCaptor<TrainingAttendance> captor = ArgumentCaptor.forClass(TrainingAttendance.class);
        verify(trainingAttendanceRepository).saveAndFlush(captor.capture());
        assertEquals(1, captor.getValue().getStore().getId());
    }

    @Test
    void centralizedClassRejectsAnEmployeeStoreThatDoesNotParticipate() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        trainingClass.setTrainingType(TrainingType.CENTRALIZED_TRAINING);
        trainingClass.setStore(null);
        trainingClass.setParticipatingStores(new LinkedHashSet<>(Set.of(otherStore)));
        stubStaff(trainingClass);
        stubNoExistingRecord(20, 7);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(10), today.atTime(8, 0)));

        assertEquals(TrainingAttendanceServiceImpl.STORE_UNRESOLVED, ex.getMessage());
    }

    @Test
    void openRecordBlocksAnotherCheckInForTheSameClass() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        TrainingAttendance open = openRecord(trainingClass, today.minusDays(1).atTime(8, 0));
        open.setWorkDate(today.minusDays(1));
        stubStaff(trainingClass);
        when(trainingAttendanceRepository.findOpenByEmployee(20)).thenReturn(List.of(open));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(staffUser, 7, here(10), today.atTime(8, 0)));

        assertEquals(TrainingAttendanceServiceImpl.OPEN_RECORD, ex.getMessage());
        verify(trainingAttendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminCannotCheckInForSomeoneElse() {
        AuthenticatedUser admin = AuthenticatedUser.from(buildUser(1, "admin", RoleName.ADMIN, null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkIn(admin, 7, here(10), today.atTime(8, 0)));

        assertEquals("Bạn không có quyền chấm công.", ex.getMessage());
        verify(employeeRepository, never()).findByIdWithStore(any());
    }

    @Test
    void lateThresholdStartsAtTwentyMinutes() {
        TrainingClass trainingClass = approvedClass(7, trainer);
        stubStaff(trainingClass);
        stubStore();
        stubNoExistingRecord(20, 7);
        when(trainingAttendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.checkIn(staffUser, 7, here(10), today.atTime(8, 19));
        service.checkIn(staffUser, 7, here(10), today.atTime(8, 20));

        ArgumentCaptor<TrainingAttendance> captor = ArgumentCaptor.forClass(TrainingAttendance.class);
        verify(trainingAttendanceRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertEquals(AttendanceStatus.PRESENT, captor.getAllValues().get(0).getStatus());
        assertEquals(0, captor.getAllValues().get(0).getLateMinutes());
        assertEquals(AttendanceStatus.LATE, captor.getAllValues().get(1).getStatus());
        assertEquals(20, captor.getAllValues().get(1).getLateMinutes());
    }

    private void stubStaff(TrainingClass trainingClass) {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(trainingAttendanceRepository.findClassForAttendance(trainingClass.getId())).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.findByClassAndEmployee(trainingClass.getId(), 20))
                .thenReturn(Optional.of(TrainingClassEnrollment.builder()
                        .id(1)
                        .trainingClass(trainingClass)
                        .employee(staffEmployee)
                        .build()));
    }

    private void stubManager(TrainingClass trainingClass) {
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(managerEmployee));
        when(trainingAttendanceRepository.findClassForAttendance(trainingClass.getId())).thenReturn(Optional.of(trainingClass));
    }

    private void stubStore() {
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
    }

    private void stubNoExistingRecord(Integer employeeId, Integer classId) {
        when(trainingAttendanceRepository.findOpenByEmployee(employeeId)).thenReturn(List.of());
        when(trainingAttendanceRepository.findByEmployee_IdAndTrainingClass_IdAndWorkDate(employeeId, classId, today))
                .thenReturn(Optional.empty());
    }

    private TrainingClass approvedClass(Integer id, User classTrainer) {
        return TrainingClass.builder()
                .id(id)
                .className("Pha chế " + id)
                .trainingType(TrainingType.STORE_TRAINING)
                .status(TrainingClassStatus.APPROVED)
                .store(store)
                .trainer(classTrainer)
                .createdBy(classTrainer)
                .startDate(today)
                .endDate(today)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .build();
    }

    private TrainingAttendance openRecord(TrainingClass trainingClass, LocalDateTime checkIn) {
        return TrainingAttendance.builder()
                .id(31)
                .employee(staffEmployee)
                .trainingClass(trainingClass)
                .store(store)
                .workDate(checkIn.toLocalDate())
                .className(trainingClass.getClassName())
                .scheduledStartTime(trainingClass.getStartTime())
                .scheduledEndTime(trainingClass.getEndTime())
                .checkInTime(checkIn)
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .status(AttendanceStatus.PRESENT)
                .build();
    }

    private AttendanceLocation here(double accuracyMeters) {
        return new AttendanceLocation(LAT, LNG, accuracyMeters);
    }

    private AttendanceLocation north(double meters, double accuracyMeters) {
        double delta = Math.toDegrees(meters / GeoDistance.EARTH_RADIUS_METERS);
        return new AttendanceLocation(BigDecimal.valueOf(LAT.doubleValue() + delta), LNG, accuracyMeters);
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
