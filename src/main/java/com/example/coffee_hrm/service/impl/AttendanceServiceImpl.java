package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.geo.GeoDistance;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.dto.response.AttendanceClockView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendancePolicyProperties;
import com.example.coffee_hrm.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    static final String TOO_EARLY = "Chưa đến thời gian Check-in";
    static final String NO_SHIFT = "Bạn không có ca làm việc tại thời điểm hiện tại.";
    static final String ALREADY_IN = "Bạn đã Check-in cho ca làm việc này.";
    static final String NOT_CHECKED_IN = "Bạn chưa Check-in cho ca làm việc này.";
    static final String ALREADY_OUT = "Bạn đã Check-out cho ca làm việc này.";
    static final String EARLY_CHECKOUT_BLOCKED = "Chưa đến giờ kết thúc ca. Không thể Check-out sớm.";
    static final String CHECKOUT_TOO_SOON = "Chưa đủ 2 tiếng kể từ giờ vào ca. Chưa thể Check-out.";
    static final String PHOTO_REQUIRED = "Hãy chụp ảnh nhân viên trước khi lưu.";
    static final String OUTSIDE_RADIUS = "Bạn đang ở ngoài phạm vi chấm công của cửa hàng.";
    static final String LOCATION_UNKNOWN = "Không thể xác định vị trí của bạn.";
    static final String STORE_LOCATION_MISSING = "Cửa hàng chưa được thiết lập vị trí chấm công.";
    static final String MANAGER_ALREADY_IN = "Bạn đã Check-in trong ngày hôm nay.";
    static final String MANAGER_ALREADY_OUT = "Bạn đã Check-out trong ngày hôm nay.";
    static final String MANAGER_NOT_IN = "Bạn chưa Check-in trong ngày hôm nay.";
    static final String MANAGER_OPEN_RECORD = "Bạn chưa Check-out bản ghi chấm công đang mở.";
    static final String SLOT_REQUIRED = "Hãy chọn ca cần chấm công.";
    static final String SLOT_FORBIDDEN = "Bạn không có quyền chấm công ca này.";
    static final String SLOT_CLOSED = "Đã hết giờ Check-in của ca này.";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final double DISTANCE_TOLERANCE_METERS = 0.01;

    private final AttendanceRepository attendanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final AttendancePhotoStore photoStore;
    private final AttendancePolicyProperties policy;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.coffee_hrm.service.ReliabilityService reliabilityService;

    @Override
    @Transactional(readOnly = true)
    public AttendanceClockView getClock(AuthenticatedUser user) {
        return buildClock(user, VietnamTime.now());
    }

    @Override
    @Transactional
    public void checkIn(AuthenticatedUser user, AttendanceLocation location) {
        checkIn(user, location, VietnamTime.now(), null);
    }

    @Override
    @Transactional
    public void checkOut(AuthenticatedUser user, AttendanceLocation location) {
        checkOut(user, location, VietnamTime.now(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public void previewCheckIn(AuthenticatedUser user, AttendanceLocation location) {
        previewCheckIn(user, location, VietnamTime.now(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public void previewCheckOut(AuthenticatedUser user, AttendanceLocation location) {
        previewCheckOut(user, location, VietnamTime.now(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public void previewCheckIn(AuthenticatedUser user, AttendanceLocation location, Integer assignmentId) {
        previewCheckIn(user, location, VietnamTime.now(), assignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public void previewCheckOut(AuthenticatedUser user, AttendanceLocation location, Integer assignmentId) {
        previewCheckOut(user, location, VietnamTime.now(), assignmentId);
    }

    @Override
    @Transactional
    public void checkInWithPhoto(AuthenticatedUser user, AttendanceLocation location, byte[] photo) {
        requirePhoto(photo);
        checkIn(user, location, VietnamTime.now(), photo, null);
    }

    @Override
    @Transactional
    public void checkOutWithPhoto(AuthenticatedUser user, AttendanceLocation location, byte[] photo) {
        requirePhoto(photo);
        checkOut(user, location, VietnamTime.now(), photo, null);
    }

    @Override
    @Transactional
    public void checkInWithPhoto(AuthenticatedUser user, AttendanceLocation location, byte[] photo, Integer assignmentId) {
        requirePhoto(photo);
        checkIn(user, location, VietnamTime.now(), photo, assignmentId);
    }

    @Override
    @Transactional
    public void checkOutWithPhoto(AuthenticatedUser user, AttendanceLocation location, byte[] photo, Integer assignmentId) {
        requirePhoto(photo);
        checkOut(user, location, VietnamTime.now(), photo, assignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] readShiftPhoto(AuthenticatedUser user, Integer attendanceId, boolean checkIn) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy ảnh."));
        assertCanViewPhoto(user, attendance);
        String name = checkIn ? attendance.getCheckInPhotoName() : attendance.getCheckOutPhotoName();
        if (name == null || name.isBlank()) {
            throw new BusinessException("Không tìm thấy ảnh.");
        }
        return photoStore.read(name);
    }

    @Override
    @Transactional
    public void markAbsences(LocalDate workDate) {
        markAbsences(workDate, VietnamTime.now());
    }

    void checkIn(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow) {
        checkIn(user, location, rawNow, null);
    }

    void checkIn(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow, byte[] photo) {
        checkIn(user, location, rawNow, photo, null);
    }

    void checkIn(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow,
                 byte[] photo, Integer assignmentId) {
        Employee employee = resolveCheckEmployee(user);
        LocalDateTime now = exactTime(rawNow);
        if (user.getRoleName() == RoleName.MANAGER) {
            checkInManager(user, employee, location, now, photo);
            return;
        }
        if (assignmentId == null) {
            checkInStaff(user, employee, location, now, photo);
            return;
        }
        checkInStaffSlot(user, employee, assignmentId, location, now, photo);
    }

    void previewCheckIn(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow) {
        previewCheckIn(user, location, rawNow, null);
    }

    void previewCheckIn(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow,
                        Integer assignmentId) {
        Employee employee = resolveCheckEmployee(user);
        LocalDateTime now = truncate(rawNow);
        if (user.getRoleName() == RoleName.MANAGER) {
            planManagerCheckIn(user, employee, location, now);
            return;
        }
        if (assignmentId == null) {
            planStaffCheckIn(user, employee, location, now);
            return;
        }
        planStaffSlot(user, employee, assignmentId, location, now);
    }

    void checkOut(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow) {
        checkOut(user, location, rawNow, null, null);
    }

    void checkOut(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow, byte[] photo) {
        checkOut(user, location, rawNow, photo, null);
    }

    void checkOut(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow,
                  byte[] photo, Integer assignmentId) {
        Employee employee = resolveCheckEmployee(user);
        LocalDateTime now = exactTime(rawNow);
        if (user.getRoleName() == RoleName.MANAGER) {
            checkOutManager(employee, location, now, photo);
            return;
        }
        if (assignmentId == null) {
            checkOutStaff(employee, location, now, photo);
            return;
        }
        checkOutStaffSlot(user, employee, assignmentId, location, now, photo);
    }

    void previewCheckOut(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow) {
        previewCheckOut(user, location, rawNow, null);
    }

    void previewCheckOut(AuthenticatedUser user, AttendanceLocation location, LocalDateTime rawNow,
                         Integer assignmentId) {
        Employee employee = resolveCheckEmployee(user);
        LocalDateTime now = truncate(rawNow);
        if (user.getRoleName() == RoleName.MANAGER) {
            assertCheckoutReady(locateManagerCheckout(employee, now), now, location);
            return;
        }
        if (assignmentId == null) {
            assertCheckoutReady(locateStaffCheckout(employee, now), now, location);
            return;
        }
        assertCheckoutReady(locateStaffSlot(user, employee, assignmentId), now, location);
    }

    void markAbsences(LocalDate workDate, LocalDateTime rawNow) {
        if (workDate == null) {
            return;
        }
        LocalDateTime now = truncate(rawNow);
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findPublishedAssignedOnDate(
                workDate, AssignmentStatus.ASSIGNED);
        for (ShiftAssignment assignment : assignments) {
            if (assignment.getShift() == null || assignment.getEmployee() == null) {
                continue;
            }
            ShiftWindow window = windowOf(assignment);
            if (now.isBefore(window.shiftEnd())) {
                continue;
            }
            if (findAttendance(assignment.getEmployee().getId(), window).isPresent()) {
                continue;
            }
            Attendance absent = Attendance.builder()
                    .employee(assignment.getEmployee())
                    .store(assignment.getShift().getStore())
                    .shift(assignment.getShift())
                    .workDate(assignment.getWorkDate())
                    .scheduledShiftName(assignment.getShift().getShiftName())
                    .scheduledStartTime(assignment.getShift().getStartTime())
                    .scheduledEndTime(assignment.getShift().getEndTime())
                    .lateMinutes(0)
                    .earlyLeaveMinutes(0)
                    .status(AttendanceStatus.ABSENT)
                    .build();
            try {
                attendanceRepository.saveAndFlush(absent);
            } catch (DataIntegrityViolationException ignored) {
                // Ca này đã có bản ghi chấm công.
            }
        }
    }

    private void checkInStaff(AuthenticatedUser user, Employee employee,
                              AttendanceLocation location, LocalDateTime now, byte[] photo) {
        StaffCheckInPlan plan = planStaffCheckIn(user, employee, location, now);
        Attendance attendance = Attendance.builder()
                .employee(employee)
                .store(plan.store())
                .shift(plan.chosen().assignment().getShift())
                .workDate(plan.chosen().assignment().getWorkDate())
                .scheduledShiftName(plan.chosen().assignment().getShift().getShiftName())
                .scheduledStartTime(plan.chosen().assignment().getShift().getStartTime())
                .scheduledEndTime(plan.chosen().assignment().getShift().getEndTime())
                .checkInTime(now)
                .checkInLatitude(location.latitude())
                .checkInLongitude(location.longitude())
                .lateMinutes(plan.lateMinutes())
                .earlyLeaveMinutes(0)
                .status(plan.lateMinutes() > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
                .build();
        attachPhoto(saveNew(attendance, ALREADY_IN), true, photo);
    }

    private StaffCheckInPlan planStaffCheckIn(AuthenticatedUser user, Employee employee,
                                              AttendanceLocation location, LocalDateTime now) {
        List<ShiftWindow> windows = loadWindows(employee.getId(), now.toLocalDate());
        ShiftWindow chosen = selectCheckInWindow(windows, now, employee.getId());
        if (findAttendance(employee.getId(), chosen).isPresent()) {
            throw new BusinessException(ALREADY_IN);
        }
        Store store = storeForShift(user, employee, chosen.assignment().getShift());
        assertInsideStore(store, location);
        return new StaffCheckInPlan(chosen, store, lateMinutes(chosen.shiftStart(), now));
    }

    private void checkInStaffSlot(AuthenticatedUser user, Employee employee, Integer assignmentId,
                                  AttendanceLocation location, LocalDateTime now, byte[] photo) {
        StaffCheckInPlan plan = planStaffSlot(user, employee, assignmentId, location, now);
        ShiftAssignment assignment = plan.chosen().assignment();
        Attendance attendance = Attendance.builder()
                .employee(employee)
                .store(plan.store())
                .shift(assignment.getShift())
                .workDate(assignment.getWorkDate())
                .scheduledShiftName(assignment.getShift().getShiftName())
                .scheduledStartTime(assignment.getShift().getStartTime())
                .scheduledEndTime(assignment.getShift().getEndTime())
                .checkInTime(now)
                .checkInLatitude(location.latitude())
                .checkInLongitude(location.longitude())
                .lateMinutes(plan.lateMinutes())
                .earlyLeaveMinutes(0)
                .status(plan.lateMinutes() > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
                .build();
        attachPhoto(saveNew(attendance, ALREADY_IN), true, photo);
    }

    private StaffCheckInPlan planStaffSlot(AuthenticatedUser user, Employee employee, Integer assignmentId,
                                           AttendanceLocation location, LocalDateTime now) {
        ShiftWindow chosen = requireStaffSlot(employee, assignmentId);
        if (now.isBefore(chosen.windowOpen())) {
            throw new BusinessException(TOO_EARLY);
        }
        if (!now.isBefore(chosen.shiftEnd())) {
            throw new BusinessException(SLOT_CLOSED);
        }
        if (findAttendance(employee.getId(), chosen).isPresent()) {
            throw new BusinessException(ALREADY_IN);
        }
        Store store = storeForShift(user, employee, chosen.assignment().getShift());
        assertInsideStore(store, location);
        return new StaffCheckInPlan(chosen, store, lateMinutes(chosen.shiftStart(), now));
    }

    private void checkInManager(AuthenticatedUser user, Employee employee,
                                AttendanceLocation location, LocalDateTime now, byte[] photo) {
        Store store = planManagerCheckIn(user, employee, location, now);
        Attendance attendance = Attendance.builder()
                .employee(employee)
                .store(store)
                .workDate(now.toLocalDate())
                .checkInTime(now)
                .checkInLatitude(location.latitude())
                .checkInLongitude(location.longitude())
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .status(AttendanceStatus.PRESENT)
                .build();
        attachPhoto(saveNew(attendance, MANAGER_ALREADY_IN), true, photo);
    }

    private Store planManagerCheckIn(AuthenticatedUser user, Employee employee,
                                     AttendanceLocation location, LocalDateTime now) {
        assertManagerCanCheckIn(employee.getId(), now.toLocalDate());
        Store store = latestStore(resolveManagedStore(user).getId(), STORE_LOCATION_MISSING);
        assertInsideStore(store, location);
        return store;
    }

    private void checkOutStaff(Employee employee, AttendanceLocation location, LocalDateTime now, byte[] photo) {
        completeCheckout(locateStaffCheckout(employee, now), now, location, photo);
    }

    private void checkOutStaffSlot(AuthenticatedUser user, Employee employee, Integer assignmentId,
                                   AttendanceLocation location, LocalDateTime now, byte[] photo) {
        completeCheckout(locateStaffSlot(user, employee, assignmentId), now, location, photo);
    }

    private Attendance locateStaffSlot(AuthenticatedUser user, Employee employee, Integer assignmentId) {
        ShiftWindow chosen = requireStaffSlot(employee, assignmentId);
        storeForShift(user, employee, chosen.assignment().getShift());
        Attendance attendance = findAttendance(employee.getId(), chosen)
                .orElseThrow(() -> new BusinessException(NOT_CHECKED_IN));
        if (attendance.getCheckInTime() == null) {
            throw new BusinessException(NOT_CHECKED_IN);
        }
        if (attendance.getCheckOutTime() != null) {
            throw new BusinessException(ALREADY_OUT);
        }
        return attendance;
    }

    private ShiftWindow requireStaffSlot(Employee employee, Integer assignmentId) {
        if (assignmentId == null) {
            throw new BusinessException(SLOT_REQUIRED);
        }
        ShiftAssignment assignment = shiftAssignmentRepository.findByIdWithDetails(assignmentId)
                .orElseThrow(() -> new BusinessException(NO_SHIFT));
        if (assignment.getEmployee() == null || !employee.getId().equals(assignment.getEmployee().getId())) {
            throw new BusinessException(SLOT_FORBIDDEN);
        }
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED
                || Boolean.FALSE.equals(assignment.getIsPublished())
                || assignment.getShift() == null
                || assignment.getShift().getStartTime() == null
                || assignment.getShift().getEndTime() == null) {
            throw new BusinessException(NO_SHIFT);
        }
        return windowOf(assignment);
    }

    private Attendance locateStaffCheckout(Employee employee, LocalDateTime now) {
        List<Attendance> openRecords = attendanceRepository.findOpenByEmployeeId(employee.getId());
        if (!openRecords.isEmpty()) {
            return openRecords.getFirst();
        }

        List<ShiftWindow> windows = loadWindows(employee.getId(), now.toLocalDate());
        Optional<ShiftWindow> relevant = relevantWindow(windows, now);
        if (relevant.isEmpty()) {
            throw new BusinessException(NO_SHIFT);
        }
        Optional<Attendance> existing = findAttendance(employee.getId(), relevant.get());
        if (existing.isPresent() && existing.get().getCheckOutTime() != null) {
            throw new BusinessException(ALREADY_OUT);
        }
        throw new BusinessException(NOT_CHECKED_IN);
    }

    private void checkOutManager(Employee employee, AttendanceLocation location, LocalDateTime now, byte[] photo) {
        completeCheckout(locateManagerCheckout(employee, now), now, location, photo);
    }

    private Attendance locateManagerCheckout(Employee employee, LocalDateTime now) {
        List<Attendance> openRecords = attendanceRepository.findOpenByEmployeeId(employee.getId());
        if (!openRecords.isEmpty()) {
            return openRecords.getFirst();
        }
        boolean checkedOutToday = attendanceRepository
                .findByEmployee_IdAndWorkDateOrderByIdDesc(employee.getId(), now.toLocalDate())
                .stream()
                .anyMatch(attendance -> attendance.getCheckOutTime() != null);
        if (checkedOutToday) {
            throw new BusinessException(MANAGER_ALREADY_OUT);
        }
        throw new BusinessException(MANAGER_NOT_IN);
    }

    private void assertManagerCanCheckIn(Integer employeeId, LocalDate today) {
        List<Attendance> openRecords = attendanceRepository.findOpenByEmployeeId(employeeId);
        if (!openRecords.isEmpty()) {
            Attendance open = openRecords.getFirst();
            if (today.equals(open.getWorkDate())) {
                throw new BusinessException(MANAGER_ALREADY_IN);
            }
            throw new BusinessException(MANAGER_OPEN_RECORD);
        }
        if (!attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(employeeId, today).isEmpty()) {
            throw new BusinessException(MANAGER_ALREADY_IN);
        }
    }

    private void completeCheckout(Attendance attendance, LocalDateTime now, AttendanceLocation location, byte[] photo) {
        assertCheckoutReady(attendance, now, location);
        int earlyMinutes = 0;
        int lateCheckoutMinutes = 0;
        LocalDateTime scheduledEnd = scheduledEnd(attendance);
        if (scheduledEnd != null && now.isBefore(scheduledEnd)) {
            earlyMinutes = (int) ChronoUnit.MINUTES.between(now, scheduledEnd);
        } else if (scheduledEnd != null) {
            long afterEnd = ChronoUnit.MINUTES.between(scheduledEnd, now);
            if (afterEnd >= lateCheckoutThreshold()) {
                lateCheckoutMinutes = (int) afterEnd;
            }
        }
        LocalDateTime checkIn = attendance.getCheckInTime() == null ? now : attendance.getCheckInTime();
        int workingMinutes = (int) Math.max(0, ChronoUnit.MINUTES.between(checkIn, now));
        int lateMinutes = attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        attendance.setCheckOutTime(now);
        attendance.setCheckOutLatitude(location.latitude());
        attendance.setCheckOutLongitude(location.longitude());
        attendance.setWorkingMinutes(workingMinutes);
        attendance.setEarlyLeaveMinutes(Math.max(earlyMinutes, 0));
        attendance.setStatus(resolveStatus(lateMinutes, earlyMinutes, lateCheckoutMinutes));
        if (photo != null && attendance.getId() != null) {
            attendance.setCheckOutPhotoName(photoStore.save(attendance.getId(), "out", photo));
        }
        attendanceRepository.save(attendance);
        if (reliabilityService != null) {
            reliabilityService.recordCompletedSwappedShiftOnCheckout(attendance);
        }
    }

    private void assertCheckoutReady(Attendance attendance, LocalDateTime now, AttendanceLocation location) {
        if (attendance.getCheckOutTime() != null) {
            throw new BusinessException(attendance.getShift() == null ? MANAGER_ALREADY_OUT : ALREADY_OUT);
        }
        if (!checkoutAllowed(attendance, now)) {
            throw new BusinessException(CHECKOUT_TOO_SOON);
        }
        Store store = storeOf(attendance, attendance.getEmployee());
        assertInsideStore(store, location);
        if (!policy.isAllowEarlyCheckout()) {
            LocalDateTime scheduledEnd = scheduledEnd(attendance);
            if (scheduledEnd != null && now.isBefore(scheduledEnd)) {
                throw new BusinessException(EARLY_CHECKOUT_BLOCKED);
            }
        }
    }

    private void assertInsideStore(Store store, AttendanceLocation location) {
        if (store == null
                || !GeoDistance.isValidLatitude(store.getLatitude())
                || !GeoDistance.isValidLongitude(store.getLongitude())) {
            throw new BusinessException(STORE_LOCATION_MISSING);
        }
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

    private AttendanceClockView buildClock(AuthenticatedUser user, LocalDateTime rawNow) {
        Employee employee = resolveCheckEmployee(user);
        LocalDateTime now = truncate(rawNow);
        boolean manager = user.getRoleName() == RoleName.MANAGER;
        Store managedStore = null;
        if (manager && user.getEmployeeId() != null) {
            managedStore = storeRepository.findByManager_Id(user.getEmployeeId()).orElse(null);
        }
        Store displayStore = managedStore != null ? managedStore : employee.getStore();

        String shiftName = null;
        String workDateLabel = null;
        String startLabel = "—";
        String endLabel = "—";
        String statusLabel = manager ? "Chưa Check-in" : NO_SHIFT;
        boolean canCheckOut = true;
        boolean canCheckIn = manager;
        String checkInNotice = manager ? null : NO_SHIFT;
        String checkOutNotice = null;
        if (manager) {
            statusLabel = managerStatus(employee.getId(), now.toLocalDate());
        } else {
            List<Attendance> openRecords = attendanceRepository.findOpenByEmployeeId(employee.getId());
            if (!openRecords.isEmpty()) {
                Attendance open = openRecords.getFirst();
                canCheckOut = checkoutAllowed(open, now);
                canCheckIn = false;
                checkInNotice = canCheckOut
                        ? ALREADY_IN
                        : "Bạn đã Check-in. Check-out mở sau 2 tiếng kể từ giờ vào ca.";
                if (!canCheckOut) {
                    checkOutNotice = "Check-out mở sau 2 tiếng kể từ giờ vào ca.";
                }
                shiftName = open.getScheduledShiftName();
                workDateLabel = formatDate(open.getWorkDate());
                startLabel = formatTime(open.getScheduledStartTime());
                endLabel = formatTime(open.getScheduledEndTime());
                statusLabel = statusText(open.getStatus());
            } else {
                List<ShiftWindow> windows = loadWindows(employee.getId(), now.toLocalDate());
                Optional<ShiftWindow> current = firstEligible(windows, now);
                if (current.isPresent()) {
                    ShiftWindow window = current.get();
                    shiftName = window.assignment().getShift().getShiftName();
                    workDateLabel = formatDate(window.assignment().getWorkDate());
                    startLabel = formatTime(window.assignment().getShift().getStartTime());
                    endLabel = formatTime(window.assignment().getShift().getEndTime());
                    Optional<Attendance> existing = findAttendance(employee.getId(), window);
                    if (existing.isPresent()) {
                        statusLabel = statusText(existing.get().getStatus());
                        canCheckIn = false;
                        checkInNotice = existing.get().getCheckOutTime() == null ? ALREADY_IN : ALREADY_OUT;
                    } else {
                        statusLabel = "Chưa Check-in";
                        canCheckIn = true;
                        checkInNotice = null;
                    }
                } else {
                    Optional<ShiftWindow> upcoming = windows.stream()
                            .filter(window -> now.isBefore(window.windowOpen()))
                            .min(Comparator.comparing(ShiftWindow::shiftStart));
                    if (upcoming.isPresent()) {
                        Shift shift = upcoming.get().assignment().getShift();
                        shiftName = shift.getShiftName();
                        workDateLabel = formatDate(upcoming.get().assignment().getWorkDate());
                        startLabel = formatTime(shift.getStartTime());
                        endLabel = formatTime(shift.getEndTime());
                        statusLabel = TOO_EARLY;
                        canCheckIn = false;
                        checkInNotice = TOO_EARLY;
                    }
                }
            }
        }

        List<AttendanceClockView.RecentItem> recent = attendanceRepository
                .findRecentByEmployee(employee.getId(), PageRequest.of(0, 5))
                .stream()
                .map(this::toRecentItem)
                .toList();
        List<AttendanceClockView.Slot> slots = manager ? List.of() : staffSlots(employee, now);

        return AttendanceClockView.builder()
                .employeeName(employee.getFullName())
                .storeName(displayStore != null ? displayStore.getStoreName() : null)
                .shiftName(shiftName)
                .workDateLabel(workDateLabel)
                .scheduledStartLabel(startLabel)
                .scheduledEndLabel(endLabel)
                .statusLabel(statusLabel)
                .manager(manager)
                .canCheckIn(canCheckIn)
                .checkInNotice(checkInNotice)
                .canCheckOut(canCheckOut)
                .checkOutNotice(checkOutNotice)
                .recent(recent)
                .slots(slots)
                .build();
    }

    private List<AttendanceClockView.Slot> staffSlots(Employee employee, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<ShiftWindow> windows = loadWindows(employee.getId(), today);
        List<Attendance> openRecords = attendanceRepository.findOpenByEmployeeId(employee.getId());
        List<ShiftWindow> visible = new ArrayList<>();
        for (ShiftWindow window : windows) {
            LocalDate workDate = window.assignment().getWorkDate();
            if (workDate.isAfter(today)) {
                continue;
            }
            boolean stillOpen = openRecords.stream().anyMatch(attendance -> sameSlot(attendance, window));
            if (workDate.equals(today) || stillOpen || now.isBefore(window.shiftEnd())) {
                visible.add(window);
            }
        }
        visible.sort(Comparator.comparing(ShiftWindow::shiftStart));
        List<AttendanceClockView.Slot> slots = new ArrayList<>();
        for (ShiftWindow window : visible) {
            Attendance attendance = findAttendance(employee.getId(), window).orElse(null);
            slots.add(toClockSlot(window, attendance, now));
        }
        return slots;
    }

    private boolean sameSlot(Attendance attendance, ShiftWindow window) {
        return attendance.getShift() != null
                && window.assignment().getShift() != null
                && attendance.getShift().getId().equals(window.assignment().getShift().getId())
                && window.assignment().getWorkDate().equals(attendance.getWorkDate());
    }

    private AttendanceClockView.Slot toClockSlot(ShiftWindow window, Attendance attendance, LocalDateTime now) {
        boolean checkedOut = attendance != null && attendance.getCheckOutTime() != null;
        boolean checkedIn = attendance != null && attendance.getCheckInTime() != null;
        boolean absent = attendance != null && attendance.getStatus() == AttendanceStatus.ABSENT;
        boolean tooEarly = now.isBefore(window.windowOpen());
        boolean closed = !now.isBefore(window.shiftEnd());
        boolean canCheckIn = attendance == null && !tooEarly && !closed;
        String checkInNotice = null;
        if (!canCheckIn) {
            if (checkedIn && !checkedOut) {
                checkInNotice = ALREADY_IN;
            } else if (checkedOut) {
                checkInNotice = ALREADY_OUT;
            } else if (absent) {
                checkInNotice = "Ca này đã được ghi nhận vắng.";
            } else if (tooEarly) {
                checkInNotice = TOO_EARLY;
            } else if (closed) {
                checkInNotice = SLOT_CLOSED;
            } else {
                checkInNotice = ALREADY_IN;
            }
        }
        boolean canCheckOut = checkedIn && !checkedOut && checkoutAllowed(attendance, now);
        String checkOutNotice = null;
        if (!canCheckOut) {
            if (!checkedIn) {
                checkOutNotice = NOT_CHECKED_IN;
            } else if (checkedOut) {
                checkOutNotice = ALREADY_OUT;
            } else {
                checkOutNotice = "Check-out mở sau 2 tiếng kể từ giờ vào ca.";
            }
        }
        String statusKey;
        String statusLabel;
        if (checkedOut) {
            statusKey = "done";
            statusLabel = "Đã hoàn thành";
        } else if (checkedIn) {
            statusKey = "working";
            statusLabel = "Đang làm";
        } else if (absent) {
            statusKey = "absent";
            statusLabel = "Vắng";
        } else if (tooEarly) {
            statusKey = "upcoming";
            statusLabel = "Chưa đến giờ";
        } else if (closed) {
            statusKey = "closed";
            statusLabel = "Đã hết giờ";
        } else {
            statusKey = "ready";
            statusLabel = "Chưa Check-in";
        }
        Shift shift = window.assignment().getShift();
        return AttendanceClockView.Slot.builder()
                .assignmentId(window.assignment().getId())
                .shiftName(shift.getShiftName())
                .scheduleLabel(formatTime(shift.getStartTime()) + " – " + formatTime(shift.getEndTime()))
                .statusKey(statusKey)
                .statusLabel(statusLabel)
                .checkInLabel(checkedIn ? attendance.getCheckInTime().format(TIME_FORMATTER) : "—")
                .checkOutLabel(checkedOut ? attendance.getCheckOutTime().format(TIME_FORMATTER) : "—")
                .canCheckIn(canCheckIn)
                .checkInNotice(checkInNotice)
                .canCheckOut(canCheckOut)
                .checkOutNotice(checkOutNotice)
                .build();
    }

    private String managerStatus(Integer employeeId, LocalDate today) {
        List<Attendance> openRecords = attendanceRepository.findOpenByEmployeeId(employeeId);
        if (!openRecords.isEmpty()) {
            return statusText(openRecords.getFirst().getStatus());
        }
        List<Attendance> todayRecords = attendanceRepository.findByEmployee_IdAndWorkDateOrderByIdDesc(employeeId, today);
        if (!todayRecords.isEmpty()) {
            return statusText(todayRecords.getFirst().getStatus());
        }
        return "Chưa Check-in";
    }

    private AttendanceClockView.RecentItem toRecentItem(Attendance attendance) {
        String checkIn = attendance.getCheckInTime() == null
                ? "—"
                : attendance.getCheckInTime().format(TIME_FORMATTER);
        String checkOut = attendance.getCheckOutTime() == null
                ? "—"
                : attendance.getCheckOutTime().format(TIME_FORMATTER);
        return AttendanceClockView.RecentItem.builder()
                .workDate(attendance.getWorkDate())
                .timeLabel(checkIn + " - " + checkOut)
                .build();
    }

    private ShiftWindow selectCheckInWindow(List<ShiftWindow> windows, LocalDateTime now, Integer employeeId) {
        List<ShiftWindow> eligible = eligible(windows, now);
        if (eligible.isEmpty()) {
            boolean upcoming = windows.stream().anyMatch(window -> now.isBefore(window.windowOpen()));
            if (upcoming) {
                throw new BusinessException(TOO_EARLY);
            }
            throw new BusinessException(NO_SHIFT);
        }
        ShiftWindow alreadyCheckedIn = null;
        for (ShiftWindow window : eligible) {
            if (findAttendance(employeeId, window).isEmpty()) {
                return window;
            }
            if (alreadyCheckedIn == null) {
                alreadyCheckedIn = window;
            }
        }
        return alreadyCheckedIn;
    }

    private Optional<ShiftWindow> relevantWindow(List<ShiftWindow> windows, LocalDateTime now) {
        Optional<ShiftWindow> eligible = firstEligible(windows, now);
        if (eligible.isPresent()) {
            return eligible;
        }
        return windows.stream()
                .filter(window -> !now.isBefore(window.windowOpen()))
                .max(Comparator.comparing(ShiftWindow::shiftStart));
    }

    private Optional<ShiftWindow> firstEligible(List<ShiftWindow> windows, LocalDateTime now) {
        return eligible(windows, now).stream().findFirst();
    }

    private List<ShiftWindow> eligible(List<ShiftWindow> windows, LocalDateTime now) {
        return windows.stream()
                .filter(window -> !now.isBefore(window.windowOpen()) && now.isBefore(window.shiftEnd()))
                .sorted(Comparator
                        .comparing((ShiftWindow window) -> now.isBefore(window.shiftStart()))
                        .thenComparing(ShiftWindow::shiftStart))
                .toList();
    }

    private List<ShiftWindow> loadWindows(Integer employeeId, LocalDate day) {
        return shiftAssignmentRepository.findAssignmentsForAttendance(
                        employeeId, day.minusDays(1), day.plusDays(1), AssignmentStatus.ASSIGNED)
                .stream()
                .filter(assignment -> assignment.getIsPublished() == null || Boolean.TRUE.equals(assignment.getIsPublished()))
                .filter(assignment -> assignment.getShift() != null
                        && assignment.getShift().getStartTime() != null
                        && assignment.getShift().getEndTime() != null)
                .map(this::windowOf)
                .toList();
    }

    private ShiftWindow windowOf(ShiftAssignment assignment) {
        LocalDateTime start = LocalDateTime.of(assignment.getWorkDate(), assignment.getShift().getStartTime());
        LocalDateTime end = LocalDateTime.of(assignment.getWorkDate(), assignment.getShift().getEndTime());
        start = truncate(start);
        end = truncate(end);
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        return new ShiftWindow(assignment, start.minusMinutes(leadMinutes()), start, end);
    }

    private Optional<Attendance> findAttendance(Integer employeeId, ShiftWindow window) {
        return attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(
                employeeId,
                window.assignment().getShift().getId(),
                window.assignment().getWorkDate());
    }

    private Store storeForShift(AuthenticatedUser user, Employee employee, Shift shift) {
        if (shift == null || shift.getStore() == null) {
            throw new BusinessException(NO_SHIFT);
        }
        if (user.getRoleName() == RoleName.MANAGER) {
            Store managed = resolveManagedStore(user);
            if (!managed.getId().equals(shift.getStore().getId())) {
                throw new BusinessException(NO_SHIFT);
            }
        } else if (employee.getStore() == null || !employee.getStore().getId().equals(shift.getStore().getId())) {
            throw new BusinessException(NO_SHIFT);
        }
        return latestStore(shift.getStore().getId(), NO_SHIFT);
    }

    private Store storeOf(Attendance attendance, Employee employee) {
        Integer storeId = attendance.getStore() != null
                ? attendance.getStore().getId()
                : (employee.getStore() != null ? employee.getStore().getId() : null);
        return latestStore(storeId, STORE_LOCATION_MISSING);
    }

    private Store latestStore(Integer storeId, String missingMessage) {
        if (storeId == null) {
            throw new BusinessException(missingMessage);
        }
        return storeRepository.findById(storeId).orElseThrow(() -> new BusinessException(missingMessage));
    }

    private Attendance saveNew(Attendance attendance, String duplicateMessage) {
        try {
            return attendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(duplicateMessage);
        }
    }

    private void attachPhoto(Attendance attendance, boolean checkIn, byte[] photo) {
        if (photo == null || attendance == null || attendance.getId() == null) {
            return;
        }
        String name = photoStore.save(attendance.getId(), checkIn ? "in" : "out", photo);
        if (checkIn) {
            attendance.setCheckInPhotoName(name);
        } else {
            attendance.setCheckOutPhotoName(name);
        }
        attendanceRepository.save(attendance);
    }

    private void requirePhoto(byte[] photo) {
        if (!AttendancePhotoStore.isJpeg(photo)) {
            throw new BusinessException(photo == null || photo.length == 0 ? PHOTO_REQUIRED : AttendancePhotoStore.PHOTO_INVALID);
        }
    }

    private void assertCanViewPhoto(AuthenticatedUser user, Attendance attendance) {
        if (user == null || attendance.getEmployee() == null) {
            throw new BusinessException("Không tìm thấy ảnh.");
        }
        if (user.getRoleName() == RoleName.ADMIN) {
            return;
        }
        if (user.getRoleName() == RoleName.STAFF) {
            if (user.getEmployeeId() != null && user.getEmployeeId().equals(attendance.getEmployee().getId())) {
                return;
            }
            throw new BusinessException("Không tìm thấy ảnh.");
        }
        if (user.getRoleName() == RoleName.MANAGER) {
            Store managed = resolveManagedStore(user);
            Integer storeId = attendance.getStore() != null
                    ? attendance.getStore().getId()
                    : (attendance.getEmployee().getStore() != null ? attendance.getEmployee().getStore().getId() : null);
            if (managed.getId().equals(storeId)) {
                return;
            }
        }
        throw new BusinessException("Không tìm thấy ảnh.");
    }

    private record StaffCheckInPlan(ShiftWindow chosen, Store store, int lateMinutes) {
    }

    private int lateMinutes(LocalDateTime shiftStart, LocalDateTime checkIn) {
        long minutes = ChronoUnit.MINUTES.between(shiftStart, checkIn);
        if (minutes < lateThreshold()) {
            return 0;
        }
        return (int) minutes;
    }

    private AttendanceStatus resolveStatus(int lateMinutes, int earlyMinutes, int lateCheckoutMinutes) {
        boolean late = lateMinutes > 0;
        boolean early = earlyMinutes > 0;
        boolean lateCheckout = lateCheckoutMinutes > 0;
        if (late && early) {
            return AttendanceStatus.LATE_AND_EARLY;
        }
        if (early) {
            return AttendanceStatus.EARLY_LEAVE;
        }
        if (late && lateCheckout) {
            return AttendanceStatus.LATE_AND_LATE_CHECKOUT;
        }
        if (lateCheckout) {
            return AttendanceStatus.LATE_CHECKOUT;
        }
        if (late) {
            return AttendanceStatus.LATE;
        }
        return AttendanceStatus.PRESENT;
    }

    private int lateCheckoutThreshold() {
        return Math.max(policy.getLateCheckoutThresholdMinutes(), 0);
    }

    private boolean checkoutAllowed(Attendance attendance, LocalDateTime now) {
        LocalDateTime shiftStart = scheduledStart(attendance);
        if (shiftStart == null) {
            return true;
        }
        return !now.isBefore(shiftStart.plusMinutes(minimumShiftMinutes()));
    }

    private int minimumShiftMinutes() {
        return Math.max(policy.getMinimumShiftMinutesBeforeCheckout(), 0);
    }

    private LocalDateTime scheduledStart(Attendance attendance) {
        if (attendance.getWorkDate() == null || attendance.getScheduledStartTime() == null) {
            return null;
        }
        return truncate(LocalDateTime.of(attendance.getWorkDate(), attendance.getScheduledStartTime()));
    }

    private LocalDateTime scheduledEnd(Attendance attendance) {
        if (attendance.getWorkDate() == null
                || attendance.getScheduledStartTime() == null
                || attendance.getScheduledEndTime() == null) {
            return null;
        }
        LocalDateTime start = truncate(LocalDateTime.of(attendance.getWorkDate(), attendance.getScheduledStartTime()));
        LocalDateTime end = truncate(LocalDateTime.of(attendance.getWorkDate(), attendance.getScheduledEndTime()));
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        return end;
    }

    private Employee resolveCheckEmployee(AuthenticatedUser user) {
        if (user == null || (user.getRoleName() != RoleName.STAFF && user.getRoleName() != RoleName.MANAGER)) {
            throw new BusinessException("Bạn không có quyền chấm công.");
        }
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa liên kết với hồ sơ nhân viên.");
        }
        return employeeRepository.findByIdWithStore(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin hồ sơ nhân viên."));
    }

    private Store resolveManagedStore(AuthenticatedUser user) {
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
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

    private LocalDateTime exactTime(LocalDateTime value) {
        return value.withNano(0);
    }

    private LocalDateTime truncate(LocalDateTime value) {
        return value.withSecond(0).withNano(0);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "—" : time.format(TIME_FORMATTER);
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FORMATTER);
    }

    private String statusText(AttendanceStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case PRESENT, ON_TIME -> "Có mặt";
            case LATE -> "Đi muộn";
            case EARLY_LEAVE -> "Về sớm";
            case LATE_AND_EARLY -> "Đi muộn và về sớm";
            case LATE_CHECKOUT -> "Check-out muộn";
            case LATE_AND_LATE_CHECKOUT -> "Đi muộn và check-out muộn";
            case NOT_PARTICIPATED -> "Không tham gia";
            case ABSENT -> "Vắng";
        };
    }

    private record ShiftWindow(ShiftAssignment assignment,
                               LocalDateTime windowOpen,
                               LocalDateTime shiftStart,
                               LocalDateTime shiftEnd) {
    }
}
