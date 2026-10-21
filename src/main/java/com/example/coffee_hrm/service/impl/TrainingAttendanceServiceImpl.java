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
    static final String OUTSIDE_RADIUS = "Bạn đang ở ngoài phạm vi chấm công của cửa hàng.";
    static final String STORE_UNRESOLVED = "Không xác định được cửa hàng để kiểm tra vị trí chấm công.";
    static final String STORE_LOCATION_MISSING = "Cửa hàng chưa được thiết lập vị trí chấm công.";
    static final String CHECKOUT_NOT_OPEN = "Không được Check-out sớm. Chỉ được Check-out từ 5 phút trước khi buổi đào tạo kết thúc.";
    static final String PHOTO_REQUIRED = "Hãy chụp ảnh nhân viên trước khi lưu.";
    private static final double DISTANCE_TOLERANCE_METERS = 0.01;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TrainingAttendanceRepository trainingAttendanceRepository;
    private final TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final AttendancePhotoStore photoStore;
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
        checkIn(user, classId, location, VietnamTime.now(), null);
    }

    @Override
    @Transactional
    public void checkOut(AuthenticatedUser user, Integer classId, AttendanceLocation location) {
        checkOut(user, classId, location, VietnamTime.now(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public void previewCheckIn(AuthenticatedUser user, Integer classId, AttendanceLocation location) {
        planCheckIn(user, classId, location, VietnamTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public void previewCheckOut(AuthenticatedUser user, Integer classId, AttendanceLocation location) {
        planCheckOut(user, classId, location, VietnamTime.now());
    }

    @Override
    @Transactional
    public void checkInWithPhoto(AuthenticatedUser user, Integer classId, AttendanceLocation location, byte[] photo) {
        requirePhoto(photo);
        checkIn(user, classId, location, VietnamTime.now(), photo);
    }

    @Override
    @Transactional
    public void checkOutWithPhoto(AuthenticatedUser user, Integer classId, AttendanceLocation location, byte[] photo) {
        requirePhoto(photo);
        checkOut(user, classId, location, VietnamTime.now(), photo);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] readPhoto(AuthenticatedUser user, Integer attendanceId, boolean checkIn) {
        TrainingAttendance attendance = trainingAttendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy ảnh."));
        assertCanViewPhoto(user, attendance);
        String name = checkIn ? attendance.getCheckInPhotoName() : attendance.getCheckOutPhotoName();
        if (name == null || name.isBlank()) {
            throw new BusinessException("Không tìm thấy ảnh.");
        }
        return photoStore.read(name);
    }

    void checkIn(AuthenticatedUser user, Integer classId, AttendanceLocation location, LocalDateTime rawNow) {
        checkIn(user, classId, location, rawNow, null);
    }

    void checkIn(AuthenticatedUser user, Integer classId, AttendanceLocation location, LocalDateTime rawNow, byte[] photo) {
        CheckInPlan plan = planCheckIn(user, classId, location, rawNow);
        TrainingAttendance attendance = TrainingAttendance.builder()
                .employee(plan.employee())
                .trainingClass(plan.trainingClass())
                .workDate(plan.today())
                .store(plan.store())
                .className(plan.trainingClass().getClassName())
                .scheduledStartTime(plan.trainingClass().getStartTime())
                .scheduledEndTime(plan.trainingClass().getEndTime())
                .checkInTime(plan.now())
                .checkInLatitude(location.latitude())
                .checkInLongitude(location.longitude())
                .lateMinutes(plan.lateMinutes())
                .earlyLeaveMinutes(0)
                .status(plan.status())
                .build();
        try {
            trainingAttendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ALREADY_IN);
        }
        attachPhoto(attendance, true, photo);
    }

    void checkOut(AuthenticatedUser user, Integer classId, AttendanceLocation location, LocalDateTime rawNow) {
        checkOut(user, classId, location, rawNow, null);
    }

    void checkOut(AuthenticatedUser user, Integer classId, AttendanceLocation location, LocalDateTime rawNow, byte[] photo) {
        CheckOutPlan plan = planCheckOut(user, classId, location, rawNow);
        TrainingAttendance attendance = plan.attendance();
        LocalDateTime checkIn = attendance.getCheckInTime() == null ? plan.now() : attendance.getCheckInTime();
        int workingMinutes = (int) Math.max(0, ChronoUnit.MINUTES.between(checkIn, plan.now()));
        int lateMinutes = attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        attendance.setCheckOutTime(plan.now());
        attendance.setCheckOutLatitude(location.latitude());
        attendance.setCheckOutLongitude(location.longitude());
        attendance.setWorkingMinutes(workingMinutes);
        attendance.setEarlyLeaveMinutes(0);
        attendance.setStatus(resolveStatus(attendance.getStatus(), lateMinutes, 0));
        trainingAttendanceRepository.save(attendance);
        attachPhoto(attendance, false, photo);
    }

    private CheckInPlan planCheckIn(AuthenticatedUser user, Integer classId, AttendanceLocation location, LocalDateTime rawNow) {
        Employee employee = resolveEmployee(user);
        LocalDateTime now = exactTime(rawNow);
        LocalDate today = now.toLocalDate();
        TrainingClass trainingClass = requireAssignedClass(user, employee, classId);
        if (openByClass(employee.getId()).containsKey(trainingClass.getId())) {
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
        if (now.isBefore(start.minusMinutes(leadMinutes()))) {
            throw new BusinessException(TOO_EARLY);
        }
        if (!now.isBefore(end)) {
            throw new BusinessException(SESSION_ENDED);
        }
        Store store = resolveGpsStore(trainingClass, employee);
        assertInsideStore(store, location);
        long minutesAfterStart = Math.max(0, ChronoUnit.MINUTES.between(start, now));
        return new CheckInPlan(employee, trainingClass, store, today, now,
                storedLateMinutes(minutesAfterStart), checkInStatus(minutesAfterStart));
    }

    private CheckOutPlan planCheckOut(AuthenticatedUser user, Integer classId, AttendanceLocation location, LocalDateTime rawNow) {
        Employee employee = resolveEmployee(user);
        LocalDateTime now = exactTime(rawNow);
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
        LocalDateTime scheduledEnd = scheduledEnd(attendance);
        if (!checkoutOpen(scheduledEnd, now)) {
            throw new BusinessException(CHECKOUT_NOT_OPEN);
        }
        Store store = resolveGpsStore(trainingClass, employee);
        assertInsideStore(store, location);
        return new CheckOutPlan(attendance, now, scheduledEnd);
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
        boolean canCheckOut = open != null && checkoutOpen(scheduledEnd(open), now);
        boolean awaitingCheckout = open != null && !canCheckOut;
        boolean scheduleValid = hasValidSchedule(trainingClass);
        boolean inWindow = false;
        if (scheduleValid && covers(trainingClass, today)) {
            LocalDateTime start = truncate(LocalDateTime.of(today, trainingClass.getStartTime()));
            LocalDateTime end = truncate(LocalDateTime.of(today, trainingClass.getEndTime()));
            inWindow = !now.isBefore(start.minusMinutes(leadMinutes())) && now.isBefore(end);
        }
        boolean canCheckIn = open == null && !checkedOutToday && scheduleValid && covers(trainingClass, today) && inWindow;
        TrainingAttendance shown = open != null ? open : todayRecord.orElse(null);
        boolean checkedIn = shown != null && shown.getCheckInTime() != null;
        boolean checkedOut = shown != null && shown.getCheckOutTime() != null;
        String statusKey = checkedOut ? "done" : (checkedIn ? "working" : (canCheckIn ? "ready" : "upcoming"));
        String checkInNotice = null;
        if (!canCheckIn) {
            if (open != null && !openToday) {
                checkInNotice = OPEN_RECORD;
            } else if (open != null) {
                checkInNotice = ALREADY_IN;
            } else if (checkedOutToday) {
                checkInNotice = ALREADY_OUT;
            } else if (!covers(trainingClass, today)) {
                checkInNotice = OUTSIDE_DATES;
            } else if (!scheduleValid) {
                checkInNotice = BAD_SCHEDULE;
            } else if (scheduleValid && now.isBefore(truncate(LocalDateTime.of(today, trainingClass.getStartTime())).minusMinutes(leadMinutes()))) {
                checkInNotice = TOO_EARLY;
            } else {
                checkInNotice = SESSION_ENDED;
            }
        }
        String checkOutNotice = null;
        if (!canCheckOut) {
            if (awaitingCheckout) {
                checkOutNotice = CHECKOUT_NOT_OPEN;
            } else if (checkedOut) {
                checkOutNotice = ALREADY_OUT;
            } else {
                checkOutNotice = NOT_CHECKED_IN;
            }
        }
        return TrainingClockView.Session.builder()
                .classId(trainingClass.getId())
                .className(trainingClass.getClassName())
                .typeLabel(typeLabel(trainingClass))
                .dateLabel(dateLabel(trainingClass, today))
                .timeLabel(timeLabel(trainingClass))
                .statusKey(statusKey)
                .statusLabel(checkedOut ? "Đã hoàn thành" : (checkedIn ? "Đang làm" : sessionStatus(
                        trainingClass, open, checkedOutToday, openToday, today, now, scheduleValid)))
                .checkInLabel(checkedIn ? shown.getCheckInTime().format(TIME_FORMAT) : "—")
                .checkOutLabel(checkedOut ? shown.getCheckOutTime().format(TIME_FORMAT) : "—")
                .canCheckIn(canCheckIn)
                .checkInNotice(checkInNotice)
                .canCheckOut(canCheckOut)
                .checkOutNotice(checkOutNotice)
                .checkoutHint(awaitingCheckout ? CHECKOUT_NOT_OPEN : null)
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
        if (status == AttendanceStatus.NOT_PARTICIPATED) {
            return "Không tham gia";
        }
        if (status == AttendanceStatus.LATE || status == AttendanceStatus.LATE_AND_EARLY) {
            return "Đã Check-in (check-in muộn)";
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

    private int storedLateMinutes(long minutesAfterStart) {
        if (minutesAfterStart < trainingLateThreshold()) {
            return 0;
        }
        return (int) minutesAfterStart;
    }

    private AttendanceStatus checkInStatus(long minutesAfterStart) {
        if (minutesAfterStart >= trainingAbsentThreshold()) {
            return AttendanceStatus.NOT_PARTICIPATED;
        }
        if (minutesAfterStart >= trainingLateThreshold()) {
            return AttendanceStatus.LATE;
        }
        return AttendanceStatus.PRESENT;
    }

    private AttendanceStatus resolveStatus(AttendanceStatus current, int lateMinutes, int earlyMinutes) {
        if (current == AttendanceStatus.NOT_PARTICIPATED) {
            return AttendanceStatus.NOT_PARTICIPATED;
        }
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

    private void attachPhoto(TrainingAttendance attendance, boolean checkIn, byte[] photo) {
        if (photo == null) {
            return;
        }
        String name = photoStore.saveTraining(attendance.getId(), checkIn ? "in" : "out", photo);
        if (checkIn) {
            attendance.setCheckInPhotoName(name);
        } else {
            attendance.setCheckOutPhotoName(name);
        }
        trainingAttendanceRepository.save(attendance);
    }

    private void requirePhoto(byte[] photo) {
        if (!AttendancePhotoStore.isJpeg(photo)) {
            throw new BusinessException(photo == null || photo.length == 0 ? PHOTO_REQUIRED : AttendancePhotoStore.PHOTO_INVALID);
        }
    }

    private void assertCanViewPhoto(AuthenticatedUser user, TrainingAttendance attendance) {
        if (user == null || attendance.getEmployee() == null) {
            throw new BusinessException("Không tìm thấy ảnh.");
        }
        if (user.getRoleName() == RoleName.ADMIN) {
            return;
        }
        if (user.getEmployeeId() != null && user.getEmployeeId().equals(attendance.getEmployee().getId())) {
            return;
        }
        if (user.getRoleName() == RoleName.MANAGER && user.getEmployeeId() != null) {
            Store managed = storeRepository.findByManager_Id(user.getEmployeeId()).orElse(null);
            Integer storeId = attendance.getStore() != null
                    ? attendance.getStore().getId()
                    : (attendance.getEmployee().getStore() != null ? attendance.getEmployee().getStore().getId() : null);
            if (managed != null && managed.getId() != null && managed.getId().equals(storeId)) {
                return;
            }
        }
        throw new BusinessException("Không tìm thấy ảnh.");
    }

    private record CheckInPlan(Employee employee,
                               TrainingClass trainingClass,
                               Store store,
                               LocalDate today,
                               LocalDateTime now,
                               int lateMinutes,
                               AttendanceStatus status) {
    }

    private record CheckOutPlan(TrainingAttendance attendance, LocalDateTime now, LocalDateTime scheduledEnd) {
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

    private int trainingLateThreshold() {
        return Math.max(policy.getTrainingLateThresholdMinutes(), 0);
    }

    private boolean checkoutOpen(LocalDateTime scheduledEnd, LocalDateTime now) {
        LocalDateTime opens = checkoutOpensAt(scheduledEnd);
        return opens == null || !now.isBefore(opens);
    }

    private LocalDateTime checkoutOpensAt(LocalDateTime scheduledEnd) {
        if (scheduledEnd == null) {
            return null;
        }
        return scheduledEnd.minusMinutes(trainingCheckoutLeadMinutes());
    }

    private int trainingCheckoutLeadMinutes() {
        return Math.max(policy.getTrainingCheckoutLeadMinutes(), 0);
    }

    private int trainingAbsentThreshold() {
        int absent = Math.max(policy.getTrainingAbsentThresholdMinutes(), 0);
        int late = trainingLateThreshold();
        return absent < late ? late : absent;
    }

    private double radiusMeters() {
        return Math.max(policy.getRadiusMeters(), 0);
    }

    private LocalDateTime exactTime(LocalDateTime value) {
        return value.withNano(0);
    }

    private LocalDateTime truncate(LocalDateTime value) {
        return value.withSecond(0).withNano(0);
    }
}
