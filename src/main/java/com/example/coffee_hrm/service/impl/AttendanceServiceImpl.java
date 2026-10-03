package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.common.web.ClientIpResolver;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    static final String WRONG_IP = "Sai địa chỉ IP";
    static final String TOO_EARLY = "Chưa đến thời gian Check-in";
    static final String NO_SHIFT = "Bạn không có ca làm việc tại thời điểm hiện tại.";
    static final String ALREADY_IN = "Bạn đã Check-in cho ca làm việc này.";
    static final String NOT_CHECKED_IN = "Bạn chưa Check-in cho ca làm việc này.";
    static final String ALREADY_OUT = "Bạn đã Check-out cho ca làm việc này.";
    static final String EARLY_CHECKOUT_BLOCKED = "Chưa đến giờ kết thúc ca. Không thể Check-out sớm.";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRepository attendanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final AttendancePolicyProperties policy;

    @Override
    @Transactional(readOnly = true)
    public AttendanceClockView getClock(AuthenticatedUser user, String requestIp) {
        return buildClock(user, requestIp, VietnamTime.now());
    }

    @Override
    @Transactional
    public void checkIn(AuthenticatedUser user, String requestIp) {
        checkIn(user, requestIp, VietnamTime.now());
    }

    @Override
    @Transactional
    public void checkOut(AuthenticatedUser user, String requestIp) {
        checkOut(user, requestIp, VietnamTime.now());
    }

    @Override
    @Transactional
    public void updateStoreIp(AuthenticatedUser user, String requestIp) {
        if (user == null || (user.getRoleName() != RoleName.STAFF && user.getRoleName() != RoleName.MANAGER)) {
            throw new BusinessException("Bạn không có quyền cập nhật IP cửa hàng.");
        }
        Store store = user.getRoleName() == RoleName.MANAGER
                ? resolveManagedStore(user)
                : storeOfEmployee(resolveCheckEmployee(user));
        String ip = ClientIpResolver.normalize(requestIp);
        if (ip == null) {
            throw new BusinessException("Không xác định được địa chỉ IP.");
        }
        store.setCurrentIp(ip);
        storeRepository.save(store);
    }

    @Override
    @Transactional
    public void markAbsences(LocalDate workDate) {
        markAbsences(workDate, VietnamTime.now());
    }

    void checkIn(AuthenticatedUser user, String requestIp, LocalDateTime rawNow) {
        Employee employee = resolveCheckEmployee(user);
        LocalDateTime now = truncate(rawNow);
        List<ShiftWindow> windows = loadWindows(employee.getId(), now.toLocalDate());
        ShiftWindow chosen = selectCheckInWindow(windows, now, employee.getId());
        Store store = storeForShift(user, employee, chosen.assignment().getShift());
        assertIp(store, requestIp);
        if (findAttendance(employee.getId(), chosen).isPresent()) {
            throw new BusinessException(ALREADY_IN);
        }

        int lateMinutes = lateMinutes(chosen.shiftStart(), now);
        Attendance attendance = Attendance.builder()
                .employee(employee)
                .store(store)
                .shift(chosen.assignment().getShift())
                .workDate(chosen.assignment().getWorkDate())
                .scheduledShiftName(chosen.assignment().getShift().getShiftName())
                .scheduledStartTime(chosen.assignment().getShift().getStartTime())
                .scheduledEndTime(chosen.assignment().getShift().getEndTime())
                .checkInTime(now)
                .checkInIp(ClientIpResolver.normalize(requestIp))
                .lateMinutes(lateMinutes)
                .earlyLeaveMinutes(0)
                .status(lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
                .build();
        try {
            attendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ALREADY_IN);
        }
    }

    void checkOut(AuthenticatedUser user, String requestIp, LocalDateTime rawNow) {
        Employee employee = resolveCheckEmployee(user);
        LocalDateTime now = truncate(rawNow);
        List<Attendance> openRecords = attendanceRepository.findOpenByEmployeeId(employee.getId());
        if (!openRecords.isEmpty()) {
            completeCheckout(openRecords.getFirst(), now, requestIp);
            return;
        }

        List<ShiftWindow> windows = loadWindows(employee.getId(), now.toLocalDate());
        Optional<ShiftWindow> relevant = relevantWindow(windows, now);
        if (relevant.isEmpty()) {
            throw new BusinessException(NO_SHIFT);
        }
        Optional<Attendance> existing = findAttendance(employee.getId(), relevant.get());
        if (existing.isPresent() && existing.get().getCheckOutTime() != null) {
            Store store = storeOf(existing.get(), employee);
            assertIp(store, requestIp);
            throw new BusinessException(ALREADY_OUT);
        }
        throw new BusinessException(NOT_CHECKED_IN);
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

    private void completeCheckout(Attendance attendance, LocalDateTime now, String requestIp) {
        if (attendance.getCheckOutTime() != null) {
            throw new BusinessException(ALREADY_OUT);
        }
        Store store = storeOf(attendance, attendance.getEmployee());
        // So với IP cửa hàng tại thời điểm check-out, không bắt checkOutIp trùng checkInIp.
        assertIp(store, requestIp);
        LocalDateTime scheduledEnd = scheduledEnd(attendance);
        if (!policy.isAllowEarlyCheckout() && scheduledEnd != null && now.isBefore(scheduledEnd)) {
            throw new BusinessException(EARLY_CHECKOUT_BLOCKED);
        }
        int earlyMinutes = 0;
        if (scheduledEnd != null && now.isBefore(scheduledEnd)) {
            earlyMinutes = (int) ChronoUnit.MINUTES.between(now, scheduledEnd);
        }
        LocalDateTime checkIn = attendance.getCheckInTime() == null
                ? now
                : truncate(attendance.getCheckInTime());
        int workingMinutes = (int) Math.max(0, ChronoUnit.MINUTES.between(checkIn, now));
        int lateMinutes = attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        attendance.setCheckOutTime(now);
        attendance.setCheckOutIp(ClientIpResolver.normalize(requestIp));
        attendance.setWorkingMinutes(workingMinutes);
        attendance.setEarlyLeaveMinutes(earlyMinutes);
        attendance.setStatus(resolveStatus(lateMinutes, earlyMinutes));
        attendanceRepository.save(attendance);
    }

    private AttendanceClockView buildClock(AuthenticatedUser user, String requestIp, LocalDateTime rawNow) {
        Employee employee = resolveCheckEmployee(user);
        LocalDateTime now = truncate(rawNow);
        boolean manager = user.getRoleName() == RoleName.MANAGER;
        Store managedStore = null;
        if (manager && user.getEmployeeId() != null) {
            managedStore = storeRepository.findByManager_Id(user.getEmployeeId()).orElse(null);
        }
        Store displayStore = managedStore != null ? managedStore : employee.getStore();

        String shiftName = null;
        String startLabel = "—";
        String endLabel = "—";
        String statusLabel = NO_SHIFT;
        List<Attendance> openRecords = attendanceRepository.findOpenByEmployeeId(employee.getId());
        if (!openRecords.isEmpty()) {
            Attendance open = openRecords.getFirst();
            shiftName = open.getScheduledShiftName();
            startLabel = formatTime(open.getScheduledStartTime());
            endLabel = formatTime(open.getScheduledEndTime());
            statusLabel = statusText(open.getStatus());
        } else {
            List<ShiftWindow> windows = loadWindows(employee.getId(), now.toLocalDate());
            Optional<ShiftWindow> current = firstEligible(windows, now);
            if (current.isPresent()) {
                ShiftWindow window = current.get();
                shiftName = window.assignment().getShift().getShiftName();
                startLabel = formatTime(window.assignment().getShift().getStartTime());
                endLabel = formatTime(window.assignment().getShift().getEndTime());
                Optional<Attendance> existing = findAttendance(employee.getId(), window);
                if (existing.isPresent() && existing.get().getCheckOutTime() != null) {
                    statusLabel = statusText(existing.get().getStatus());
                } else if (existing.isPresent()) {
                    statusLabel = statusText(existing.get().getStatus());
                } else {
                    statusLabel = "Chưa Check-in";
                }
            } else {
                Optional<ShiftWindow> upcoming = windows.stream()
                        .filter(window -> now.isBefore(window.windowOpen()))
                        .min(Comparator.comparing(ShiftWindow::shiftStart));
                if (upcoming.isPresent()) {
                    Shift shift = upcoming.get().assignment().getShift();
                    shiftName = shift.getShiftName();
                    startLabel = formatTime(shift.getStartTime());
                    endLabel = formatTime(shift.getEndTime());
                    statusLabel = TOO_EARLY;
                }
            }
        }

        List<AttendanceClockView.RecentItem> recent = attendanceRepository
                .findRecentByEmployee(employee.getId(), PageRequest.of(0, 5))
                .stream()
                .map(this::toRecentItem)
                .toList();

        return AttendanceClockView.builder()
                .employeeName(employee.getFullName())
                .storeName(displayStore != null ? displayStore.getStoreName() : null)
                .shiftName(shiftName)
                .scheduledStartLabel(startLabel)
                .scheduledEndLabel(endLabel)
                .statusLabel(statusLabel)
                .manager(manager)
                .storeCurrentIp(displayStore != null ? displayStore.getCurrentIp() : null)
                .requestIp(ClientIpResolver.normalize(requestIp))
                .recent(recent)
                .build();
    }

    private AttendanceClockView.RecentItem toRecentItem(Attendance attendance) {
        String checkIn = attendance.getCheckInTime() == null
                ? "—"
                : truncate(attendance.getCheckInTime()).format(TIME_FORMATTER);
        String checkOut = attendance.getCheckOutTime() == null
                ? "—"
                : truncate(attendance.getCheckOutTime()).format(TIME_FORMATTER);
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
                .filter(assignment -> Boolean.TRUE.equals(assignment.getIsPublished()))
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
        return storeRepository.findById(shift.getStore().getId())
                .orElseThrow(() -> new BusinessException(NO_SHIFT));
    }

    private Store storeOfEmployee(Employee employee) {
        if (employee.getStore() == null) {
            throw new BusinessException("Tài khoản chưa được gắn cửa hàng.");
        }
        return storeRepository.findById(employee.getStore().getId())
                .orElseThrow(() -> new BusinessException("Tài khoản chưa được gắn cửa hàng."));
    }

    private Store storeOf(Attendance attendance, Employee employee) {
        Integer storeId = attendance.getStore() != null
                ? attendance.getStore().getId()
                : (employee.getStore() != null ? employee.getStore().getId() : null);
        if (storeId == null) {
            throw new BusinessException(WRONG_IP);
        }
        return storeRepository.findById(storeId).orElseThrow(() -> new BusinessException(WRONG_IP));
    }

    private void assertIp(Store store, String requestIp) {
        if (store == null || !ClientIpResolver.matches(requestIp, store.getCurrentIp())) {
            throw new BusinessException(WRONG_IP);
        }
    }

    private int lateMinutes(LocalDateTime shiftStart, LocalDateTime checkIn) {
        long minutes = ChronoUnit.MINUTES.between(shiftStart, checkIn);
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

    private LocalDateTime truncate(LocalDateTime value) {
        return value.withSecond(0).withNano(0);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "—" : time.format(TIME_FORMATTER);
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
            case ABSENT -> "Vắng";
        };
    }

    private record ShiftWindow(ShiftAssignment assignment,
                               LocalDateTime windowOpen,
                               LocalDateTime shiftStart,
                               LocalDateTime shiftEnd) {
    }
}
