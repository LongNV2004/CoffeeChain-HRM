package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.dto.response.WorkAvailabilityResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAvailabilityServiceImpl implements StaffAvailabilityService {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] VI_DAYS = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
    private static final String AVAILABILITY_REF = "WORK_AVAILABILITY";

    private final WorkAvailabilityRepository workAvailabilityRepository;
    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    public WeeklyAvailabilityView getNextWeekAvailabilityGrid(AuthenticatedUser staff) {
        Employee employee = requireStaffEmployee(staff);
        LocalDate weekStart = nextWeekMonday();
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(employee.getStore().getId());
        Set<String> selectedKeys = toSlotKeys(
                workAvailabilityRepository.findByEmployeeAndDateRange(employee.getId(), weekStart, weekEnd));
        return buildGrid(employee.getStore(), weekStart, weekEnd, shifts, selectedKeys, Map.of());
    }

    @Override
    public List<WorkAvailabilityResponse> listMyNextWeekAvailabilities(AuthenticatedUser staff) {
        Integer employeeId = requireStaffEmployeeId(staff);
        LocalDate weekStart = nextWeekMonday();
        return workAvailabilityRepository.findByEmployeeAndDateRange(employeeId, weekStart, weekStart.plusDays(6))
                .stream()
                .map(this::toAvailabilityResponse)
                .toList();
    }

    @Override
    @Transactional
    public int submitNextWeekAvailability(SubmitWorkAvailabilityRequest request, AuthenticatedUser staff) {
        Employee employee = requireStaffEmployeeWithManager(staff);
        LocalDate weekStart = nextWeekMonday();
        LocalDate weekEnd = weekStart.plusDays(6);

        List<ParsedSlot> slots = parseAndValidateSlots(request, employee.getStore().getId(), weekStart, weekEnd);
        if (slots.isEmpty()) {
            throw new BusinessException("Vui lòng tick ít nhất một ngày/ca trong tuần kế tiếp.");
        }

        workAvailabilityRepository.deleteByEmployeeAndDateRange(employee.getId(), weekStart, weekEnd);

        List<WorkAvailability> toSave = new ArrayList<>(slots.size());
        for (ParsedSlot slot : slots) {
            toSave.add(WorkAvailability.builder()
                    .employee(employee)
                    .shift(slot.shift())
                    .workDate(slot.workDate())
                    .build());
        }
        workAvailabilityRepository.saveAll(toSave);

        notifyStoreManager(employee, weekStart, weekEnd, toSave.size());
        return toSave.size();
    }

    @Override
    public WeeklyAvailabilityView getStoreNextWeekAvailabilityGrid(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        LocalDate weekStart = nextWeekMonday();
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId());
        List<WorkAvailability> availabilities =
                workAvailabilityRepository.findByStoreAndDateRange(store.getId(), weekStart, weekEnd);

        Map<String, List<WeeklyAvailabilityView.StaffProposal>> proposalsBySlot = new HashMap<>();
        for (WorkAvailability wa : availabilities) {
            String key = slotKey(wa.getShift().getId(), wa.getWorkDate());
            proposalsBySlot.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(WeeklyAvailabilityView.StaffProposal.builder()
                            .availabilityId(wa.getId())
                            .employeeId(wa.getEmployee().getId())
                            .employeeName(wa.getEmployee().getFullName())
                            .build());
        }
        return buildGrid(store, weekStart, weekEnd, shifts, Set.of(), proposalsBySlot);
    }

    @Override
    public List<WorkAvailabilityResponse> listStoreNextWeekAvailabilities(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        LocalDate weekStart = nextWeekMonday();
        return workAvailabilityRepository.findByStoreAndDateRange(store.getId(), weekStart, weekStart.plusDays(6))
                .stream()
                .map(this::toAvailabilityResponse)
                .toList();
    }

    private void notifyStoreManager(Employee staffEmployee, LocalDate weekStart, LocalDate weekEnd, int slotCount) {
        Employee managerEmployee = staffEmployee.getStore().getManager();
        if (managerEmployee == null || managerEmployee.getId() == null) {
            return;
        }
        userRepository.findByEmployee_Id(managerEmployee.getId()).ifPresent(managerUser -> {
            if (!Boolean.TRUE.equals(managerUser.getIsActive())) {
                return;
            }
            String message = String.format(
                    "%s đã đăng ký %d ngày/ca có thể làm trong tuần %s – %s tại %s. Xem đề xuất tại Lịch rảnh nhân viên.",
                    staffEmployee.getFullName(),
                    slotCount,
                    weekStart.format(DAY_MONTH),
                    weekEnd.format(DAY_MONTH),
                    staffEmployee.getStore().getStoreName());
            notificationService.notifyUsers(
                    List.of(managerUser),
                    "STAFF đăng ký lịch rảnh tuần kế tiếp",
                    message,
                    NotificationType.WORK_AVAILABILITY_SUBMITTED,
                    AVAILABILITY_REF,
                    staffEmployee.getId());
        });
    }

    private List<ParsedSlot> parseAndValidateSlots(SubmitWorkAvailabilityRequest request,
                                                   Integer storeId,
                                                   LocalDate weekStart,
                                                   LocalDate weekEnd) {
        List<String> rawSlots = request != null && request.getSelectedSlots() != null
                ? request.getSelectedSlots()
                : List.of();

        Map<Integer, Shift> shiftCache = new HashMap<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        List<ParsedSlot> parsed = new ArrayList<>();

        for (String raw : rawSlots) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.trim().split(":", 2);
            if (parts.length != 2) {
                throw new BusinessException("Dữ liệu ngày/ca không hợp lệ.");
            }
            Integer shiftId;
            LocalDate workDate;
            try {
                shiftId = Integer.valueOf(parts[0]);
                workDate = LocalDate.parse(parts[1], ISO_DATE);
            } catch (Exception ex) {
                throw new BusinessException("Dữ liệu ngày/ca không hợp lệ.");
            }

            if (workDate.isBefore(weekStart) || workDate.isAfter(weekEnd)) {
                throw new BusinessException("Chỉ được đăng ký lịch cho tuần làm việc kế tiếp.");
            }

            String key = slotKey(shiftId, workDate);
            if (!uniqueKeys.add(key)) {
                continue;
            }

            Shift shift = shiftCache.computeIfAbsent(shiftId, id ->
                    shiftRepository.findByIdAndStore_Id(id, storeId)
                            .orElseThrow(() -> new BusinessException(
                                    "Ca làm việc không tồn tại hoặc không thuộc cửa hàng hiện tại của bạn.")));
            parsed.add(new ParsedSlot(shift, workDate));
        }
        return parsed;
    }

    private WeeklyAvailabilityView buildGrid(Store store,
                                             LocalDate weekStart,
                                             LocalDate weekEnd,
                                             List<Shift> shifts,
                                             Set<String> selectedKeys,
                                             Map<String, List<WeeklyAvailabilityView.StaffProposal>> proposalsBySlot) {
        List<WeeklyAvailabilityView.DayHeader> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            days.add(WeeklyAvailabilityView.DayHeader.builder()
                    .date(date)
                    .dayOfWeekName(VI_DAYS[i])
                    .formattedDate(date.format(DAY_MONTH))
                    .build());
        }

        int selectedCount;
        if (!selectedKeys.isEmpty()) {
            selectedCount = selectedKeys.size();
        } else {
            selectedCount = proposalsBySlot.values().stream().mapToInt(List::size).sum();
        }

        List<WeeklyAvailabilityView.ShiftRow> rows = new ArrayList<>();
        for (Shift shift : shifts) {
            List<WeeklyAvailabilityView.DayCell> cells = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekStart.plusDays(i);
                String key = slotKey(shift.getId(), date);
                cells.add(WeeklyAvailabilityView.DayCell.builder()
                        .date(date)
                        .shiftId(shift.getId())
                        .slotKey(key)
                        .selected(selectedKeys.contains(key))
                        .proposals(proposalsBySlot.getOrDefault(key, List.of()))
                        .build());
            }
            rows.add(WeeklyAvailabilityView.ShiftRow.builder()
                    .shiftId(shift.getId())
                    .shiftName(shift.getShiftName())
                    .startTime(shift.getStartTime())
                    .endTime(shift.getEndTime())
                    .formattedTime(shift.getStartTime().format(TIME) + " – " + shift.getEndTime().format(TIME))
                    .dayCells(cells)
                    .build());
        }

        return WeeklyAvailabilityView.builder()
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .weekStartDate(weekStart)
                .weekEndDate(weekEnd)
                .selectedCount(selectedCount)
                .days(days)
                .shiftRows(rows)
                .build();
    }

    private Set<String> toSlotKeys(List<WorkAvailability> availabilities) {
        Set<String> keys = new HashSet<>();
        for (WorkAvailability wa : availabilities) {
            keys.add(slotKey(wa.getShift().getId(), wa.getWorkDate()));
        }
        return keys;
    }

    private String slotKey(Integer shiftId, LocalDate workDate) {
        return shiftId + ":" + workDate.format(ISO_DATE);
    }

    private LocalDate nextWeekMonday() {
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return thisMonday.plusWeeks(1);
    }

    private Store resolveManagerStore(AuthenticatedUser manager) {
        if (manager == null || manager.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Chỉ Manager được xem đề xuất lịch rảnh.");
        }
        if (manager.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản Manager chưa gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(manager.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Tài khoản Manager chưa được gán cửa hàng."));
    }

    private Employee requireStaffEmployee(AuthenticatedUser staff) {
        Integer employeeId = requireStaffEmployeeId(staff);
        Employee employee = employeeRepository.findByIdWithStore(employeeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên."));
        validateActiveStaffStore(employee);
        return employee;
    }

    private Employee requireStaffEmployeeWithManager(AuthenticatedUser staff) {
        Integer employeeId = requireStaffEmployeeId(staff);
        Employee employee = employeeRepository.findByIdWithStoreAndManager(employeeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên."));
        validateActiveStaffStore(employee);
        return employee;
    }

    private void validateActiveStaffStore(Employee employee) {
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException("Tài khoản nhân viên không còn hoạt động.");
        }
        Store store = employee.getStore();
        if (store == null || !Boolean.TRUE.equals(store.getIsActive())) {
            throw new BusinessException("Cửa hàng hiện tại không còn hoạt động.");
        }
    }

    private Integer requireStaffEmployeeId(AuthenticatedUser staff) {
        if (staff == null || staff.getRoleName() != RoleName.STAFF) {
            throw new BusinessException("Chỉ nhân viên mới được đăng ký lịch rảnh.");
        }
        if (staff.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa gắn với hồ sơ nhân viên.");
        }
        return staff.getEmployeeId();
    }

    private WorkAvailabilityResponse toAvailabilityResponse(WorkAvailability availability) {
        Shift shift = availability.getShift();
        Employee employee = availability.getEmployee();
        return WorkAvailabilityResponse.builder()
                .id(availability.getId())
                .employeeId(employee != null ? employee.getId() : null)
                .employeeName(employee != null ? employee.getFullName() : null)
                .shiftId(shift.getId())
                .shiftName(shift.getShiftName())
                .shiftStartTime(shift.getStartTime())
                .shiftEndTime(shift.getEndTime())
                .workDate(availability.getWorkDate())
                .note(availability.getNote())
                .createdAt(availability.getCreatedAt())
                .build();
    }

    private record ParsedSlot(Shift shift, LocalDate workDate) {
    }
}
