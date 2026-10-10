package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.geo.GeoDistance;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.dto.response.TrainingClockView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingAttendance;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingAttendanceRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendancePolicyProperties;
import com.example.coffee_hrm.service.TrainingAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TrainingAttendanceServiceImpl implements TrainingAttendanceService {

    static final String NOT_ASSIGNED = "Bạn không được phân công tham gia lớp đào tạo này.";
    static final String NOT_APPROVED = "Lớp đào tạo chưa được duyệt.";
    static final String OUTSIDE_DATES = "Hôm nay không nằm trong thời gian của lớp đào tạo.";
    static final String BAD_SCHEDULE = "Lớp đào tạo chưa được thiết lập đủ thông tin giờ học.";
    static final String TOO_EARLY = "Chưa đến thời gian Check-in";
    static final String SESSION_ENDED = "Buổi đào tạo đã kết thúc.";
    static final String ALREADY_IN = "Bạn đã Check-in cho buổi đào tạo này.";
    static final String NOT_CHECKED_IN = "Bạn chưa Check-in cho buổi đào tạo này.";
    static final String ALREADY_OUT = "Bạn đã Check-out cho buổi đào tạo này.";
    static final String OPEN_RECORD = "Bạn chưa Check-out bản ghi chấm công đang mở.";
    static final String LOCATION_UNKNOWN = "Không thể xác định vị trí của bạn.";
    static final String LOCATION_INACCURATE = "Vị trí chưa đủ chính xác. Hãy thử lại ở nơi có tín hiệu tốt hơn.";
    static final String OUTSIDE_RADIUS = "Bạn đang ở ngoài phạm vi chấm công của cửa hàng.";
    static final String STORE_UNRESOLVED = "Không xác định được cửa hàng để kiểm tra vị trí chấm công.";
    static final String STORE_LOCATION_MISSING = "Cửa hàng chưa được thiết lập vị trí chấm công.";
    static final String EARLY_CHECKOUT_BLOCKED = "Chưa đến giờ kết thúc buổi. Không thể Check-out sớm.";
    static final double MAX_ACCURACY_METERS = 50;
    private static final double DISTANCE_TOLERANCE_METERS = 0.01;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TrainingAttendanceRepository trainingAttendanceRepository;
    private final TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final AttendancePolicyProperties policy;

    @Override
    @Transactional(readOnly = true)
    public TrainingClockView getClock(AuthenticatedUser user) {
        Employee employee = resolveEmployee(user);
        LocalDateTime now = truncate(VietnamTime.now());
        LocalDate today = now.toLocalDate();
        Map<Integer, TrainingAttendance> openByClass = openByClass(employee.getId());
        Map<Integer, TrainingClass> classes = new LinkedHashMap<>();
        for (TrainingClass trainingClass : assignedClasses(user, employee)) {
            if (covers(trainingClass, today) || openByClass.containsKey(trainingClass.getId())) {
                classes.put(trainingClass.getId(), trainingClass);
            }
        }
        for (TrainingAttendance open : openByClass.values()) {
            TrainingClass trainingClass = open.getTrainingClass();
            if (trainingClass != null && trainingClass.getId() != null && !classes.containsKey(trainingClass.getId())) {
                loadClass(trainingClass.getId())
                        .filter(loaded -> isAssigned(user, employee, loaded))
                        .ifPresent(loaded -> classes.put(loaded.getId(), loaded));
            }
        }
        List<TrainingClockView.Session> sessions = new ArrayList<>();
        for (TrainingClass trainingClass : classes.values()) {
            sessions.add(toSession(employee, trainingClass, openByClass.get(trainingClass.getId()), today, now));
        }
        sessions.sort(Comparator
                .comparing(TrainingClockView.Session::getTimeLabel, Comparator.nullsLast(String::compareTo))
                .thenComparing(TrainingClockView.Session::getClassName, Comparator.nullsLast(String::compareToIgnoreCase)));
        Store home = employee.getStore();
        return TrainingClockView.builder()
                .employeeName(employee.getFullName())
                .storeName(home != null ? home.getStoreName() : null)
                .sessions(sessions)
                .build();
    }

    @Override
    @Transactional
    public void checkIn(AuthenticatedUser user, Integer classId, AttendanceLocation location) {
        checkIn(user, classId, location, VietnamTime.now());
    }

    @Override
    @Transactional
    public void checkOut(AuthenticatedUser user, Integer classId, AttendanceLocation location) {
        checkOut(user, classId, location, VietnamTime.now());
    }

    void checkIn(AuthenticatedUser user, Integer classId, AttendanceLocation location, LocalDateTime rawNow) {
        Employee employee = resolveEmployee(user);
        LocalDateTime now = truncate(rawNow);
        LocalDate today = now.toLocalDate();
        TrainingClass trainingClass = requireAssignedClass(user, employee, classId);
        if (!openByClass(employee.getId()).isEmpty()
                && openByClass(employee.getId()).containsKey(trainingClass.getId())) {
            throw new BusinessException(OPEN_RECORD);
        }
        Optional<TrainingAttendance> todayRecord = trainingAttendanceRepository
                .findByEmployee_IdAndTrainingClass_IdAndWorkDate(employee.getId(), trainingClass.getId(), today);
        if (todayRecord.isPresent()) {
            throw new BusinessException(todayRecord.get().getCheckOutTime() == null ? ALREADY_IN : ALREADY_OUT);
        }
        if (!covers(trainingClass, today)) {
            throw new BusinessException(OUTSIDE_DATES);
        }
        if (!hasValidSchedule(trainingClass)) {
            throw new BusinessException(BAD_SCHEDULE);
        }
        LocalDateTime start = truncate(LocalDateTime.of(today, trainingClass.getStartTime()));
        LocalDateTime end = truncate(LocalDateTime.of(today, trainingClass.getEndTime()));
        LocalDateTime windowOpen = start.minusMinutes(leadMinutes());
        if (now.isBefore(windowOpen)) {
            throw new BusinessException(TOO_EARLY);
        }
        if (!now.isBefore(end)) {
            throw new BusinessException(SESSION_ENDED);
        }
        Store store = resolveGpsStore(trainingClass, employee);
        assertInsideStore(store, location);
        int lateMinutes = lateMinutes(start, now);
        TrainingAttendance attendance = TrainingAttendance.builder()
                .employee(employee)
                .trainingClass(trainingClass)
                .workDate(today)
                .store(store)
                .className(trainingClass.getClassName())
                .scheduledStartTime(trainingClass.getStartTime())
                .scheduledEndTime(trainingClass.getEndTime())
                .checkInTime(now)
                .checkInLatitude(location.latitude())
                .checkInLongitude(location.longitude())
                .lateMinutes(lateMinutes)
                .earlyLeaveMinutes(0)
                .status(lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
                .build();
        try {
            trainingAttendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ALREADY_IN);
        }
    }

    void checkOut(AuthenticatedUser user, Integer classId, AttendanceLocation location, LocalDateTime rawNow) {
        Employee employee = resolveEmployee(user);
        LocalDateTime now = truncate(rawNow);
        TrainingClass trainingClass = requireAssignedClass(user, employee, classId);
        List<TrainingAttendance> openRecords = trainingAttendanceRepository
                .findOpenByEmployeeAndClass(employee.getId(), trainingClass.getId());
        if (openRecords.isEmpty()) {
            Optional<TrainingAttendance> todayRecord = trainingAttendanceRepository
                    .findByEmployee_IdAndTrainingClass_IdAndWorkDate(
                            employee.getId(), trainingClass.getId(), now.toLocalDate());
            if (todayRecord.isPresent() && todayRecord.get().getCheckOutTime() != null) {
                throw new BusinessException(ALREADY_OUT);
            }
            throw new BusinessException(NOT_CHECKED_IN);
        }
        TrainingAttendance attendance = openRecords.getFirst();
        if (attendance.getCheckOutTime() != null) {
            throw new BusinessException(ALREADY_OUT);
        }
        Store store = resolveGpsStore(trainingClass, employee);
        assertInsideStore(store, location);
        LocalDateTime scheduledEnd = scheduledEnd(attendance);
        if (!policy.isAllowEarlyCheckout() && scheduledEnd != null && now.isBefore(scheduledEnd)) {
            throw new BusinessException(EARLY_CHECKOUT_BLOCKED);
        }
        int earlyMinutes = 0;
        if (scheduledEnd != null && now.isBefore(scheduledEnd)) {
            earlyMinutes = (int) ChronoUnit.MINUTES.between(now, scheduledEnd);
        }
        LocalDateTime checkIn = attendance.getCheckInTime() == null ? now : truncate(attendance.getCheckInTime());
        int workingMinutes = (int) Math.max(0, ChronoUnit.MINUTES.between(checkIn, now));
        int lateMinutes = attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        attendance.setCheckOutTime(now);
        attendance.setCheckOutLatitude(location.latitude());
        attendance.setCheckOutLongitude(location.longitude());
        attendance.setWorkingMinutes(workingMinutes);
        attendance.setEarlyLeaveMinutes(Math.max(earlyMinutes, 0));
        attendance.setStatus(resolveStatus(lateMinutes, earlyMinutes));
        trainingAttendanceRepository.save(attendance);
    }

    private TrainingClockView.Session toSession(Employee employee,
                                                TrainingClass trainingClass,
                                                TrainingAttendance open,
                                                LocalDate today,
                                                LocalDateTime now) {
        Optional<TrainingAttendance> todayRecord = trainingAttendanceRepository
                .findByEmployee_IdAndTrainingClass_IdAndWorkDate(employee.getId(), trainingClass.getId(), today);
        boolean checkedOutToday = todayRecord.isPresent() && todayRecord.get().getCheckOutTime() != null;
        boolean openToday = open != null && today.equals(open.getWorkDate());
        boolean canCheckOut = open != null;
        boolean scheduleValid = hasValidSchedule(trainingClass);
        boolean inWindow = false;
        if (scheduleValid && covers(trainingClass, today)) {
            LocalDateTime start = truncate(LocalDateTime.of(today, trainingClass.getStartTime()));
            LocalDateTime end = truncate(LocalDateTime.of(today, trainingClass.getEndTime()));
            inWindow = !now.isBefore(start.minusMinutes(leadMinutes())) && now.isBefore(end);
        }
        boolean canCheckIn = open == null && !checkedOutToday && scheduleValid && covers(trainingClass, today) && inWindow;
        return TrainingClockView.Session.builder()
                .classId(trainingClass.getId())
                .className(trainingClass.getClassName())
                .typeLabel(typeLabel(trainingClass))
                .dateLabel(dateLabel(trainingClass, today))
                .timeLabel(timeLabel(trainingClass))
                .statusLabel(sessionStatus(trainingClass, open, checkedOutToday, openToday, today, now, scheduleValid))
                .canCheckIn(canCheckIn)
                .canCheckOut(canCheckOut)
                .build();
    }

    private String sessionStatus(TrainingClass trainingClass,
                                 TrainingAttendance open,
                                 boolean checkedOutToday,
                                 boolean openToday,
                                 LocalDate today,
                                 LocalDateTime now,
                                 boolean scheduleValid) {
        if (open != null && !openToday) {
            return OPEN_RECORD;
        }
        if (open != null) {
            return statusText(open.getStatus());
        }
        if (checkedOutToday) {
            return "Đã Check-out";
        }
        if (!covers(trainingClass, today)) {
            return OUTSIDE_DATES;
        }
        if (!scheduleValid) {
            return BAD_SCHEDULE;
        }
        LocalDateTime start = truncate(LocalDateTime.of(today, trainingClass.getStartTime()));
        LocalDateTime end = truncate(LocalDateTime.of(today, trainingClass.getEndTime()));
        if (now.isBefore(start.minusMinutes(leadMinutes()))) {
            return TOO_EARLY;
        }
        if (!now.isBefore(end)) {
            return SESSION_ENDED;
        }
        return "Chưa Check-in";
    }

    private String statusText(AttendanceStatus status) {
        if (status == AttendanceStatus.LATE || status == AttendanceStatus.LATE_AND_EARLY) {
            return "Đã Check-in (đi muộn)";
        }
        return "Đã Check-in";
    }

    private List<TrainingClass> assignedClasses(AuthenticatedUser user, Employee employee) {
        if (user.getRoleName() == RoleName.MANAGER) {
            if (user.getUserId() == null) {
                return List.of();
            }
            return trainingAttendanceRepository.findApprovedClassesForTrainer(user.getUserId(), TrainingClassStatus.APPROVED);
        }
        return trainingClassEnrollmentRepository
                .findByEmployeeAndStatusWithClass(employee.getId(), TrainingClassStatus.APPROVED)
                .stream()
                .map(TrainingClassEnrollment::getTrainingClass)
                .filter(trainingClass -> trainingClass != null && trainingClass.getStatus() == TrainingClassStatus.APPROVED)
                .toList();
    }

    private TrainingClass requireAssignedClass(AuthenticatedUser user, Employee employee, Integer classId) {
        TrainingClass trainingClass = loadClass(classId)
                .orElseThrow(() -> new BusinessException(NOT_ASSIGNED));
        if (trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            throw new BusinessException(NOT_APPROVED);
        }
        if (!isAssigned(user, employee, trainingClass)) {
            throw new BusinessException(NOT_ASSIGNED);
        }
        return trainingClass;
    }

    private boolean isAssigned(AuthenticatedUser user, Employee employee, TrainingClass trainingClass) {
        if (trainingClass == null || trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            return false;
        }
        if (user.getRoleName() == RoleName.MANAGER) {
            return trainingClass.getTrainer() != null
                    && user.getUserId() != null
                    && user.getUserId().equals(trainingClass.getTrainer().getId());
        }
        if (user.getRoleName() != RoleName.STAFF || employee.getId() == null || trainingClass.getId() == null) {
            return false;
        }
        return trainingClassEnrollmentRepository
                .findByClassAndEmployee(trainingClass.getId(), employee.getId())
                .isPresent();
    }

    private Optional<TrainingClass> loadClass(Integer classId) {
        if (classId == null) {
            return Optional.empty();
        }
        return trainingAttendanceRepository.findClassForAttendance(classId);
    }

    private Map<Integer, TrainingAttendance> openByClass(Integer employeeId) {
        Map<Integer, TrainingAttendance> open = new LinkedHashMap<>();
        for (TrainingAttendance attendance : trainingAttendanceRepository.findOpenByEmployee(employeeId)) {
            if (attendance.getTrainingClass() != null && attendance.getTrainingClass().getId() != null) {
                open.putIfAbsent(attendance.getTrainingClass().getId(), attendance);
            }
        }
        return open;
    }

    private Store resolveGpsStore(TrainingClass trainingClass, Employee employee) {
        if (trainingClass.getTrainingType() == TrainingType.CENTRALIZED_TRAINING) {
            Store home = employee.getStore();
            boolean participating = home != null
                    && home.getId() != null
                    && trainingClass.getParticipatingStores() != null
                    && trainingClass.getParticipatingStores().stream()
                    .anyMatch(store -> home.getId().equals(store.getId()));
            if (!participating) {
                throw new BusinessException(STORE_UNRESOLVED);
            }
            return latestStore(home.getId());
        }
        if (trainingClass.getStore() == null || trainingClass.getStore().getId() == null) {
            throw new BusinessException(STORE_UNRESOLVED);
        }
        return latestStore(trainingClass.getStore().getId());
    }

    private Store latestStore(Integer storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(STORE_UNRESOLVED));
        if (!GeoDistance.isValidLatitude(store.getLatitude()) || !GeoDistance.isValidLongitude(store.getLongitude())) {
            throw new BusinessException(STORE_LOCATION_MISSING);
        }
        return store;
    }

    private void assertInsideStore(Store store, AttendanceLocation location) {
        if (location == null
                || !GeoDistance.isValidLatitude(location.latitude())
                || !GeoDistance.isValidLongitude(location.longitude())) {
            throw new BusinessException(LOCATION_UNKNOWN);
        }
        Double accuracy = location.accuracyMeters();
        if (accuracy == null || accuracy < 0 || accuracy > MAX_ACCURACY_METERS) {
            throw new BusinessException(LOCATION_INACCURATE);
        }
        double distance = GeoDistance.meters(
                store.getLatitude(), store.getLongitude(), location.latitude(), location.longitude());
        if (distance > radiusMeters() + DISTANCE_TOLERANCE_METERS) {
            throw new BusinessException(OUTSIDE_RADIUS);
        }
    }

    private boolean covers(TrainingClass trainingClass, LocalDate day) {
        return trainingClass.getStartDate() != null
                && trainingClass.getEndDate() != null
                && !day.isBefore(trainingClass.getStartDate())
                && !day.isAfter(trainingClass.getEndDate());
    }

    private boolean hasValidSchedule(TrainingClass trainingClass) {
        return trainingClass.getStartTime() != null
                && trainingClass.getEndTime() != null
                && trainingClass.getEndTime().isAfter(trainingClass.getStartTime());
    }

    private Employee resolveEmployee(AuthenticatedUser user) {
        if (user == null || (user.getRoleName() != RoleName.STAFF && user.getRoleName() != RoleName.MANAGER)) {
            throw new BusinessException("Bạn không có quyền chấm công.");
        }
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa liên kết với hồ sơ nhân viên.");
        }
        return employeeRepository.findByIdWithStore(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin hồ sơ nhân viên."));
    }

    private LocalDateTime scheduledEnd(TrainingAttendance attendance) {
        if (attendance.getWorkDate() == null
                || attendance.getScheduledStartTime() == null
                || attendance.getScheduledEndTime() == null
                || !attendance.getScheduledEndTime().isAfter(attendance.getScheduledStartTime())) {
            return null;
        }
        return truncate(LocalDateTime.of(attendance.getWorkDate(), attendance.getScheduledEndTime()));
    }

    private int lateMinutes(LocalDateTime start, LocalDateTime checkIn) {
        long minutes = ChronoUnit.MINUTES.between(start, checkIn);
        if (minutes < lateThreshold()) {
            return 0;
        }
        return (int) minutes;
    }

    private AttendanceStatus resolveStatus(int lateMinutes, int earlyMinutes) {
        boolean late = lateMinutes > 0;
        boolean early = earlyMinutes > 0;
        if (late && early) {
            return AttendanceStatus.LATE_AND_EARLY;
        }
        if (late) {
            return AttendanceStatus.LATE;
        }
        if (early) {
            return AttendanceStatus.EARLY_LEAVE;
        }
        return AttendanceStatus.PRESENT;
    }

    private String typeLabel(TrainingClass trainingClass) {
        if (trainingClass.getTrainingType() == null) {
            return TrainingType.STORE_TRAINING.getLabel();
        }
        return trainingClass.getTrainingType().getLabel();
    }

    private String dateLabel(TrainingClass trainingClass, LocalDate today) {
        String todayLabel = DATE_FORMAT.format(today);
        if (trainingClass.getStartDate() == null) {
            return todayLabel;
        }
        String start = DATE_FORMAT.format(trainingClass.getStartDate());
        if (trainingClass.getEndDate() == null || trainingClass.getEndDate().equals(trainingClass.getStartDate())) {
            return start;
        }
        return todayLabel + " · " + start + " – " + DATE_FORMAT.format(trainingClass.getEndDate());
    }

    private String timeLabel(TrainingClass trainingClass) {
        if (trainingClass.getStartTime() == null || trainingClass.getEndTime() == null) {
            return "Chưa có giờ học";
        }
        return TIME_FORMAT.format(trainingClass.getStartTime()) + " – " + TIME_FORMAT.format(trainingClass.getEndTime());
    }

    private int leadMinutes() {
        return Math.max(policy.getCheckInLeadMinutes(), 0);
    }

    private int lateThreshold() {
        return Math.max(policy.getLateThresholdMinutes(), 0);
    }

    private double radiusMeters() {
        return Math.max(policy.getRadiusMeters(), 0);
    }

    private LocalDateTime truncate(LocalDateTime value) {
        return value.withSecond(0).withNano(0);
    }
}
