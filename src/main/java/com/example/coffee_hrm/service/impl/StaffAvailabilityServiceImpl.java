package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AvailabilityDuration;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
import com.example.coffee_hrm.dto.response.AvailabilityPreview;
import com.example.coffee_hrm.dto.response.SlotLimitUpdateResult;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.dto.response.WorkAvailabilityResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import com.example.coffee_hrm.service.support.AvailabilityCoverage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAvailabilityServiceImpl implements StaffAvailabilityService {

    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] VI_DAYS = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
    private static final String AVAILABILITY_REF = "WORK_AVAILABILITY";
    static final String NOT_CERTIFIED_MESSAGE =
            "Bạn chưa có chứng chỉ nên không thể đăng ký ca làm việc. "
                    + "Vui lòng hoàn thành khóa đào tạo và đạt yêu cầu để được cấp chứng chỉ.";
    static final String NOT_CERTIFIED_APPROVAL_MESSAGE =
            "Nhân viên chưa có chứng chỉ nên không thể đăng ký ca làm việc. "
                    + "Vui lòng hoàn thành khóa đào tạo và đạt yêu cầu để được cấp chứng chỉ.";
    static final String INVALID_LIMIT_MESSAGE = "Vui lòng nhập số lượng nhân viên lớn hơn 0.";
    static final String SLOT_LIMIT_HEADCOUNT_MESSAGE =
            "Số lượng không được vượt quá số nhân viên trong quán.";
    static final String SLOT_LIMIT_NO_STAFF_MESSAGE =
            "Cửa hàng chưa có nhân viên Staff. Hãy thêm nhân viên trước khi cấu hình sức chứa lớn hơn 0.";
    static final String OTHER_STORE_SHIFT_MESSAGE = "Ca làm việc không thuộc cửa hàng bạn quản lý.";

    private static final List<ApprovalStatus> OCCUPYING_STATUSES =
            List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);

    private final WorkAvailabilityRepository workAvailabilityRepository;
    private final ShiftSlotLimitRepository shiftSlotLimitRepository;
    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ScheduleService scheduleService;

    @Override
    public WeeklyAvailabilityView getNextWeekAvailabilityGrid(AuthenticatedUser staff) {
        Employee employee = requireStaffEmployee(staff);
        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(employee.getStore().getId());
        List<WorkAvailability> visible = workAvailabilityRepository.findVisibleByEmployee(
                employee.getId(), VietnamTime.today(), ApprovalStatus.PENDING);
        Map<String, SlotUsage> usage = loadSlotUsage(employee.getStore().getId(), employee.getId());
        WeeklyAvailabilityView grid = buildPatternGrid(employee.getStore(), shifts, Set.of(), Map.of(), usage);
        grid.setRegistrations(toGroups(visible));
        grid.setSelectedCount(grid.getRegistrations().size());
        return grid;
    }

    @Override
    public List<WorkAvailabilityResponse> listMyNextWeekAvailabilities(AuthenticatedUser staff) {
        Integer employeeId = requireStaffEmployeeId(staff);
        return workAvailabilityRepository.findVisibleByEmployee(employeeId, VietnamTime.today(), ApprovalStatus.PENDING)
                .stream()
                .map(this::toAvailabilityResponse)
                .toList();
    }

    @Override
    public String registrationBlockedMessage(AuthenticatedUser staff) {
        Employee employee = requireStaffEmployee(staff);
        return isCertified(employee) ? null : NOT_CERTIFIED_MESSAGE;
    }

    @Override
    public AvailabilityPreview previewRegistration(SubmitWorkAvailabilityRequest request, AuthenticatedUser staff) {
        Employee employee = requireStaffEmployee(staff);
        requireCertifiedToRegisterShift(employee);
        PreparedRegistration prepared = prepare(request, employee);
        enforceSlotCapacity(prepared, employee, false);
        return AvailabilityCoverage.buildPreview(
                prepared.duration().getLabel(),
                prepared.validFrom(),
                prepared.validTo(),
                prepared.slots().stream()
                        .map(slot -> new AvailabilityCoverage.SlotChoice(slot.dayOfWeek(), shiftLabel(slot.shift())))
                        .toList(),
                replacementNote(prepared.pendingToReplace()));
    }

    @Override
    @Transactional
    public int submitNextWeekAvailability(SubmitWorkAvailabilityRequest request, AuthenticatedUser staff) {
        Employee employee = requireStaffEmployeeWithManager(staff);
        requireCertifiedToRegisterShift(employee);
        PreparedRegistration prepared = prepare(request, employee);
        if (prepared.slots().isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một thứ và một ca.");
        }
        enforceSlotCapacity(prepared, employee, true);

        if (!prepared.pendingToReplace().isEmpty()) {
            List<Integer> ids = prepared.pendingToReplace().stream()
                    .map(WorkAvailability::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!ids.isEmpty()) {
                workAvailabilityRepository.deleteByIds(ids);
            }
        }

        String registrationKey = UUID.randomUUID().toString();
        List<WorkAvailability> toSave = new ArrayList<>(prepared.slots().size());
        for (ParsedPatternSlot slot : prepared.slots()) {
            toSave.add(WorkAvailability.builder()
                    .employee(employee)
                    .shift(slot.shift())
                    .dayOfWeek(slot.dayOfWeek())
                    .validFrom(prepared.validFrom())
                    .validTo(prepared.validTo())
                    .durationCode(prepared.duration().getCode())
                    .registrationKey(registrationKey)
                    .status(ApprovalStatus.PENDING)
                    .build());
        }
        workAvailabilityRepository.saveAll(toSave);
        notifyStoreManager(employee, prepared, toSave.size());
        return toSave.size();
    }

    @Override
    public WeeklyAvailabilityView getStoreNextWeekAvailabilityGrid(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId());
        List<WorkAvailability> visible = workAvailabilityRepository.findVisibleByStore(
                store.getId(), VietnamTime.today(), ApprovalStatus.PENDING);

        Map<String, List<WeeklyAvailabilityView.StaffProposal>> proposalsBySlot = new HashMap<>();
        LocalDate weekStart = nextWeekMonday();
        for (WorkAvailability wa : visible) {
            Integer day = patternDay(wa);
            if (day == null || wa.getShift() == null || wa.getShift().getId() == null) {
                continue;
            }
            LocalDate occurrence = weekStart.plusDays(day - 1);
            if (!occupiesSlot(wa, day, occurrence)) {
                continue;
            }
            String key = slotKey(wa.getShift().getId(), day);
            proposalsBySlot.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(toProposal(wa));
        }
        Map<String, SlotUsage> usage = loadSlotUsage(store.getId(), null);
        WeeklyAvailabilityView grid = buildPatternGrid(store, shifts, Set.of(), proposalsBySlot, usage);
        grid.setRegistrations(toGroups(visible));
        grid.setSelectedCount(grid.getRegistrations().size());
        return grid;
    }

    @Override
    public List<WorkAvailabilityResponse> listStoreNextWeekAvailabilities(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        return workAvailabilityRepository.findVisibleByStore(store.getId(), VietnamTime.today(), ApprovalStatus.PENDING)
                .stream()
                .map(this::toAvailabilityResponse)
                .toList();
    }

    @Override
    @Transactional
    public void reviewAvailability(Integer availabilityId, boolean approved, AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        WorkAvailability availability = workAvailabilityRepository.findByIdWithDetails(availabilityId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đăng ký lịch làm việc."));

        if (availability.getDayOfWeek() != null && availability.getRegistrationKey() != null) {
            reviewRegistration(availability.getRegistrationKey(), approved, manager);
            return;
        }

        Employee employee = availability.getEmployee();
        assertSameStore(employee, store);

        ApprovalStatus current = resolveStatus(availability.getStatus());
        if (!approved) {
            rejectSingle(availability, current);
            return;
        }

        if (current == ApprovalStatus.REJECTED) {
            throw new BusinessException(
                    "Đề xuất đã bị từ chối nên không tự xếp lịch. Dùng Phân ca trên Lịch làm việc nếu vẫn cần xếp nhân viên này.");
        }

        if (!isCertified(employee)) {
            throw new BusinessException(NOT_CERTIFIED_APPROVAL_MESSAGE);
        }
        scheduleService.assignShiftFromApprovedAvailability(
                AssignShiftRequest.builder()
                        .employeeId(employee.getId())
                        .shiftId(availability.getShift().getId())
                        .workDate(availability.getWorkDate())
                        .build(),
                manager);

        boolean newlyApproved = current != ApprovalStatus.APPROVED;
        if (newlyApproved) {
            availability.setStatus(ApprovalStatus.APPROVED);
            workAvailabilityRepository.save(availability);
        }
        if (newlyApproved) {
            notifyEmployee(employee, true, "Quản lý đã duyệt ca "
                    + availability.getShift().getShiftName()
                    + " ngày " + dateLabel(availability.getWorkDate())
                    + ". Ca này đã có trên lịch làm việc.");
        }
    }

    @Override
    @Transactional
    public void reviewRegistration(String registrationKey, boolean approved, AuthenticatedUser manager) {
        if (registrationKey == null || registrationKey.isBlank()) {
            throw new BusinessException("Không tìm thấy đăng ký lịch làm việc.");
        }
        Store store = resolveManagerStore(manager);
        List<WorkAvailability> group = workAvailabilityRepository.findByRegistrationKey(registrationKey);
        if (group.isEmpty()) {
            throw new BusinessException("Không tìm thấy đăng ký lịch làm việc.");
        }
        Employee employee = group.getFirst().getEmployee();
        assertSameStore(employee, store);

        boolean anyApproved = group.stream().anyMatch(wa -> resolveStatus(wa.getStatus()) == ApprovalStatus.APPROVED);
        boolean allRejected = group.stream().allMatch(wa -> resolveStatus(wa.getStatus()) == ApprovalStatus.REJECTED);
        if (!approved) {
            if (anyApproved) {
                throw new BusinessException(
                        "Đăng ký đã được duyệt nên không thể từ chối. Lịch đã duyệt được giữ nguyên đến hết thời hạn.");
            }
            if (allRejected) {
                return;
            }
            List<WorkAvailability> toSave = new ArrayList<>();
            for (WorkAvailability availability : group) {
                if (resolveStatus(availability.getStatus()) == ApprovalStatus.PENDING) {
                    availability.setStatus(ApprovalStatus.REJECTED);
                    toSave.add(availability);
                }
            }
            if (!toSave.isEmpty()) {
                workAvailabilityRepository.saveAll(toSave);
                notifyEmployee(employee, false, rejectionMessage(group.getFirst(), toSave.size()));
            }
            return;
        }

        if (group.stream().anyMatch(wa -> resolveStatus(wa.getStatus()) == ApprovalStatus.REJECTED)) {
            throw new BusinessException(
                    "Đăng ký đã bị từ chối nên không được dùng để phân ca. Nhân viên cần gửi đăng ký mới.");
        }
        if (!isCertified(employee)) {
            throw new BusinessException(NOT_CERTIFIED_APPROVAL_MESSAGE);
        }

        applyApprovedPattern(group, manager);
        List<WorkAvailability> toSave = new ArrayList<>();
        for (WorkAvailability availability : group) {
            if (resolveStatus(availability.getStatus()) != ApprovalStatus.APPROVED) {
                availability.setStatus(ApprovalStatus.APPROVED);
                toSave.add(availability);
            }
        }
        if (!toSave.isEmpty()) {
            workAvailabilityRepository.saveAll(toSave);
            notifyEmployee(employee, true, approvalMessage(group.getFirst(), group.size()));
        }
    }

    @Override
    @Transactional
    public int reviewAllPendingRegistrations(boolean approved, AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        List<WorkAvailability> visible = workAvailabilityRepository.findVisibleByStore(
                store.getId(), VietnamTime.today(), ApprovalStatus.PENDING);
        LinkedHashSet<String> registrationKeys = new LinkedHashSet<>();
        List<Integer> legacyIds = new ArrayList<>();
        for (WorkAvailability availability : visible) {
            if (resolveStatus(availability.getStatus()) != ApprovalStatus.PENDING) {
                continue;
            }
            if (availability.getRegistrationKey() != null && !availability.getRegistrationKey().isBlank()) {
                registrationKeys.add(availability.getRegistrationKey());
            } else if (availability.getId() != null) {
                legacyIds.add(availability.getId());
            }
        }
        if (registrationKeys.isEmpty() && legacyIds.isEmpty()) {
            throw new BusinessException("Không có đơn đang chờ duyệt.");
        }
        for (String registrationKey : registrationKeys) {
            reviewRegistration(registrationKey, approved, manager);
        }
        for (Integer availabilityId : legacyIds) {
            reviewAvailability(availabilityId, approved, manager);
        }
        return registrationKeys.size() + legacyIds.size();
    }

    @Override
    public int countStaffForSlotLimit(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        return countStaffExcludingManager(store, manager.getEmployeeId());
    }

    @Override
    @Transactional
    public SlotLimitUpdateResult updateSlotLimit(Integer shiftId,
                                                  Integer dayOfWeek,
                                                  String maxEmployeesRaw,
                                                  AuthenticatedUser manager) {
        int maxEmployees = parsePositiveLimit(maxEmployeesRaw);
        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw new BusinessException("Thứ trong tuần không hợp lệ.");
        }
        if (shiftId == null) {
            throw new BusinessException("Vui lòng chọn ca làm việc.");
        }
        Store store = resolveManagerStore(manager);
        Shift shift = shiftRepository.findByIdAndStore_Id(shiftId, store.getId())
                .orElseThrow(() -> new BusinessException(OTHER_STORE_SHIFT_MESSAGE));
        int staffCount = countStaffExcludingManager(store, manager.getEmployeeId());
        if (staffCount < 1) {
            throw new BusinessException(SLOT_LIMIT_NO_STAFF_MESSAGE);
        }
        if (maxEmployees > staffCount) {
            throw new BusinessException(SLOT_LIMIT_HEADCOUNT_MESSAGE);
        }

        ShiftSlotLimit limit = shiftSlotLimitRepository.lockByShiftAndDay(shiftId, dayOfWeek)
                .orElseGet(() -> ShiftSlotLimit.builder()
                        .shift(shift)
                        .dayOfWeek(dayOfWeek)
                        .build());
        String label = slotLabel(shift, dayOfWeek);
        List<WorkAvailability> rows = capacityCandidates(shiftId);
        LocalDate nextOccurrence = nextWeekMonday().plusDays(dayOfWeek - 1);
        UsageCount nextWeek = countOnDate(rows, dayOfWeek, nextOccurrence, Set.of(), null);
        PeakOccupancy peak = peakOccupancy(rows, dayOfWeek, VietnamTime.today());
        if (peak.occupied() > maxEmployees) {
            throw new BusinessException(
                    "Không lưu cấu hình. " + label + " ngày " + dateLabel(peak.date())
                            + " đã có " + peak.occupied()
                            + " đăng ký đang chiếm chỗ (đã duyệt " + peak.approved()
                            + ", chờ duyệt " + peak.pending()
                            + "), cao hơn mức " + maxEmployees
                            + ". Hãy chọn sức chứa không thấp hơn số đăng ký đang chiếm chỗ. "
                            + "Các đăng ký của nhân viên được giữ nguyên.");
        }

        limit.setShift(shift);
        limit.setDayOfWeek(dayOfWeek);
        limit.setMaxEmployees(maxEmployees);
        limit.setUpdatedAt(VietnamTime.now());
        try {
            shiftSlotLimitRepository.saveAndFlush(limit);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(
                    "Giới hạn cho ngày và ca này vừa được cập nhật. Hãy tải lại trang rồi thử lại.");
        }

        int remaining = maxEmployees - nextWeek.pending() - nextWeek.approved();
        String message = "Đã cập nhật " + label + ": tối đa " + maxEmployees
                + " nhân viên cho mỗi tuần. Tuần kế tiếp ngày " + dateLabel(nextOccurrence)
                + ": đã duyệt " + nextWeek.approved()
                + ", chờ duyệt " + nextWeek.pending()
                + ", còn " + remaining
                + " chỗ. Sức chứa áp dụng riêng cho từng tuần.";
        if (peak.date() != null && peak.occupied() > nextWeek.pending() + nextWeek.approved()) {
            message += " Tuần đông nhất sắp tới (" + dateLabel(peak.date())
                    + ") đang có " + peak.occupied() + " người.";
        }
        return new SlotLimitUpdateResult(message, false);
    }

    private void applyApprovedPattern(List<WorkAvailability> group, AuthenticatedUser manager) {
        for (WorkAvailability availability : group) {
            if (availability.getDayOfWeek() == null || availability.getShift() == null || availability.getEmployee() == null) {
                continue;
            }
            List<LocalDate> dates = AvailabilityCoverage.occurrences(
                    availability.getDayOfWeek(), availability.getValidFrom(), availability.getValidTo());
            for (LocalDate date : dates) {
                if (date.isBefore(VietnamTime.today())) {
                    continue;
                }
                try {
                    scheduleService.assignShiftFromApprovedAvailability(
                            AssignShiftRequest.builder()
                                    .employeeId(availability.getEmployee().getId())
                                    .shiftId(availability.getShift().getId())
                                    .workDate(date)
                                    .build(),
                            manager);
                } catch (BusinessException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("thời gian đã qua")) {
                        continue;
                    }
                    throw new BusinessException(
                            "Không xếp được " + describeConflict(availability)
                                    + " ngày " + date.format(FULL_DATE) + ": " + ex.getMessage());
                }
            }
        }
    }

    private String approvalMessage(WorkAvailability availability, int slotCount) {
        String period = availability.getDayOfWeek() != null
                ? AvailabilityCoverage.formatPeriod(availability.getValidFrom(), availability.getValidTo())
                : dateLabel(availability.getWorkDate());
        return "Quản lý đã duyệt " + slotCount + " slot lịch làm việc ("
                + durationLabel(availability) + ", " + period
                + "). Các ca đã được xếp vào lịch làm việc tương lai.";
    }

    private String rejectionMessage(WorkAvailability availability, int slotCount) {
        String period = availability.getDayOfWeek() != null
                ? AvailabilityCoverage.formatPeriod(availability.getValidFrom(), availability.getValidTo())
                : dateLabel(availability.getWorkDate());
        return "Quản lý đã từ chối " + slotCount + " slot đăng ký lịch làm việc ("
                + durationLabel(availability) + ", " + period
                + "). Bạn có thể gửi đăng ký mới.";
    }

    private void notifyEmployee(Employee employee, boolean approved, String message) {
        if (employee == null || employee.getId() == null) {
            return;
        }
        userRepository.findByEmployee_Id(employee.getId()).ifPresent(user -> {
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                return;
            }
            notificationService.notifyUsers(
                    List.of(user),
                    approved ? "Đăng ký ca làm đã được duyệt" : "Đăng ký ca làm bị từ chối",
                    message,
                    approved ? NotificationType.WORK_AVAILABILITY_APPROVED : NotificationType.WORK_AVAILABILITY_REJECTED,
                    AVAILABILITY_REF,
                    employee.getId());
        });
    }

    private void rejectSingle(WorkAvailability availability, ApprovalStatus current) {
        if (current == ApprovalStatus.APPROVED) {
            throw new BusinessException(
                    "Đề xuất đã được duyệt và đã có trên lịch làm việc. Hãy hủy ca tại Lịch làm việc nếu cần điều chỉnh.");
        }
        if (current == ApprovalStatus.REJECTED) {
            return;
        }
        availability.setStatus(ApprovalStatus.REJECTED);
        workAvailabilityRepository.save(availability);
        notifyEmployee(availability.getEmployee(), false, rejectionMessage(availability, 1));
    }

    private PreparedRegistration prepare(SubmitWorkAvailabilityRequest request, Employee employee) {
        if (request == null || request.getDurationCode() == null || request.getDurationCode().isBlank()) {
            throw new BusinessException("Vui lòng chọn thời hạn áp dụng.");
        }
        AvailabilityDuration duration = AvailabilityDuration.fromCode(request.getDurationCode())
                .orElseThrow(() -> new BusinessException(
                        "Thời hạn đăng ký không hợp lệ. Chọn 1 tuần, 1 tháng, 2 tháng, 6 tháng hoặc 1 năm."));
        LocalDate validFrom = nextWeekMonday();
        LocalDate validTo = duration.endInclusive(validFrom);
        if (validTo.isBefore(validFrom)) {
            throw new BusinessException("Thời hạn đăng ký không hợp lệ.");
        }

        List<ParsedPatternSlot> slots = parseSlots(request, employee.getStore().getId());
        if (slots.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một thứ và một ca.");
        }

        List<WorkAvailability> existing = workAvailabilityRepository.findVisibleByEmployee(
                employee.getId(), VietnamTime.today(), ApprovalStatus.PENDING);
        List<String> approvedConflicts = new ArrayList<>();
        List<WorkAvailability> pendingToReplace = new ArrayList<>();
        for (ParsedPatternSlot slot : slots) {
            for (WorkAvailability current : existing) {
                if (!conflictsWith(current, slot, validFrom, validTo)) {
                    continue;
                }
                if (resolveStatus(current.getStatus()) == ApprovalStatus.APPROVED) {
                    approvedConflicts.add(describeConflict(current));
                } else if (resolveStatus(current.getStatus()) == ApprovalStatus.PENDING) {
                    pendingToReplace.add(current);
                }
            }
        }
        if (!approvedConflicts.isEmpty()) {
            throw new BusinessException(
                    "Đăng ký mới chồng lên lịch đã được duyệt: "
                            + String.join("; ", approvedConflicts.stream().distinct().toList())
                            + ". Hãy chọn thứ hoặc thời hạn không trùng. Lịch đã duyệt được giữ nguyên.");
        }
        return new PreparedRegistration(duration, validFrom, validTo, slots, pendingToReplace.stream().distinct().toList());
    }

    private boolean conflictsWith(WorkAvailability existing,
                                  ParsedPatternSlot slot,
                                  LocalDate validFrom,
                                  LocalDate validTo) {
        if (existing.getShift() == null || !Objects.equals(existing.getShift().getId(), slot.shift().getId())) {
            return false;
        }
        ApprovalStatus status = resolveStatus(existing.getStatus());
        if (status == ApprovalStatus.REJECTED) {
            return false;
        }
        if (existing.getDayOfWeek() != null) {
            if (!Objects.equals(existing.getDayOfWeek(), slot.dayOfWeek())) {
                return false;
            }
            if (existing.getValidTo() != null && existing.getValidTo().isBefore(VietnamTime.today())) {
                return false;
            }
            return AvailabilityCoverage.rangesOverlap(validFrom, validTo, existing.getValidFrom(), existing.getValidTo());
        }
        LocalDate workDate = existing.getWorkDate();
        if (workDate == null || workDate.isBefore(VietnamTime.today())) {
            return false;
        }
        if (workDate.getDayOfWeek().getValue() != slot.dayOfWeek()) {
            return false;
        }
        return !workDate.isBefore(validFrom) && !workDate.isAfter(validTo);
    }

    private List<ParsedPatternSlot> parseSlots(SubmitWorkAvailabilityRequest request, Integer storeId) {
        List<String> rawSlots = request.getSelectedSlots() != null ? request.getSelectedSlots() : List.of();
        Map<Integer, Shift> shiftCache = new HashMap<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        List<ParsedPatternSlot> parsed = new ArrayList<>();

        for (String raw : rawSlots) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.trim().split(":", 2);
            if (parts.length != 2) {
                throw new BusinessException("Dữ liệu thứ/ca không hợp lệ.");
            }
            Integer shiftId;
            int dayOfWeek;
            try {
                shiftId = Integer.valueOf(parts[0]);
                dayOfWeek = Integer.parseInt(parts[1]);
            } catch (Exception ex) {
                throw new BusinessException("Dữ liệu thứ/ca không hợp lệ.");
            }
            if (dayOfWeek < 1 || dayOfWeek > 7) {
                throw new BusinessException("Thứ trong tuần không hợp lệ.");
            }
            if (!uniqueKeys.add(slotKey(shiftId, dayOfWeek))) {
                continue;
            }
            Shift shift = shiftCache.computeIfAbsent(shiftId, id ->
                    shiftRepository.findByIdAndStore_Id(id, storeId)
                            .orElseThrow(() -> new BusinessException(
                                    "Ca làm việc không tồn tại hoặc không thuộc cửa hàng hiện tại của bạn.")));
            if (shift.getStartTime() == null || shift.getEndTime() == null
                    || !shift.getStartTime().isBefore(shift.getEndTime())) {
                throw new BusinessException("Ca " + shift.getShiftName() + " có giờ bắt đầu hoặc kết thúc không hợp lệ.");
            }
            parsed.add(new ParsedPatternSlot(shift, dayOfWeek));
        }
        return parsed;
    }

    private WeeklyAvailabilityView buildPatternGrid(Store store,
                                                    List<Shift> shifts,
                                                    Set<String> selectedKeys,
                                                    Map<String, List<WeeklyAvailabilityView.StaffProposal>> proposalsBySlot,
                                                    Map<String, SlotUsage> usageBySlot) {
        LocalDate weekStart = nextWeekMonday();
        List<WeeklyAvailabilityView.DayHeader> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            days.add(WeeklyAvailabilityView.DayHeader.builder()
                    .date(date)
                    .dayOfWeekName(VI_DAYS[i])
                    .formattedDate(date.format(FULL_DATE))
                    .build());
        }

        List<WeeklyAvailabilityView.ShiftRow> rows = new ArrayList<>();
        for (Shift shift : shifts) {
            List<WeeklyAvailabilityView.DayCell> cells = new ArrayList<>(7);
            for (int day = 1; day <= 7; day++) {
                String key = slotKey(shift.getId(), day);
                LocalDate occurrence = weekStart.plusDays(day - 1);
                SlotUsage usage = usageBySlot.getOrDefault(key, new SlotUsage());
                CellView capacity = describeCell(usage, occurrence);
                cells.add(WeeklyAvailabilityView.DayCell.builder()
                        .date(occurrence)
                        .shiftId(shift.getId())
                        .slotKey(key)
                        .dayOfWeek(day)
                        .dayOfWeekName(VI_DAYS[day - 1])
                        .selected(selectedKeys.contains(key))
                        .proposals(proposalsBySlot.getOrDefault(key, List.of()))
                        .maxEmployees(capacity.maxEmployees())
                        .approvedCount(usage.approved())
                        .pendingCount(usage.pending())
                        .remaining(capacity.remaining())
                        .remainingText(capacity.remainingText())
                        .configured(capacity.configured())
                        .overCapacity(capacity.overCapacity())
                        .registrationClosed(capacity.registrationClosed())
                        .slotStatusKey(capacity.statusKey())
                        .slotStatusLabel(capacity.statusLabel())
                        .registrationHint(capacity.hint())
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

        int selectedCount = selectedKeys.isEmpty()
                ? proposalsBySlot.values().stream().mapToInt(List::size).sum()
                : selectedKeys.size();
        return WeeklyAvailabilityView.builder()
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .weekStartDate(weekStart)
                .weekEndDate(weekStart.plusDays(6))
                .selectedCount(selectedCount)
                .patternMode(false)
                .days(days)
                .shiftRows(rows)
                .registrations(List.of())
                .build();
    }

    private List<WeeklyAvailabilityView.RegistrationGroup> toGroups(List<WorkAvailability> rows) {
        Map<String, List<WorkAvailability>> grouped = new LinkedHashMap<>();
        for (WorkAvailability row : rows) {
            String key = row.getRegistrationKey() != null ? row.getRegistrationKey() : "legacy-" + row.getId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<WeeklyAvailabilityView.RegistrationGroup> groups = new ArrayList<>();
        for (List<WorkAvailability> group : grouped.values()) {
            group.sort(Comparator
                    .comparing((WorkAvailability wa) -> patternDay(wa) == null ? 8 : patternDay(wa))
                    .thenComparing(wa -> wa.getShift() != null && wa.getShift().getStartTime() != null
                            ? wa.getShift().getStartTime() : java.time.LocalTime.MIN));
            WorkAvailability first = group.getFirst();
            Employee employee = first.getEmployee();
            List<Integer> days = group.stream().map(this::patternDay).filter(Objects::nonNull).distinct().toList();
            String slots = group.stream()
                    .map(wa -> {
                        String day = patternDay(wa) == null ? dateLabel(wa.getWorkDate()) : AvailabilityCoverage.dayName(patternDay(wa));
                        String shiftName = wa.getShift() != null ? wa.getShift().getShiftName() : "Ca";
                        return day + ": " + shiftName;
                    })
                    .distinct()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            String projection = first.getDayOfWeek() != null
                    ? AvailabilityCoverage.projectionSummary(first.getValidFrom(), first.getValidTo(), days)
                    : dateLabel(first.getWorkDate());
            groups.add(WeeklyAvailabilityView.RegistrationGroup.builder()
                    .registrationKey(first.getRegistrationKey())
                    .availabilityId(first.getId())
                    .employeeId(employee != null ? employee.getId() : null)
                    .employeeName(employee != null ? employee.getFullName() : null)
                    .certificationLabel(certificationLabel(employee))
                    .durationLabel(durationLabel(first))
                    .formattedPeriod(first.getDayOfWeek() != null
                            ? AvailabilityCoverage.formatPeriod(first.getValidFrom(), first.getValidTo())
                            : dateLabel(first.getWorkDate()))
                    .slotsByDay(slots)
                    .projectionSummary(projection)
                    .statusKey(statusKey(first.getStatus()))
                    .statusLabel(statusLabel(first.getStatus()))
                    .pending(resolveStatus(first.getStatus()) == ApprovalStatus.PENDING)
                    .build());
        }
        return groups;
    }

    private WeeklyAvailabilityView.StaffProposal toProposal(WorkAvailability availability) {
        Employee employee = availability.getEmployee();
        return WeeklyAvailabilityView.StaffProposal.builder()
                .availabilityId(availability.getId())
                .employeeId(employee != null ? employee.getId() : null)
                .employeeName(employee != null ? employee.getFullName() : null)
                .statusKey(statusKey(availability.getStatus()))
                .statusLabel(statusLabel(availability.getStatus()))
                .pending(resolveStatus(availability.getStatus()) == ApprovalStatus.PENDING)
                .detail(availability.getDayOfWeek() != null
                        ? durationLabel(availability) + " · " + AvailabilityCoverage.formatPeriod(
                        availability.getValidFrom(), availability.getValidTo())
                        : dateLabel(availability.getWorkDate()))
                .certificationLabel(certificationLabel(employee))
                .build();
    }

    private String describeConflict(WorkAvailability availability) {
        String shiftName = availability.getShift() != null ? availability.getShift().getShiftName() : "Ca";
        if (availability.getDayOfWeek() != null) {
            return shiftName + " mỗi " + AvailabilityCoverage.dayName(availability.getDayOfWeek())
                    + " (" + AvailabilityCoverage.formatPeriod(availability.getValidFrom(), availability.getValidTo()) + ")";
        }
        return shiftName + " ngày " + dateLabel(availability.getWorkDate());
    }

    private void notifyStoreManager(Employee staffEmployee, PreparedRegistration prepared, int slotCount) {
        Employee managerEmployee = staffEmployee.getStore().getManager();
        if (managerEmployee == null || managerEmployee.getId() == null) {
            return;
        }
        userRepository.findByEmployee_Id(managerEmployee.getId()).ifPresent(managerUser -> {
            if (!Boolean.TRUE.equals(managerUser.getIsActive())) {
                return;
            }
            String message = String.format(
                    "%s đã đăng ký %d slot lịch làm việc định kỳ (%s, %s) tại %s. Xem đề xuất tại Đăng kí lịch làm việc.",
                    staffEmployee.getFullName(),
                    slotCount,
                    prepared.duration().getLabel(),
                    AvailabilityCoverage.formatPeriod(prepared.validFrom(), prepared.validTo()),
                    staffEmployee.getStore().getStoreName());
            notificationService.notifyUsers(
                    List.of(managerUser),
                    "Nhân viên đăng ký ca làm",
                    message,
                    NotificationType.WORK_AVAILABILITY_SUBMITTED,
                    AVAILABILITY_REF,
                    staffEmployee.getId());
        });
    }

    private String replacementNote(List<WorkAvailability> pendingToReplace) {
        if (pendingToReplace == null || pendingToReplace.isEmpty()) {
            return null;
        }
        return "Khi gửi, đăng ký đang chờ duyệt bị trùng sẽ được thay bằng đăng ký mới. Lịch đã duyệt không đổi.";
    }

    private String shiftLabel(Shift shift) {
        return shift.getShiftName() + " (" + shift.getStartTime().format(TIME) + " – " + shift.getEndTime().format(TIME) + ")";
    }

    private Integer patternDay(WorkAvailability availability) {
        if (availability.getDayOfWeek() != null) {
            return availability.getDayOfWeek();
        }
        if (availability.getWorkDate() != null) {
            return availability.getWorkDate().getDayOfWeek().getValue();
        }
        return null;
    }

    private String durationLabel(WorkAvailability availability) {
        return AvailabilityDuration.fromCode(availability.getDurationCode())
                .map(AvailabilityDuration::getLabel)
                .orElse(availability.getDayOfWeek() == null ? "Theo ngày" : "Định kỳ");
    }

    private String certificationLabel(Employee employee) {
        if (employee == null || employee.getCertificationStatus() == null) {
            return null;
        }
        return employee.getCertificationStatus().getLabel();
    }

    private String dateLabel(LocalDate date) {
        return date == null ? "" : date.format(FULL_DATE);
    }

    private String slotKey(Integer shiftId, int dayOfWeek) {
        return shiftId + ":" + dayOfWeek;
    }

    private LocalDate nextWeekMonday() {
        LocalDate thisMonday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return thisMonday.plusWeeks(1);
    }

    private void assertSameStore(Employee employee, Store store) {
        if (employee == null || employee.getStore() == null || !Objects.equals(employee.getStore().getId(), store.getId())) {
            throw new BusinessException("Đề xuất này không thuộc nhân viên của cửa hàng bạn quản lý.");
        }
    }

    private int countStaffExcludingManager(Store store, Integer managerEmployeeId) {
        long count = employeeRepository.countSchedulableStaff(
                store.getId(), EmployeeStatus.ACTIVE, RoleName.STAFF, managerEmployeeId);
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private Store resolveManagerStore(AuthenticatedUser manager) {
        if (manager == null || manager.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Chỉ Manager được xem đăng ký lịch làm việc.");
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

    private void requireCertifiedToRegisterShift(Employee employee) {
        if (!isCertified(employee)) {
            throw new BusinessException(NOT_CERTIFIED_MESSAGE);
        }
    }

    private boolean isCertified(Employee employee) {
        return employee.getCertificationStatus() == CertificationStatus.CERTIFIED;
    }

    private void validateActiveStaffStore(Employee employee) {
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException("Tài khoản nhân viên không còn hoạt động.");
        }
        Store store = employee.getStore();
        if (store == null || !Boolean.TRUE.equals(store.getIsActive())) {
            throw new BusinessException(com.example.coffee_hrm.common.store.StoreAccessMessages.INACTIVE);
        }
    }

    private Integer requireStaffEmployeeId(AuthenticatedUser staff) {
        if (staff == null || staff.getRoleName() != RoleName.STAFF) {
            throw new BusinessException("Chỉ nhân viên mới được đăng ký lịch làm việc.");
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
                .shiftId(shift != null ? shift.getId() : null)
                .shiftName(shift != null ? shift.getShiftName() : null)
                .shiftStartTime(shift != null ? shift.getStartTime() : null)
                .shiftEndTime(shift != null ? shift.getEndTime() : null)
                .workDate(availability.getWorkDate())
                .dayOfWeek(availability.getDayOfWeek())
                .dayOfWeekName(availability.getDayOfWeek() != null
                        ? AvailabilityCoverage.dayName(availability.getDayOfWeek()) : null)
                .validFrom(availability.getValidFrom())
                .validTo(availability.getValidTo())
                .durationLabel(durationLabel(availability))
                .formattedPeriod(availability.getDayOfWeek() != null
                        ? AvailabilityCoverage.formatPeriod(availability.getValidFrom(), availability.getValidTo())
                        : dateLabel(availability.getWorkDate()))
                .registrationKey(availability.getRegistrationKey())
                .note(availability.getNote())
                .createdAt(availability.getCreatedAt())
                .statusKey(statusKey(availability.getStatus()))
                .statusLabel(statusLabel(availability.getStatus()))
                .pending(resolveStatus(availability.getStatus()) == ApprovalStatus.PENDING)
                .build();
    }

    private ApprovalStatus resolveStatus(ApprovalStatus status) {
        return status != null ? status : ApprovalStatus.PENDING;
    }

    private String statusKey(ApprovalStatus status) {
        return switch (resolveStatus(status)) {
            case PENDING -> "pending";
            case APPROVED -> "approved";
            case REJECTED -> "rejected";
            case CANCELLED -> "cancelled";
        };
    }

    private String statusLabel(ApprovalStatus status) {
        return switch (resolveStatus(status)) {
            case PENDING -> "Chờ duyệt";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Từ chối";
            case CANCELLED -> "Đã hủy";
        };
    }

    /**
     * Số chỗ còn lại của một ngày = tối đa − chờ duyệt − đã duyệt của đúng ngày đó.
     * Giá trị âm nghĩa là ngày đó đang có nhiều đăng ký hơn sức chứa.
     */
    static int remainingSeats(int maxEmployees, int pendingCount, int approvedCount) {
        return maxEmployees - pendingCount - approvedCount;
    }

    static int parsePositiveLimit(String raw) {
        if (raw == null || !raw.trim().matches("\\d+")) {
            throw new BusinessException(INVALID_LIMIT_MESSAGE);
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw new BusinessException(INVALID_LIMIT_MESSAGE);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new BusinessException(INVALID_LIMIT_MESSAGE);
        }
    }

    /**
     * Một đăng ký chiếm chỗ khi trạng thái là chờ duyệt hoặc đã duyệt
     * và đúng ngày làm việc đó nằm trong thời hạn của đăng ký.
     * Đăng ký từ chối, đã hết hạn, hoặc thuộc tuần khác không chiếm chỗ.
     */
    static boolean occupiesSlot(WorkAvailability availability, int dayOfWeek, LocalDate occurrenceDate) {
        if (availability == null || occurrenceDate == null || dayOfWeek < 1 || dayOfWeek > 7) {
            return false;
        }
        if (occurrenceDate.getDayOfWeek().getValue() != dayOfWeek) {
            return false;
        }
        ApprovalStatus status = availability.getStatus() != null ? availability.getStatus() : ApprovalStatus.PENDING;
        if (status != ApprovalStatus.PENDING && status != ApprovalStatus.APPROVED) {
            return false;
        }
        if (availability.getDayOfWeek() != null) {
            if (!Objects.equals(availability.getDayOfWeek(), dayOfWeek)) {
                return false;
            }
            if (availability.getValidFrom() != null && occurrenceDate.isBefore(availability.getValidFrom())) {
                return false;
            }
            return availability.getValidTo() == null || !occurrenceDate.isAfter(availability.getValidTo());
        }
        return occurrenceDate.equals(availability.getWorkDate());
    }

    private void addSeat(Map<String, ApprovalStatus> seats, WorkAvailability row) {
        ApprovalStatus status = resolveStatus(row.getStatus());
        if (status != ApprovalStatus.PENDING && status != ApprovalStatus.APPROVED) {
            return;
        }
        String key = row.getEmployee() != null && row.getEmployee().getId() != null
                ? employeeSeatKey(row.getEmployee().getId())
                : "a:" + row.getId();
        ApprovalStatus current = seats.get(key);
        if (current == ApprovalStatus.APPROVED) {
            return;
        }
        seats.put(key, status == ApprovalStatus.APPROVED ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
    }

    private static String employeeSeatKey(Integer employeeId) {
        return "e:" + employeeId;
    }

    private void enforceSlotCapacity(PreparedRegistration prepared, Employee employee, boolean lockRows) {
        List<ParsedPatternSlot> ordered = new ArrayList<>(prepared.slots());
        ordered.sort(Comparator
                .comparing((ParsedPatternSlot slot) -> slot.shift().getId())
                .thenComparingInt(ParsedPatternSlot::dayOfWeek));
        Set<Integer> replacedIds = new LinkedHashSet<>();
        for (WorkAvailability pending : prepared.pendingToReplace()) {
            if (pending.getId() != null) {
                replacedIds.add(pending.getId());
            }
        }
        for (ParsedPatternSlot slot : ordered) {
            Integer shiftId = slot.shift().getId();
            ShiftSlotLimit limit = lockRows
                    ? shiftSlotLimitRepository.lockByShiftAndDay(shiftId, slot.dayOfWeek()).orElse(null)
                    : shiftSlotLimitRepository.findByShift_IdAndDayOfWeek(shiftId, slot.dayOfWeek()).orElse(null);
            String label = slotLabel(slot.shift(), slot.dayOfWeek());
            if (limit == null || limit.getMaxEmployees() == null || limit.getMaxEmployees() < 1) {
                throw new BusinessException(label
                        + " chưa có số lượng nhân viên tối đa. Bạn chưa thể đăng ký slot này.");
            }
            List<WorkAvailability> rows = capacityCandidates(shiftId);
            List<LocalDate> dates = AvailabilityCoverage.occurrences(
                    slot.dayOfWeek(), prepared.validFrom(), prepared.validTo());
            boolean checkedDate = false;
            for (LocalDate date : dates) {
                if (date.isBefore(VietnamTime.today())) {
                    continue;
                }
                checkedDate = true;
                UsageCount usage = countOnDate(rows, slot.dayOfWeek(), date, replacedIds, employee.getId());
                if (usage.duplicateEmployee()) {
                    throw new BusinessException("Bạn đã đăng ký " + label
                            + " ngày " + date.format(FULL_DATE)
                            + ". Không tạo thêm đăng ký trùng cho slot này.");
                }
                int occupied = usage.pending() + usage.approved();
                if (occupied >= limit.getMaxEmployees()) {
                    throw new BusinessException(label + " đã đủ số lượng nhân viên cho ngày "
                            + date.format(FULL_DATE)
                            + ". Tối đa " + limit.getMaxEmployees()
                            + ", đã có " + usage.pending() + " đăng ký chờ duyệt và "
                            + usage.approved() + " đăng ký đã duyệt. "
                            + "Nếu bạn vừa thấy còn chỗ, người khác đã đăng ký trước.");
                }
            }
            if (!checkedDate) {
                throw new BusinessException(label + " không có ngày nào trong thời hạn đã chọn.");
            }
        }
    }

    private List<WorkAvailability> capacityCandidates(Integer shiftId) {
        return workAvailabilityRepository.findCapacityCandidatesByShift(
                shiftId, VietnamTime.today(), ApprovalStatus.PENDING, OCCUPYING_STATUSES);
    }

    private UsageCount countOnDate(List<WorkAvailability> rows,
                                   int dayOfWeek,
                                   LocalDate date,
                                   Set<Integer> excludedIds,
                                   Integer employeeId) {
        Map<String, ApprovalStatus> seats = new LinkedHashMap<>();
        for (WorkAvailability row : rows) {
            if (row.getId() != null && excludedIds.contains(row.getId())) {
                continue;
            }
            if (!occupiesSlot(row, dayOfWeek, date)) {
                continue;
            }
            addSeat(seats, row);
        }
        int pending = 0;
        int approved = 0;
        for (ApprovalStatus status : seats.values()) {
            if (status == ApprovalStatus.APPROVED) {
                approved++;
            } else {
                pending++;
            }
        }
        boolean duplicateEmployee = employeeId != null && seats.containsKey(employeeSeatKey(employeeId));
        return new UsageCount(pending, approved, duplicateEmployee);
    }

    private PeakOccupancy peakOccupancy(List<WorkAvailability> rows, int dayOfWeek, LocalDate fromDate) {
        Map<LocalDate, Map<String, ApprovalStatus>> byDate = new HashMap<>();
        for (WorkAvailability row : rows) {
            for (LocalDate date : datesOccupied(row, dayOfWeek, fromDate)) {
                addSeat(byDate.computeIfAbsent(date, ignored -> new LinkedHashMap<>()), row);
            }
        }
        PeakOccupancy peak = new PeakOccupancy(null, 0, 0);
        for (Map.Entry<LocalDate, Map<String, ApprovalStatus>> entry : byDate.entrySet()) {
            int pending = 0;
            int approved = 0;
            for (ApprovalStatus status : entry.getValue().values()) {
                if (status == ApprovalStatus.APPROVED) {
                    approved++;
                } else {
                    pending++;
                }
            }
            int occupied = pending + approved;
            if (peak.date() == null || occupied > peak.occupied()
                    || (occupied == peak.occupied() && entry.getKey().isBefore(peak.date()))) {
                peak = new PeakOccupancy(entry.getKey(), pending, approved);
            }
        }
        return peak;
    }

    private List<LocalDate> datesOccupied(WorkAvailability availability, int dayOfWeek, LocalDate fromDate) {
        if (availability.getDayOfWeek() == null) {
            LocalDate workDate = availability.getWorkDate();
            if (workDate != null && !workDate.isBefore(fromDate) && occupiesSlot(availability, dayOfWeek, workDate)) {
                return List.of(workDate);
            }
            return List.of();
        }
        if (!Objects.equals(availability.getDayOfWeek(), dayOfWeek)) {
            return List.of();
        }
        LocalDate start = availability.getValidFrom();
        if (start == null || start.isBefore(fromDate)) {
            start = fromDate;
        }
        LocalDate end = availability.getValidTo() != null ? availability.getValidTo() : start.plusYears(2);
        if (end.isBefore(start)) {
            return List.of();
        }
        return AvailabilityCoverage.occurrences(dayOfWeek, start, end);
    }

    private Map<String, SlotUsage> loadSlotUsage(Integer storeId, Integer viewerEmployeeId) {
        Map<String, SlotUsage> usage = new HashMap<>();
        for (ShiftSlotLimit limit : shiftSlotLimitRepository.findByStoreId(storeId)) {
            if (limit.getShift() == null || limit.getShift().getId() == null || limit.getDayOfWeek() == null) {
                continue;
            }
            usage.computeIfAbsent(slotKey(limit.getShift().getId(), limit.getDayOfWeek()), ignored -> new SlotUsage())
                    .maxEmployees = limit.getMaxEmployees();
        }
        LocalDate weekStart = nextWeekMonday();
        List<WorkAvailability> rows = workAvailabilityRepository.findCapacityCandidatesByStore(
                storeId, VietnamTime.today(), ApprovalStatus.PENDING, OCCUPYING_STATUSES);
        for (WorkAvailability row : rows) {
            if (row.getShift() == null || row.getShift().getId() == null) {
                continue;
            }
            Integer day = patternDay(row);
            if (day == null) {
                continue;
            }
            LocalDate occurrence = weekStart.plusDays(day - 1);
            if (!occupiesSlot(row, day, occurrence)) {
                continue;
            }
            SlotUsage slot = usage.computeIfAbsent(slotKey(row.getShift().getId(), day), ignored -> new SlotUsage());
            addSeat(slot.seats, row);
            if (viewerEmployeeId != null && row.getEmployee() != null
                    && viewerEmployeeId.equals(row.getEmployee().getId())) {
                if (resolveStatus(row.getStatus()) == ApprovalStatus.PENDING) {
                    slot.viewerPending = true;
                } else {
                    slot.viewerApproved = true;
                }
            }
        }
        return usage;
    }

    private CellView describeCell(SlotUsage usage, LocalDate occurrence) {
        boolean configured = usage.maxEmployees != null && usage.maxEmployees > 0;
        int occupied = usage.pending() + usage.approved();
        int remaining = configured ? Math.max(0, remainingSeats(usage.maxEmployees, usage.pending(), usage.approved())) : 0;
        boolean over = configured && occupied > usage.maxEmployees;
        int occupiedAfterOwnReplacement = usage.viewerPending ? occupied - 1 : occupied;
        boolean fits = configured && occupiedAfterOwnReplacement < usage.maxEmployees;
        boolean closed = !configured || usage.viewerApproved || !fits;
        String dateText = dateLabel(occurrence);
        String key;
        String label;
        String hint;
        if (!configured) {
            key = "unconfigured";
            label = "Chưa cấu hình";
            hint = "Chưa có giới hạn. Chưa thể đăng ký.";
        } else if (over) {
            key = "over";
            label = "Vượt giới hạn";
            hint = usage.viewerApproved
                    ? "Bạn đã đăng ký slot này. Slot đang vượt giới hạn nên tạm ngưng nhận thêm."
                    : "Đã vượt " + (occupied - usage.maxEmployees)
                    + " đăng ký so với giới hạn ngày " + dateText + ". Tạm ngưng nhận đăng ký mới.";
        } else if (remaining == 0) {
            key = "full";
            label = "Đã đủ người";
            hint = usage.viewerApproved
                    ? "Bạn đã đăng ký slot này."
                    : usage.viewerPending
                    ? "Slot đã đủ người cho ngày " + dateText + ". Bạn có thể gửi lại đăng ký đang chờ của mình."
                    : "Đã đủ số lượng nhân viên cho ngày " + dateText + ".";
        } else if (usage.viewerApproved) {
            key = "open";
            label = "Còn chỗ";
            hint = "Bạn đã đăng ký slot này.";
        } else if (usage.viewerPending) {
            key = "open";
            label = "Còn chỗ";
            hint = "Đăng ký của bạn đang chờ duyệt. Còn " + remaining + " chỗ cho ngày " + dateText + ".";
        } else {
            key = "open";
            label = "Còn chỗ";
            hint = "Còn " + remaining + " chỗ cho ngày " + dateText + ".";
        }
        return new CellView(
                configured ? usage.maxEmployees : null,
                remaining,
                configured ? Integer.toString(remaining) : "—",
                configured,
                over,
                closed,
                key,
                label,
                hint);
    }

    private String slotLabel(Shift shift, int dayOfWeek) {
        String name = shift.getShiftName() != null ? shift.getShiftName() : "Ca";
        String time = "";
        if (shift.getStartTime() != null && shift.getEndTime() != null) {
            time = " (" + shift.getStartTime().format(TIME) + " – " + shift.getEndTime().format(TIME) + ")";
        }
        return AvailabilityCoverage.dayName(dayOfWeek) + " – " + name + time;
    }

    private record ParsedPatternSlot(Shift shift, int dayOfWeek) {
    }

    private record PreparedRegistration(AvailabilityDuration duration,
                                        LocalDate validFrom,
                                        LocalDate validTo,
                                        List<ParsedPatternSlot> slots,
                                        List<WorkAvailability> pendingToReplace) {
    }

    private record UsageCount(int pending, int approved, boolean duplicateEmployee) {
    }

    private record PeakOccupancy(LocalDate date, int pending, int approved) {
        private int occupied() {
            return pending + approved;
        }
    }

    private record CellView(Integer maxEmployees,
                            int remaining,
                            String remainingText,
                            boolean configured,
                            boolean overCapacity,
                            boolean registrationClosed,
                            String statusKey,
                            String statusLabel,
                            String hint) {
    }

    private static final class SlotUsage {
        private Integer maxEmployees;
        private final Map<String, ApprovalStatus> seats = new LinkedHashMap<>();
        private boolean viewerPending;
        private boolean viewerApproved;

        private int pending() {
            int count = 0;
            for (ApprovalStatus status : seats.values()) {
                if (status == ApprovalStatus.PENDING) {
                    count++;
                }
            }
            return count;
        }

        private int approved() {
            int count = 0;
            for (ApprovalStatus status : seats.values()) {
                if (status == ApprovalStatus.APPROVED) {
                    count++;
                }
            }
            return count;
        }
    }
}
