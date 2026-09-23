package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.AttendanceHistoryView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Override
    @Transactional(readOnly = true)
    public AttendanceHistoryView getStaffHistory(AuthenticatedUser user, LocalDate fromDate, LocalDate toDate) {
        Employee employee = resolveStaffEmployee(user);
        LocalDate[] range = resolveRange(fromDate, toDate);
        List<Attendance> records = attendanceRepository.findHistoryByEmployee(
                employee.getId(), range[0], range[1]);
        Map<String, String> shifts = shiftLabels(shiftAssignmentRepository.findWeeklyAssignmentsForEmployee(
                employee.getId(), range[0], range[1], AssignmentStatus.ASSIGNED));

        return buildView(employee.getId(), employee.getFullName(), storeName(employee.getStore()),
                range[0], range[1], null, List.of(), records, shifts);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceHistoryView getManagerHistory(AuthenticatedUser user,
                                                   Integer employeeId,
                                                   LocalDate fromDate,
                                                   LocalDate toDate) {
        Store store = resolveManagerStore(user);
        LocalDate[] range = resolveRange(fromDate, toDate);
        if (employeeId != null) {
            assertEmployeeInStore(employeeId, store);
        }

        List<AttendanceHistoryView.EmployeeOption> employees = loadStoreEmployees(store.getId());
        List<Attendance> records = employeeId == null
                ? attendanceRepository.findHistoryByStore(store.getId(), range[0], range[1])
                : attendanceRepository.findHistoryByStoreAndEmployee(store.getId(), employeeId, range[0], range[1]);
        Map<String, String> shifts = shiftLabels(shiftAssignmentRepository.findWeeklyAssignmentsForStore(
                store.getId(), range[0], range[1], AssignmentStatus.ASSIGNED));

        return buildView(null, user.getDisplayName(), store.getStoreName(),
                range[0], range[1], employeeId, employees, records, shifts);
    }

    private AttendanceHistoryView buildView(Integer employeeId,
                                            String employeeName,
                                            String storeName,
                                            LocalDate fromDate,
                                            LocalDate toDate,
                                            Integer filterEmployeeId,
                                            List<AttendanceHistoryView.EmployeeOption> employees,
                                            List<Attendance> records,
                                            Map<String, String> shifts) {
        List<AttendanceHistoryView.AttendanceRow> rows = new ArrayList<>();
        BigDecimal totalHours = BigDecimal.ZERO;
        for (Attendance attendance : records) {
            Employee employee = attendance.getEmployee();
            if (attendance.getTotalHours() != null) {
                totalHours = totalHours.add(attendance.getTotalHours());
            }
            String shiftKey = employee.getId() + "|" + attendance.getWorkDate();
            rows.add(AttendanceHistoryView.AttendanceRow.builder()
                    .attendanceId(attendance.getId())
                    .employeeId(employee.getId())
                    .employeeName(employee.getFullName())
                    .workDate(attendance.getWorkDate())
                    .checkInLabel(formatClock(attendance.getWorkDate(), attendance.getCheckInTime()))
                    .checkOutLabel(formatClock(attendance.getWorkDate(), attendance.getCheckOutTime()))
                    .totalHoursLabel(formatHours(attendance.getTotalHours()))
                    .statusLabel(statusLabel(attendance.getStatus()))
                    .statusCss(statusCss(attendance.getStatus()))
                    .shiftLabel(shifts.getOrDefault(shiftKey, "Chưa phân ca"))
                    .build());
        }

        return AttendanceHistoryView.builder()
                .employeeId(employeeId)
                .employeeName(employeeName)
                .storeName(storeName)
                .fromDate(fromDate)
                .toDate(toDate)
                .filterEmployeeId(filterEmployeeId)
                .employees(employees)
                .rows(rows)
                .recordCount(rows.size())
                .totalHours(totalHours)
                .totalHoursLabel(formatHours(totalHours))
                .build();
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
        LocalDate today = LocalDate.now();
        LocalDate start = fromDate != null ? fromDate : today.withDayOfMonth(1);
        LocalDate end = toDate != null ? toDate : today;
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

    private String formatHours(BigDecimal hours) {
        if (hours == null) {
            return "—";
        }
        return hours.stripTrailingZeros().toPlainString() + " giờ";
    }

    private String statusLabel(AttendanceStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case ON_TIME -> "Đúng giờ";
            case LATE -> "Đi muộn";
            case EARLY_LEAVE -> "Về sớm";
            case ABSENT -> "Vắng";
        };
    }

    private String statusCss(AttendanceStatus status) {
        if (status == null) {
            return "status-unknown";
        }
        return switch (status) {
            case ON_TIME -> "status-on-time";
            case LATE -> "status-late";
            case EARLY_LEAVE -> "status-early";
            case ABSENT -> "status-absent";
        };
    }

    private String storeName(Store store) {
        return store != null ? store.getStoreName() : null;
    }
}
