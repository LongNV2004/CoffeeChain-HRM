package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceHistoryKind;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AttendanceHistoryQuery;
import com.example.coffee_hrm.dto.response.AttendanceHistoryView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingAttendance;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingAttendanceRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AttendanceHistoryServiceImpl implements AttendanceHistoryService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final long MAX_RANGE_DAYS = 366;

    private final AttendanceRepository attendanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final ShiftRepository shiftRepository;
    private final TrainingAttendanceRepository trainingAttendanceRepository;

    @Override
    @Transactional(readOnly = true)
    public AttendanceHistoryView getStaffHistory(AuthenticatedUser user, LocalDate fromDate, LocalDate toDate) {
        return getStaffHistory(user, AttendanceHistoryQuery.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceHistoryView getStaffHistory(AuthenticatedUser user, AttendanceHistoryQuery query) {
        Employee employee = resolveStaffEmployee(user);
        AttendanceHistoryQuery safeQuery = query == null ? AttendanceHistoryQuery.builder().build() : query;
        LocalDate[] range = resolveRange(safeQuery.getFromDate(), safeQuery.getToDate());
        List<Attendance> records = includeShift(safeQuery)
                ? applyFilters(attendanceRepository.findHistoryByEmployee(
                employee.getId(), range[0], range[1]), safeQuery)
                : List.of();
        Map<String, String> shifts = includeShift(safeQuery)
                ? shiftLabels(shiftAssignmentRepository.findWeeklyAssignmentsForEmployee(
                employee.getId(), range[0], range[1], AssignmentStatus.ASSIGNED))
                : Map.of();
        List<TrainingAttendance> training = includeTraining(safeQuery)
                ? applyTrainingFilters(trainingAttendanceRepository.findHistoryByEmployee(
                employee.getId(), range[0], range[1]), safeQuery)
                : List.of();

        return buildView(employee.getId(), employee.getFullName(), storeName(employee.getStore()),
                range[0], range[1], null, null, null, safeQuery,
                List.of(), List.of(), List.of(), records, training, shifts);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceHistoryView getManagerHistory(AuthenticatedUser user,
                                                   Integer employeeId,
                                                   LocalDate fromDate,
                                                   LocalDate toDate) {
        return getManagerHistory(user, AttendanceHistoryQuery.builder()
                .employeeId(employeeId)
                .fromDate(fromDate)
                .toDate(toDate)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceHistoryView getManagerHistory(AuthenticatedUser user, AttendanceHistoryQuery query) {
        Store store = resolveManagerStore(user);
        AttendanceHistoryQuery safeQuery = query == null ? AttendanceHistoryQuery.builder().build() : query;
        Integer employeeId = safeQuery.getEmployeeId();
        if (employeeId != null) {
            assertEmployeeVisibleToManager(employeeId, store, user);
        }
        if (safeQuery.getShiftId() != null && shiftRepository != null
                && shiftRepository.findByIdAndStore_Id(safeQuery.getShiftId(), store.getId()).isEmpty()) {
            throw new BusinessException("Ca làm việc không thuộc cửa hàng bạn quản lý.");
        }
        LocalDate[] range = resolveRange(safeQuery.getFromDate(), safeQuery.getToDate());
        List<AttendanceHistoryView.EmployeeOption> employees = loadStoreEmployees(store.getId());
        List<Attendance> records = List.of();
        if (includeShift(safeQuery)) {
            records = employeeId == null
                    ? attendanceRepository.findHistoryByStore(store.getId(), range[0], range[1])
                    : attendanceRepository.findHistoryByStoreAndEmployee(store.getId(), employeeId, range[0], range[1]);
            records = applyFilters(records, safeQuery);
        }
        Map<String, String> shifts = includeShift(safeQuery)
                ? shiftLabels(shiftAssignmentRepository.findWeeklyAssignmentsForStore(
                store.getId(), range[0], range[1], AssignmentStatus.ASSIGNED))
                : Map.of();
        List<TrainingAttendance> training = List.of();
        if (includeTraining(safeQuery)) {
            training = employeeId == null
                    ? trainingAttendanceRepository.findHistoryForManager(
                    store.getId(), user.getEmployeeId(), range[0], range[1])
                    : trainingAttendanceRepository.findHistoryByEmployee(employeeId, range[0], range[1]);
            training = applyTrainingFilters(training, safeQuery);
        }
        List<AttendanceHistoryView.ShiftOption> shiftOptions = shiftRepository == null
                ? List.of()
                : shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId()).stream()
                .map(this::toShiftOption)
                .toList();

        return buildView(null, user.getDisplayName(), store.getStoreName(),
                range[0], range[1], store.getId(), employeeId, null, safeQuery,
                employees, List.of(), shiftOptions, records, training, shifts);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceHistoryView getAdminHistory(AuthenticatedUser user, AttendanceHistoryQuery query) {
        if (user == null || user.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException("Bạn không có quyền xem lịch sử chấm công toàn hệ thống.");
        }
        AttendanceHistoryQuery safeQuery = query == null ? AttendanceHistoryQuery.builder().build() : query;
        LocalDate[] range = resolveRange(safeQuery.getFromDate(), safeQuery.getToDate());
        if (safeQuery.getStoreId() != null && safeQuery.getEmployeeId() != null) {
            assertEmployeeInStore(safeQuery.getEmployeeId(), safeQuery.getStoreId());
        }
        List<Attendance> records = List.of();
        if (includeShift(safeQuery)) {
            records = applyFilters(
                    attendanceRepository.findHistoryBetween(range[0], range[1]), safeQuery);
            if (safeQuery.getStoreId() != null) {
                records = records.stream()
                        .filter(attendance -> belongsToStore(attendance, safeQuery.getStoreId()))
                        .toList();
            }
            if (safeQuery.getEmployeeId() != null) {
                records = records.stream()
                        .filter(attendance -> attendance.getEmployee() != null
                                && Objects.equals(attendance.getEmployee().getId(), safeQuery.getEmployeeId()))
                        .toList();
            }
        }
        List<TrainingAttendance> training = List.of();
        if (includeTraining(safeQuery)) {
            training = applyTrainingFilters(
                    trainingAttendanceRepository.findHistoryBetween(range[0], range[1]), safeQuery);
            if (safeQuery.getStoreId() != null) {
                training = training.stream()
                        .filter(row -> trainingBelongsToStore(row, safeQuery.getStoreId()))
                        .toList();
            }
            if (safeQuery.getEmployeeId() != null) {
                training = training.stream()
                        .filter(row -> row.getEmployee() != null
                                && Objects.equals(row.getEmployee().getId(), safeQuery.getEmployeeId()))
                        .toList();
            }
        }
        List<AttendanceHistoryView.StoreOption> stores = storeRepository.findAllWithManager().stream()
                .map(store -> AttendanceHistoryView.StoreOption.builder()
                        .storeId(store.getId())
                        .storeName(store.getStoreName())
                        .build())
                .toList();
        List<AttendanceHistoryView.EmployeeOption> employees = safeQuery.getStoreId() == null
                ? allEmployeeOptions()
                : loadStoreEmployees(safeQuery.getStoreId());
        List<AttendanceHistoryView.ShiftOption> shiftOptions = List.of();
        if (safeQuery.getStoreId() != null && shiftRepository != null) {
            shiftOptions = shiftRepository.findByStore_IdOrderByStartTimeAsc(safeQuery.getStoreId()).stream()
                    .map(this::toShiftOption)
                    .toList();
        }
        return buildView(null, user.getDisplayName(), null,
                range[0], range[1], safeQuery.getStoreId(), safeQuery.getEmployeeId(),
                safeQuery.getStoreId(), safeQuery,
                employees, stores, shiftOptions, records, training, Map.of());
    }

    private AttendanceHistoryView buildView(Integer employeeId,
                                            String employeeName,
                                            String storeName,
                                            LocalDate fromDate,
                                            LocalDate toDate,
                                            Integer viewStoreId,
                                            Integer filterEmployeeId,
                                            Integer filterStoreId,
                                            AttendanceHistoryQuery query,
                                            List<AttendanceHistoryView.EmployeeOption> employees,
                                            List<AttendanceHistoryView.StoreOption> stores,
                                            List<AttendanceHistoryView.ShiftOption> shiftOptions,
                                            List<Attendance> records,
                                            List<TrainingAttendance> trainingRecords,
                                            Map<String, String> shifts) {
        List<AttendanceHistoryView.AttendanceRow> rows = new ArrayList<>();
        BigDecimal totalHours = BigDecimal.ZERO;
        int minuteTotal = 0;
        boolean hasMinuteTotal = false;
        for (Attendance attendance : records) {
            Employee employee = attendance.getEmployee();
            boolean completed = attendance.getCheckOutTime() != null;
            if (completed && attendance.getWorkingMinutes() != null) {
                minuteTotal += attendance.getWorkingMinutes();
                hasMinuteTotal = true;
            } else if (completed && attendance.getTotalHours() != null) {
                totalHours = totalHours.add(attendance.getTotalHours());
            }
            String shiftKey = employee.getId() + "|" + attendance.getWorkDate();
            String rowStore = attendance.getStore() != null
                    ? attendance.getStore().getStoreName()
                    : storeName(employee.getStore());
            rows.add(AttendanceHistoryView.AttendanceRow.builder()
                    .attendanceId(attendance.getId())
                    .employeeId(employee.getId())
                    .employeeName(employee.getFullName())
                    .workDate(attendance.getWorkDate())
                    .storeName(rowStore != null ? rowStore : "—")
                    .scheduledStartLabel(formatTime(attendance.getScheduledStartTime()))
                    .scheduledEndLabel(formatTime(attendance.getScheduledEndTime()))
                    .checkInLabel(formatClock(attendance.getWorkDate(), attendance.getCheckInTime()))
                    .checkOutLabel(formatClock(attendance.getWorkDate(), attendance.getCheckOutTime()))
                    .totalHoursLabel(rowHoursLabel(attendance))
                    .lateLabel(formatMinuteValue(attendance.getLateMinutes()))
                    .earlyLabel(completed ? formatMinuteValue(attendance.getEarlyLeaveMinutes()) : "—")
                    .missingLabel(missingLabel(attendance))
                    .checkInLocation(formatCoordinates(attendance.getCheckInLatitude(), attendance.getCheckInLongitude()))
                    .checkOutLocation(formatCoordinates(attendance.getCheckOutLatitude(), attendance.getCheckOutLongitude()))
                    .statusLabel(statusLabel(attendance.getStatus()))
                    .statusCss(statusCss(attendance.getStatus()))
                    .shiftLabel(snapshotShiftLabel(attendance, shifts.getOrDefault(shiftKey, "Chưa phân ca")))
                    .kindLabel("Chấm công ca làm")
                    .build());
        }
        boolean hasTraining = trainingRecords != null && !trainingRecords.isEmpty();
        if (hasTraining) {
            for (TrainingAttendance training : trainingRecords) {
                rows.add(trainingRow(training));
                if (training.getCheckOutTime() != null && training.getWorkingMinutes() != null) {
                    minuteTotal += training.getWorkingMinutes();
                    hasMinuteTotal = true;
                }
            }
            rows.sort(Comparator
                    .comparing(AttendanceHistoryView.AttendanceRow::getWorkDate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(AttendanceHistoryView.AttendanceRow::getEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase))
                    .thenComparing(AttendanceHistoryView.AttendanceRow::getAttendanceId, Comparator.nullsLast(Comparator.reverseOrder())));
        }

        String totalLabel = hasMinuteTotal
                ? formatMinutes(minuteTotal + hoursToMinutes(totalHours))
                : formatHours(totalHours);
        return AttendanceHistoryView.builder()
                .employeeId(employeeId)
                .employeeName(employeeName)
                .storeName(storeName)
                .fromDate(fromDate)
                .toDate(toDate)
                .filterEmployeeId(filterEmployeeId)
                .filterStoreId(filterStoreId != null ? filterStoreId : viewStoreId)
                .filterShiftId(query != null ? query.getShiftId() : null)
                .filterStatus(query != null && query.getStatus() != null ? query.getStatus().getDbValue() : null)
                .lateOnly(query != null && query.isLateOnly())
                .earlyOnly(query != null && query.isEarlyOnly())
                .filterKind(kindValue(query))
                .employees(employees)
                .stores(stores)
                .shifts(shiftOptions)
                .statuses(statusOptions())
                .rows(rows)
                .recordCount(rows.size())
                .totalHours(totalHours)
                .totalHoursLabel(totalLabel)
                .build();
    }

    private boolean includeShift(AttendanceHistoryQuery query) {
        return query == null || query.getKind() == null || query.getKind() != AttendanceHistoryKind.TRAINING;
    }

    private boolean includeTraining(AttendanceHistoryQuery query) {
        if (query != null && query.getShiftId() != null) {
            return false;
        }
        return query == null || query.getKind() == null || query.getKind() != AttendanceHistoryKind.SHIFT;
    }

    private String kindValue(AttendanceHistoryQuery query) {
        if (query == null || query.getKind() == null || query.getKind() == AttendanceHistoryKind.ALL) {
            return "all";
        }
        return query.getKind() == AttendanceHistoryKind.SHIFT ? "shift" : "training";
    }

    private AttendanceHistoryView.AttendanceRow trainingRow(TrainingAttendance training) {
        Employee employee = training.getEmployee();
        boolean completed = training.getCheckOutTime() != null;
        String rowStore = training.getStore() != null
                ? training.getStore().getStoreName()
                : storeName(employee.getStore());
        return AttendanceHistoryView.AttendanceRow.builder()
                .attendanceId(training.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getFullName())
                .workDate(training.getWorkDate())
                .storeName(rowStore != null ? rowStore : "—")
                .scheduledStartLabel(formatTime(training.getScheduledStartTime()))
                .scheduledEndLabel(formatTime(training.getScheduledEndTime()))
                .checkInLabel(formatClock(training.getWorkDate(), training.getCheckInTime()))
                .checkOutLabel(formatClock(training.getWorkDate(), training.getCheckOutTime()))
                .totalHoursLabel(completed && training.getWorkingMinutes() != null
                        ? formatMinutes(training.getWorkingMinutes())
                        : "—")
                .lateLabel(formatMinuteValue(training.getLateMinutes()))
                .earlyLabel(completed ? formatMinuteValue(training.getEarlyLeaveMinutes()) : "—")
                .missingLabel(completed ? formatMinuteValue(training.getEarlyLeaveMinutes()) : "—")
                .checkInLocation(formatCoordinates(training.getCheckInLatitude(), training.getCheckInLongitude()))
                .checkOutLocation(formatCoordinates(training.getCheckOutLatitude(), training.getCheckOutLongitude()))
                .statusLabel(statusLabel(training.getStatus()))
                .statusCss(statusCss(training.getStatus()))
                .shiftLabel(trainingShiftLabel(training))
                .kindLabel("Chấm công đào tạo")
                .build();
    }

    private String trainingShiftLabel(TrainingAttendance training) {
        String name = training.getClassName() != null && !training.getClassName().isBlank()
                ? training.getClassName()
                : "Đào tạo";
        if (training.getScheduledStartTime() == null || training.getScheduledEndTime() == null) {
            return name;
        }
        return name
                + " ("
                + training.getScheduledStartTime().format(TIME_FORMATTER)
                + "–"
                + training.getScheduledEndTime().format(TIME_FORMATTER)
                + ")";
    }

    private List<TrainingAttendance> applyTrainingFilters(List<TrainingAttendance> records, AttendanceHistoryQuery query) {
        if (query == null || (query.getStatus() == null && !query.isLateOnly() && !query.isEarlyOnly())) {
            return records;
        }
        return records.stream().filter(row -> trainingMatches(row, query)).toList();
    }

    private boolean trainingMatches(TrainingAttendance row, AttendanceHistoryQuery query) {
        if (query.getStatus() != null && row.getStatus() != query.getStatus()) {
            return false;
        }
        if (query.isLateOnly() && !isLateStatus(row.getStatus(), row.getLateMinutes())) {
            return false;
        }
        return !query.isEarlyOnly() || isEarlyStatus(row.getStatus(), row.getEarlyLeaveMinutes());
    }

    private boolean isLateStatus(AttendanceStatus status, Integer lateMinutes) {
        return status == AttendanceStatus.LATE
                || status == AttendanceStatus.LATE_AND_EARLY
                || (lateMinutes != null && lateMinutes > 0);
    }

    private boolean isEarlyStatus(AttendanceStatus status, Integer earlyMinutes) {
        return status == AttendanceStatus.EARLY_LEAVE
                || status == AttendanceStatus.LATE_AND_EARLY
                || (earlyMinutes != null && earlyMinutes > 0);
    }

    private boolean trainingBelongsToStore(TrainingAttendance row, Integer storeId) {
        if (row.getStore() != null && Objects.equals(row.getStore().getId(), storeId)) {
            return true;
        }
        return row.getEmployee() != null
                && row.getEmployee().getStore() != null
                && Objects.equals(row.getEmployee().getStore().getId(), storeId);
    }

    private List<Attendance> applyFilters(List<Attendance> records, AttendanceHistoryQuery query) {
        if (query == null || (query.getShiftId() == null && query.getStatus() == null
                && !query.isLateOnly() && !query.isEarlyOnly())) {
            return records;
        }
        return records.stream()
                .filter(attendance -> matches(attendance, query))
                .toList();
    }

    private boolean matches(Attendance attendance, AttendanceHistoryQuery query) {
        if (query.getShiftId() != null
                && (attendance.getShift() == null || !query.getShiftId().equals(attendance.getShift().getId()))) {
            return false;
        }
        if (query.getStatus() != null && attendance.getStatus() != query.getStatus()) {
            return false;
        }
        if (query.isLateOnly() && !isLate(attendance)) {
            return false;
        }
        return !query.isEarlyOnly() || isEarly(attendance);
    }

    private boolean isLate(Attendance attendance) {
        return attendance.getStatus() == AttendanceStatus.LATE
                || attendance.getStatus() == AttendanceStatus.LATE_AND_EARLY
                || (attendance.getLateMinutes() != null && attendance.getLateMinutes() > 0);
    }

    private boolean isEarly(Attendance attendance) {
        return attendance.getStatus() == AttendanceStatus.EARLY_LEAVE
                || attendance.getStatus() == AttendanceStatus.LATE_AND_EARLY
                || (attendance.getEarlyLeaveMinutes() != null && attendance.getEarlyLeaveMinutes() > 0);
    }

    private boolean belongsToStore(Attendance attendance, Integer storeId) {
        if (attendance.getStore() != null && Objects.equals(attendance.getStore().getId(), storeId)) {
            return true;
        }
        return attendance.getEmployee() != null
                && attendance.getEmployee().getStore() != null
                && Objects.equals(attendance.getEmployee().getStore().getId(), storeId);
    }

    private void assertEmployeeInStore(Integer employeeId, Integer storeId) {
        Employee employee = employeeRepository.findByIdWithStore(employeeId)
                .orElseThrow(() -> new BusinessException("Nhân viên không tồn tại."));
        if (employee.getStore() == null || !Objects.equals(employee.getStore().getId(), storeId)) {
            throw new BusinessException("Nhân viên không thuộc cửa hàng đã chọn.");
        }
    }

    private List<AttendanceHistoryView.EmployeeOption> allEmployeeOptions() {
        List<AttendanceHistoryView.EmployeeOption> employees = new ArrayList<>();
        employees.addAll(toEmployeeOptions(employeeRepository.findByStatusWithStore(EmployeeStatus.ACTIVE)));
        employees.addAll(toEmployeeOptions(employeeRepository.findByStatusWithStore(EmployeeStatus.ON_LEAVE)));
        employees.addAll(toEmployeeOptions(employeeRepository.findByStatusWithStore(EmployeeStatus.TERMINATED)));
        return employees;
    }

    private List<AttendanceHistoryView.EmployeeOption> toEmployeeOptions(List<Employee> employees) {
        return employees.stream()
                .map(employee -> AttendanceHistoryView.EmployeeOption.builder()
                        .employeeId(employee.getId())
                        .fullName(employee.getFullName()
                                + (employee.getStore() != null ? " — " + employee.getStore().getStoreName() : ""))
                        .build())
                .toList();
    }

    private AttendanceHistoryView.ShiftOption toShiftOption(Shift shift) {
        return AttendanceHistoryView.ShiftOption.builder()
                .shiftId(shift.getId())
                .label(shift.getShiftName()
                        + " ("
                        + shift.getStartTime().format(TIME_FORMATTER)
                        + "–"
                        + shift.getEndTime().format(TIME_FORMATTER)
                        + ")")
                .build();
    }

    private List<AttendanceHistoryView.StatusOption> statusOptions() {
        List<AttendanceHistoryView.StatusOption> options = new ArrayList<>();
        for (AttendanceStatus status : AttendanceStatus.values()) {
            options.add(AttendanceHistoryView.StatusOption.builder()
                    .value(status.getDbValue())
                    .label(statusLabel(status))
                    .build());
        }
        return options;
    }

    private List<AttendanceHistoryView.EmployeeOption> loadStoreEmployees(Integer storeId) {
        List<Employee> employees = new ArrayList<>();
        employees.addAll(employeeRepository.findByStore_IdAndStatus(storeId, EmployeeStatus.ACTIVE));
        employees.addAll(employeeRepository.findByStore_IdAndStatus(storeId, EmployeeStatus.ON_LEAVE));
        employees.addAll(employeeRepository.findByStore_IdAndStatus(storeId, EmployeeStatus.TERMINATED));
        employees.sort(Comparator.comparing(Employee::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return employees.stream()
                .map(employee -> AttendanceHistoryView.EmployeeOption.builder()
                        .employeeId(employee.getId())
                        .fullName(employee.getFullName())
                        .build())
                .toList();
    }

    private Map<String, String> shiftLabels(List<ShiftAssignment> assignments) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignments) {
            if (assignment.getEmployee() == null || assignment.getShift() == null || assignment.getWorkDate() == null) {
                continue;
            }
            String key = assignment.getEmployee().getId() + "|" + assignment.getWorkDate();
            String label = assignment.getShift().getShiftName()
                    + " ("
                    + assignment.getShift().getStartTime().format(TIME_FORMATTER)
                    + "–"
                    + assignment.getShift().getEndTime().format(TIME_FORMATTER)
                    + ")";
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(label);
        }
        Map<String, String> labels = new LinkedHashMap<>();
        grouped.forEach((key, values) -> labels.put(key, String.join(", ", values)));
        return labels;
    }

    private String snapshotShiftLabel(Attendance attendance, String fallback) {
        if (attendance.getScheduledShiftName() != null
                && attendance.getScheduledStartTime() != null
                && attendance.getScheduledEndTime() != null) {
            return attendance.getScheduledShiftName()
                    + " ("
                    + attendance.getScheduledStartTime().format(TIME_FORMATTER)
                    + "–"
                    + attendance.getScheduledEndTime().format(TIME_FORMATTER)
                    + ")";
        }
        return fallback;
    }

    private void assertEmployeeVisibleToManager(Integer employeeId, Store store, AuthenticatedUser user) {
        if (Objects.equals(employeeId, user.getEmployeeId())) {
            return;
        }
        assertEmployeeInStore(employeeId, store);
    }

    private void assertEmployeeInStore(Integer employeeId, Store store) {
        Employee employee = employeeRepository.findByIdWithStore(employeeId)
                .orElseThrow(() -> new BusinessException("Nhân viên không tồn tại."));
        if (employee.getStore() == null || !Objects.equals(employee.getStore().getId(), store.getId())) {
            throw new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý.");
        }
    }

    private Employee resolveStaffEmployee(AuthenticatedUser user) {
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa liên kết với hồ sơ nhân viên.");
        }
        return employeeRepository.findByIdWithStore(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin hồ sơ nhân viên."));
    }

    private Store resolveManagerStore(AuthenticatedUser user) {
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
    }

    private LocalDate[] resolveRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = VietnamTime.today();
        LocalDate start = fromDate != null ? fromDate : today.withDayOfMonth(1);
        LocalDate end = toDate != null ? toDate : today;
        if (start.isAfter(today) || end.isAfter(today)) {
            throw new BusinessException("Không thể xem lịch sử chấm công sau ngày hiện tại theo giờ Việt Nam.");
        }
        if (end.isBefore(start)) {
            throw new BusinessException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessException("Khoảng thời gian xem lịch sử không được vượt quá 366 ngày.");
        }
        return new LocalDate[]{start, end};
    }

    private String formatClock(LocalDate workDate, LocalDateTime time) {
        if (time == null) {
            return "—";
        }
        if (workDate != null && !time.toLocalDate().equals(workDate)) {
            return time.format(DATE_TIME_FORMATTER);
        }
        return time.format(TIME_FORMATTER);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "—" : time.format(TIME_FORMATTER);
    }

    private String rowHoursLabel(Attendance attendance) {
        if (attendance.getCheckOutTime() == null) {
            return "—";
        }
        if (attendance.getWorkingMinutes() != null) {
            return formatMinutes(attendance.getWorkingMinutes());
        }
        return formatHours(attendance.getTotalHours());
    }

    private String missingLabel(Attendance attendance) {
        if (attendance.getStatus() == AttendanceStatus.ABSENT) {
            return formatMinutes(scheduledMinutes(attendance));
        }
        if (attendance.getCheckOutTime() == null) {
            return "—";
        }
        return formatMinuteValue(attendance.getEarlyLeaveMinutes());
    }

    private int scheduledMinutes(Attendance attendance) {
        if (attendance.getWorkDate() == null
                || attendance.getScheduledStartTime() == null
                || attendance.getScheduledEndTime() == null) {
            return 0;
        }
        LocalDateTime start = LocalDateTime.of(attendance.getWorkDate(), attendance.getScheduledStartTime());
        LocalDateTime end = LocalDateTime.of(attendance.getWorkDate(), attendance.getScheduledEndTime());
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        return (int) ChronoUnit.MINUTES.between(start, end);
    }

    private String formatMinuteValue(Integer minutes) {
        if (minutes == null) {
            return "—";
        }
        return formatMinutes(minutes);
    }

    private String formatMinutes(int minutes) {
        if (minutes <= 0) {
            return "0 phút";
        }
        int hours = minutes / 60;
        int remain = minutes % 60;
        if (hours == 0) {
            return remain + " phút";
        }
        if (remain == 0) {
            return hours + " giờ";
        }
        return hours + " giờ " + remain + " phút";
    }

    private int hoursToMinutes(BigDecimal hours) {
        if (hours == null) {
            return 0;
        }
        return hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String formatHours(BigDecimal hours) {
        if (hours == null) {
            return "—";
        }
        return hours.stripTrailingZeros().toPlainString() + " giờ";
    }

    private String formatCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return "—";
        }
        return latitude.stripTrailingZeros().toPlainString()
                + ", "
                + longitude.stripTrailingZeros().toPlainString();
    }

    private String statusLabel(AttendanceStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case PRESENT -> "Có mặt";
            case ON_TIME -> "Đúng giờ";
            case LATE -> "Đi muộn";
            case EARLY_LEAVE -> "Về sớm";
            case LATE_AND_EARLY -> "Đi muộn và về sớm";
            case ABSENT -> "Vắng";
        };
    }

    private String statusCss(AttendanceStatus status) {
        if (status == null) {
            return "status-unknown";
        }
        return switch (status) {
            case PRESENT, ON_TIME -> "status-on-time";
            case LATE -> "status-late";
            case EARLY_LEAVE -> "status-early";
            case LATE_AND_EARLY -> "status-late-early";
            case ABSENT -> "status-absent";
        };
    }

    private String storeName(Store store) {
        return store != null ? store.getStoreName() : null;
    }
}
