package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AvailabilityDuration;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
import com.example.coffee_hrm.dto.request.UpdateSlotStaffingForm;
import com.example.coffee_hrm.dto.response.AvailabilityPreview;
import com.example.coffee_hrm.dto.response.SlotLimitUpdateResult;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.dto.response.WorkAvailabilityResponse;
import com.example.coffee_hrm.dto.response.WorkPositionOption;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.ShiftSlotPositionNeed;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingSkill;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.repository.EmployeePassedSkill;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.ShiftSlotPositionNeedRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.repository.TrainingSkillRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import com.example.coffee_hrm.service.support.AvailabilityCoverage;
import com.example.coffee_hrm.service.support.ContractShiftQuota;
import com.example.coffee_hrm.service.support.PositionStaffing;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAvailabilityServiceImpl implements StaffAvailabilityService {

    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] VI_DAYS = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
    private static final int MAX_SHIFTS_PER_DAY = 2;
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
    static final String POSITION_COUNT_MESSAGE = "Số lượng mỗi vị trí phải là số nguyên không âm.";
    static final String POSITION_REQUIRED_MESSAGE =
            "Vui lòng chọn vị trí làm việc phù hợp với chứng chỉ của bạn.";
    static final String NO_ACTIVE_POSITION_MESSAGE =
            "Chưa có vị trí công việc. Vị trí lấy từ danh mục kỹ năng đào tạo đang hoạt động.";

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
    private final ShiftSlotPositionNeedRepository shiftSlotPositionNeedRepository;
    private final TrainingSkillRepository trainingSkillRepository;
    private final TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    private final EmploymentContractRepository employmentContractRepository;

    @Override
    public WeeklyAvailabilityView getNextWeekAvailabilityGrid(AuthenticatedUser staff) {
        Employee employee = requireStaffEmployee(staff);
        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(employee.getStore().getId());
        List<WorkAvailability> visible = workAvailabilityRepository.findVisibleByEmployee(
                employee.getId(), VietnamTime.today(), ApprovalStatus.PENDING);
        LocalDate weekStart = nextWeekMonday();
        Map<String, SlotUsage> usage = loadSlotUsage(employee.getStore().getId(), employee.getId(), weekStart);
        WeeklyAvailabilityView grid = buildPatternGrid(
                employee.getStore(), shifts, Set.of(), Map.of(), usage,
                staffingContext(employee.getStore().getId(), usage), weekStart);
        grid.setRegistrations(toGroups(visible));
        grid.setSelectedCount(grid.getRegistrations().size());
        applyContractQuota(grid, employee.getId(), weekStart);
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
        if (!isCertified(employee)) {
            return NOT_CERTIFIED_MESSAGE;
        }
        return ContractShiftQuota.describe(contractsOf(employee.getId()), nextWeekMonday()).blockMessage();
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
        employeeRepository.lockById(employee.getId());
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
                    .skill(prepared.skill())
                    .status(ApprovalStatus.PENDING)
                    .build());
        }
        workAvailabilityRepository.saveAll(toSave);
        notifyStoreManager(employee, prepared, toSave.size());
        return toSave.size();
    }

    @Override
    public WeeklyAvailabilityView getStoreNextWeekAvailabilityGrid(AuthenticatedUser manager) {
        return storeGrid(manager, nextWeekMonday());
    }

    @Override
    public WeeklyAvailabilityView getStoreWeekStaffingGrid(AuthenticatedUser manager, LocalDate weekStart) {
        LocalDate monday = weekStart == null
                ? nextWeekMonday()
                : weekStart.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return storeGrid(manager, monday);
    }

    private WeeklyAvailabilityView storeGrid(AuthenticatedUser manager, LocalDate weekStart) {
        Store store = resolveManagerStore(manager);
        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId());
        List<WorkAvailability> visible = workAvailabilityRepository.findVisibleByStore(
                store.getId(), VietnamTime.today(), ApprovalStatus.PENDING);

        Map<String, List<WeeklyAvailabilityView.StaffProposal>> proposalsBySlot = new HashMap<>();
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
        Map<String, SlotUsage> usage = loadSlotUsage(store.getId(), null, weekStart);
        WeeklyAvailabilityView grid = buildPatternGrid(
                store, shifts, Set.of(), proposalsBySlot, usage, staffingContext(store.getId(), usage), weekStart);
        grid.setRegistrations(toGroups(visible, weekStart).stream()
                .filter(WeeklyAvailabilityView.RegistrationGroup::isActiveInWeek)
                .toList());
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
        employeeRepository.lockById(employee.getId());
        group = workAvailabilityRepository.findByRegistrationKey(registrationKey);
        if (group.isEmpty()) {
            throw new BusinessException("Không tìm thấy đăng ký lịch làm việc.");
        }
        employee = group.getFirst().getEmployee();

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
        if (registrationUsesPositions(group)) {
            assertConfirmedQuota(group, employee);
        }
        assertApprovedSetMatchesContract(employee, group);

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
        assertExistingPositionsMatch(shiftId, dayOfWeek, maxEmployees);
        return persistSlotLimit(shift, dayOfWeek, maxEmployees, null);
    }

    @Override
    @Transactional
    public SlotLimitUpdateResult updateSlotStaffing(Integer shiftId,
                                                     Integer dayOfWeek,
                                                     String maxEmployeesRaw,
                                                     Map<Integer, String> requiredCounts,
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
        List<ParsedPositionNeed> positions = parsePositionNeeds(requiredCounts, maxEmployees, store, manager);
        return persistSlotLimit(shift, dayOfWeek, maxEmployees, positions);
    }

    @Override
    @Transactional
    public SlotLimitUpdateResult updateSlotStaffingBatch(List<UpdateSlotStaffingForm> slots, AuthenticatedUser manager) {
        if (slots == null || slots.isEmpty()) {
            throw new BusinessException("Không có thay đổi cấu hình để lưu.");
        }
        List<UpdateSlotStaffingForm> ordered = slots.stream()
                .filter(slot -> slot != null && slot.getShiftId() != null && slot.getDayOfWeek() != null)
                .sorted(Comparator
                        .comparing(UpdateSlotStaffingForm::getShiftId)
                        .thenComparing(UpdateSlotStaffingForm::getDayOfWeek))
                .toList();
        if (ordered.isEmpty()) {
            throw new BusinessException("Không có thay đổi cấu hình để lưu.");
        }
        for (UpdateSlotStaffingForm slot : ordered) {
            updateSlotStaffing(slot.getShiftId(), slot.getDayOfWeek(), slot.getMaxEmployees(),
                    slot.getRequiredCounts(), manager);
        }
        return new SlotLimitUpdateResult("Đã lưu cấu hình cho " + ordered.size() + " ngày ca.", false);
    }

    @Override
    public Map<Integer, Integer> countEligibleStaffBySkill(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        List<Integer> staffIds = employeeRepository.findSchedulableStaffIds(
                store.getId(), EmployeeStatus.ACTIVE, RoleName.STAFF,
                CertificationStatus.CERTIFIED, manager.getEmployeeId());
        return holderCounts(staffIds);
    }

    @Override
    public int countEligibleStaff(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        List<TrainingSkill> active = trainingSkillRepository.findByStatusWithCreator(TrainingSkillStatus.ACTIVE);
        Set<Integer> activeIds = active.stream().map(TrainingSkill::getId).collect(Collectors.toSet());
        if (activeIds.isEmpty()) {
            return 0;
        }
        List<Integer> staffIds = employeeRepository.findSchedulableStaffIds(
                store.getId(), EmployeeStatus.ACTIVE, RoleName.STAFF,
                CertificationStatus.CERTIFIED, manager.getEmployeeId());
        if (staffIds.isEmpty()) {
            return 0;
        }
        return (int) trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                        staffIds, TrainingResult.PASS, TrainingClassStatus.APPROVED).stream()
                .filter(row -> row.getEmployeeId() != null && activeIds.contains(row.getSkillId()))
                .map(EmployeePassedSkill::getEmployeeId)
                .distinct()
                .count();
    }

    @Override
    public List<WorkPositionOption> listActiveWorkPositions() {
        return trainingSkillRepository.findByStatusWithCreator(TrainingSkillStatus.ACTIVE).stream()
                .map(skill -> new WorkPositionOption(skill.getId(), skill.getSkillName()))
                .toList();
    }

    @Override
    public List<WorkPositionOption> listMyWorkPositions(AuthenticatedUser staff) {
        Employee employee = requireStaffEmployee(staff);
        if (!isCertified(employee)) {
            return List.of();
        }
        Set<Integer> passed = passedSkillIds(employee.getId());
        if (passed.isEmpty()) {
            return List.of();
        }
        return trainingSkillRepository.findByStatusWithCreator(TrainingSkillStatus.ACTIVE).stream()
                .filter(skill -> passed.contains(skill.getId()))
                .map(skill -> new WorkPositionOption(skill.getId(), skill.getSkillName()))
                .toList();
    }

    private SlotLimitUpdateResult persistSlotLimit(Shift shift,
                                                    int dayOfWeek,
                                                    int maxEmployees,
                                                    List<ParsedPositionNeed> positions) {
        Integer shiftId = shift.getId();
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
        if (positions != null) {
            replacePositionNeeds(limit, positions);
            String detail = positions.stream()
                    .filter(item -> item.count() > 0)
                    .map(item -> item.skill().getSkillName() + " " + item.count())
                    .collect(Collectors.joining(", "));
            message += " Nhu cầu theo vị trí: " + detail + ".";
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
        return "Quản lý đã duyệt " + slotCount + " ca lịch làm việc ("
                + durationLabel(availability) + ", " + period
                + "). Các ca đã được xếp vào lịch làm việc tương lai.";
    }

    private String rejectionMessage(WorkAvailability availability, int slotCount) {
        String period = availability.getDayOfWeek() != null
                ? AvailabilityCoverage.formatPeriod(availability.getValidFrom(), availability.getValidTo())
                : dateLabel(availability.getWorkDate());
        return "Quản lý đã từ chối " + slotCount + " ca đăng ký lịch làm việc ("
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
        for (WorkAvailability current : existing) {
            if (resolveStatus(current.getStatus()) == ApprovalStatus.PENDING
                    && overlapsRegistration(current, validFrom, validTo)) {
                pendingToReplace.add(current);
            }
        }
        if (!approvedConflicts.isEmpty()) {
            throw new BusinessException(
                    "Đăng ký mới chồng lên lịch đã được duyệt: "
                            + String.join("; ", approvedConflicts.stream().distinct().toList())
                            + ". Hãy chọn thứ hoặc thời hạn không trùng. Lịch đã duyệt được giữ nguyên.");
        }
        List<WorkAvailability> pending = pendingToReplace.stream().distinct().toList();
        enforceContractShiftCount(employee, validFrom, validTo, slots, existing, pending);
        TrainingSkill skill = resolveRegistrationSkill(request, employee, slots);
        return new PreparedRegistration(duration, validFrom, validTo, slots, pending, skill);
    }

    private boolean overlapsRegistration(WorkAvailability existing, LocalDate validFrom, LocalDate validTo) {
        ApprovalStatus status = resolveStatus(existing.getStatus());
        if (status == ApprovalStatus.REJECTED || status == ApprovalStatus.CANCELLED) {
            return false;
        }
        if (existing.getDayOfWeek() != null) {
            if (existing.getValidTo() != null && existing.getValidTo().isBefore(VietnamTime.today())) {
                return false;
            }
            return AvailabilityCoverage.rangesOverlap(validFrom, validTo, existing.getValidFrom(), existing.getValidTo());
        }
        LocalDate workDate = existing.getWorkDate();
        return workDate != null
                && !workDate.isBefore(VietnamTime.today())
                && !workDate.isBefore(validFrom)
                && !workDate.isAfter(validTo);
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
                                                    Map<String, SlotUsage> usageBySlot,
                                                    StaffingContext staffing,
                                                    LocalDate weekStart) {
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
                PositionStaffing.Snapshot snapshot = PositionStaffing.assess(
                        usage.maxEmployees,
                        staffing.requirements().getOrDefault(key, List.of()),
                        toStaffingSeats(usage, staffing.qualifiedPairs()));
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
                        .registeredCount(snapshot.registeredCount())
                        .positionsFilled(snapshot.positionsFilled())
                        .positionsTracked(snapshot.positionsTracked())
                        .shortfall(snapshot.shortfall())
                        .positionsConfigured(snapshot.positionsConfigured())
                        .awaitingApproval(snapshot.awaitingApproval())
                        .staffingProgress(snapshot.staffingProgress())
                        .staffingStatusKey(snapshot.staffingStatusKey())
                        .staffingStatusLabel(snapshot.staffingStatusLabel())
                        .shortageSummary(snapshot.shortageSummary())
                        .positionLines(toPositionLines(snapshot))
                        .skillActivity(toSkillActivity(snapshot))
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
                .shortageAlerts(shortageAlerts(rows))
                .build();
    }

    private List<WeeklyAvailabilityView.RegistrationGroup> toGroups(List<WorkAvailability> rows) {
        return toGroups(rows, null);
    }

    private List<WeeklyAvailabilityView.RegistrationGroup> toGroups(List<WorkAvailability> rows, LocalDate weekStart) {
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
            String slotKeys = ";" + group.stream()
                    .map(wa -> {
                        Integer day = patternDay(wa);
                        String shiftName = wa.getShift() != null && wa.getShift().getShiftName() != null
                                ? wa.getShift().getShiftName() : "";
                        return (day == null ? "0" : day) + "|" + shiftName;
                    })
                    .distinct()
                    .reduce((left, right) -> left + ";" + right)
                    .orElse("") + ";";
            boolean activeInWeek = weekStart == null || group.stream().anyMatch(wa -> coversWeek(wa, weekStart));
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
                    .positionName(positionName(first))
                    .activeInWeek(activeInWeek)
                    .slotKeys(slotKeys)
                    .build());
        }
        return groups;
    }

    private boolean coversWeek(WorkAvailability availability, LocalDate weekStart) {
        Integer day = patternDay(availability);
        if (day == null) {
            LocalDate date = availability.getWorkDate();
            return date != null && !date.isBefore(weekStart) && !date.isAfter(weekStart.plusDays(6));
        }
        LocalDate occurrence = weekStart.plusDays(day - 1L);
        if (availability.getValidFrom() != null && occurrence.isBefore(availability.getValidFrom())) {
            return false;
        }
        return availability.getValidTo() == null || !occurrence.isAfter(availability.getValidTo());
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
                .positionName(positionName(availability))
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
                    "%s đã đăng ký %d ca lịch làm việc định kỳ (%s, %s) tại %s. Xem đề xuất tại Đăng kí lịch làm việc.",
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
                .positionName(positionName(availability))
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
        addSeat(seats, row, null);
    }

    private void addSeat(Map<String, ApprovalStatus> seats,
                         WorkAvailability row,
                         Map<String, SeatDraft> drafts) {
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
        ApprovalStatus next = status == ApprovalStatus.APPROVED ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING;
        seats.put(key, next);
        if (drafts != null) {
            Integer employeeId = row.getEmployee() != null ? row.getEmployee().getId() : null;
            Integer skillId = row.getSkill() != null ? row.getSkill().getId() : null;
            drafts.put(key, new SeatDraft(employeeId, next, skillId, isCertified(row.getEmployee())));
        }
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
                        + " chưa có số lượng nhân viên tối đa. Bạn chưa thể đăng ký ca này.");
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
                            + ". Không tạo thêm đăng ký trùng cho ca này.");
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
                assertPositionCapacity(prepared, slot, label, date, rows, replacedIds);
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

    private Map<String, SlotUsage> loadSlotUsage(Integer storeId, Integer viewerEmployeeId, LocalDate weekStart) {
        Map<String, SlotUsage> usage = new HashMap<>();
        for (ShiftSlotLimit limit : shiftSlotLimitRepository.findByStoreId(storeId)) {
            if (limit.getShift() == null || limit.getShift().getId() == null || limit.getDayOfWeek() == null) {
                continue;
            }
            usage.computeIfAbsent(slotKey(limit.getShift().getId(), limit.getDayOfWeek()), ignored -> new SlotUsage())
                    .maxEmployees = limit.getMaxEmployees();
        }
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
            addSeat(slot.seats, row, slot.drafts);
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
                    ? "Bạn đã đăng ký ca này. Ca đang vượt giới hạn nên tạm ngưng nhận thêm."
                    : "Đã vượt " + (occupied - usage.maxEmployees)
                    + " đăng ký so với giới hạn ngày " + dateText + ". Tạm ngưng nhận đăng ký mới.";
        } else if (remaining == 0) {
            key = "full";
            label = "Đã đủ người";
            hint = usage.viewerApproved
                    ? "Bạn đã đăng ký ca này."
                    : usage.viewerPending
                    ? "Ca đã đủ người cho ngày " + dateText + ". Bạn có thể gửi lại đăng ký đang chờ của mình."
                    : "Đã đủ số lượng nhân viên cho ngày " + dateText + ".";
        } else if (usage.viewerApproved) {
            key = "open";
            label = "Còn chỗ";
            hint = "Bạn đã đăng ký ca này.";
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

    private String positionName(WorkAvailability availability) {
        return availability != null && availability.getSkill() != null ? availability.getSkill().getSkillName() : null;
    }

    private List<String> shortageAlerts(List<WeeklyAvailabilityView.ShiftRow> rows) {
        List<String> alerts = new ArrayList<>();
        for (WeeklyAvailabilityView.ShiftRow row : rows) {
            if (row.getDayCells() == null) {
                continue;
            }
            for (WeeklyAvailabilityView.DayCell cell : row.getDayCells()) {
                if (!PositionStaffing.SHORT.equals(cell.getStaffingStatusKey()) || !cell.isPositionsConfigured()) {
                    continue;
                }
                String text = row.getShiftName() + " " + cell.getDayOfWeekName()
                        + " (" + row.getFormattedTime() + "): Thiếu nhân sự.";
                if (cell.getShortfall() > 0) {
                    text += " Còn thiếu " + cell.getShortfall() + " người theo số đã duyệt.";
                }
                if (cell.getShortageSummary() != null && !cell.getShortageSummary().isBlank()) {
                    text += " " + cell.getShortageSummary() + ".";
                }
                alerts.add(text);
            }
        }
        return alerts;
    }

    private List<WeeklyAvailabilityView.PositionLine> toPositionLines(PositionStaffing.Snapshot snapshot) {
        return snapshot.positions().stream()
                .map(line -> WeeklyAvailabilityView.PositionLine.builder()
                        .skillId(line.skillId())
                        .skillName(line.skillName())
                        .required(line.required())
                        .registered(line.registered())
                        .pending(line.pending())
                        .approved(line.approved())
                        .shortfall(line.shortfall())
                        .statusKey(line.statusKey())
                        .statusLabel(line.statusLabel())
                        .build())
                .toList();
    }

    private Map<Integer, WeeklyAvailabilityView.SkillActivity> toSkillActivity(PositionStaffing.Snapshot snapshot) {
        Map<Integer, WeeklyAvailabilityView.SkillActivity> activity = new LinkedHashMap<>();
        snapshot.skillCounts().forEach((skillId, count) -> activity.put(
                skillId, new WeeklyAvailabilityView.SkillActivity(count.registered(), count.pending(), count.approved())));
        return activity;
    }

    private List<PositionStaffing.Seat> toStaffingSeats(SlotUsage usage, Set<String> qualified) {
        List<PositionStaffing.Seat> seats = new ArrayList<>();
        for (SeatDraft draft : usage.drafts.values()) {
            boolean qualifiedForSkill = draft.certified()
                    && draft.employeeId() != null
                    && draft.skillId() != null
                    && qualified.contains(draft.employeeId() + ":" + draft.skillId());
            seats.add(new PositionStaffing.Seat(
                    draft.employeeId(), draft.status(), draft.skillId(), qualifiedForSkill));
        }
        return seats;
    }

    private StaffingContext staffingContext(Integer storeId, Map<String, SlotUsage> usage) {
        Map<String, List<PositionStaffing.Requirement>> requirements = loadRequirements(storeId);
        Set<String> qualified = requirements.isEmpty() ? Set.of() : qualifiedPairs(usage);
        return new StaffingContext(requirements, qualified);
    }

    private Map<String, List<PositionStaffing.Requirement>> loadRequirements(Integer storeId) {
        Map<String, List<PositionStaffing.Requirement>> map = new HashMap<>();
        for (ShiftSlotPositionNeed need : shiftSlotPositionNeedRepository.findByStoreId(storeId)) {
            if (need.getSlotLimit() == null || need.getSlotLimit().getShift() == null
                    || need.getSlotLimit().getShift().getId() == null
                    || need.getSlotLimit().getDayOfWeek() == null
                    || need.getSkill() == null || need.getSkill().getId() == null
                    || need.getRequiredCount() == null) {
                continue;
            }
            String key = slotKey(need.getSlotLimit().getShift().getId(), need.getSlotLimit().getDayOfWeek());
            map.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new PositionStaffing.Requirement(
                            need.getSkill().getId(),
                            need.getSkill().getSkillName(),
                            need.getRequiredCount()));
        }
        return map;
    }

    private Set<String> qualifiedPairs(Map<String, SlotUsage> usage) {
        Set<Integer> employeeIds = new LinkedHashSet<>();
        for (SlotUsage slot : usage.values()) {
            for (SeatDraft draft : slot.drafts.values()) {
                if (draft.employeeId() != null && draft.certified()) {
                    employeeIds.add(draft.employeeId());
                }
            }
        }
        if (employeeIds.isEmpty()) {
            return Set.of();
        }
        return trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                        employeeIds, TrainingResult.PASS, TrainingClassStatus.APPROVED).stream()
                .map(row -> row.getEmployeeId() + ":" + row.getSkillId())
                .collect(Collectors.toSet());
    }

    private void assertExistingPositionsMatch(Integer shiftId, int dayOfWeek, int maxEmployees) {
        List<ShiftSlotPositionNeed> needs = shiftSlotPositionNeedRepository.findByShiftAndDay(shiftId, dayOfWeek);
        if (needs.isEmpty()) {
            return;
        }
        int sum = needs.stream()
                .map(ShiftSlotPositionNeed::getRequiredCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (sum != maxEmployees) {
            throw new BusinessException("Đã sửa tổng chỉ tiêu thành " + maxEmployees
                    + ". Hãy điều chỉnh số lượng từng vị trí để tổng nhu cầu bằng tổng chỉ tiêu. "
                    + "Cấu hình hiện tại đang là " + sum + ".");
        }
    }

    private List<ParsedPositionNeed> parsePositionNeeds(Map<Integer, String> requiredCounts,
                                                        int maxEmployees,
                                                        Store store,
                                                        AuthenticatedUser manager) {
        if (requiredCounts == null || requiredCounts.isEmpty()) {
            throw new BusinessException("Vui lòng nhập số lượng cho từng vị trí.");
        }
        List<TrainingSkill> active = trainingSkillRepository.findByStatusWithCreator(TrainingSkillStatus.ACTIVE);
        if (active.isEmpty()) {
            throw new BusinessException(NO_ACTIVE_POSITION_MESSAGE);
        }
        Map<Integer, TrainingSkill> byId = new LinkedHashMap<>();
        for (TrainingSkill skill : active) {
            byId.put(skill.getId(), skill);
        }
        if (!requiredCounts.keySet().containsAll(byId.keySet())) {
            throw new BusinessException("Vui lòng nhập số lượng cho từng vị trí đang hoạt động.");
        }
        List<ParsedPositionNeed> parsed = new ArrayList<>();
        int sum = 0;
        for (Map.Entry<Integer, String> entry : requiredCounts.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            TrainingSkill skill = byId.get(entry.getKey());
            if (skill == null) {
                throw new BusinessException("Vị trí không có trong danh mục kỹ năng đang hoạt động.");
            }
            int count = parseNonNegative(entry.getValue());
            sum += count;
            parsed.add(new ParsedPositionNeed(skill, count));
        }
        if (sum != maxEmployees) {
            throw new BusinessException(positionSumMessage(sum, maxEmployees));
        }
        List<Integer> staffIds = employeeRepository.findSchedulableStaffIds(
                store.getId(),
                EmployeeStatus.ACTIVE,
                RoleName.STAFF,
                CertificationStatus.CERTIFIED,
                manager.getEmployeeId());
        Map<Integer, Integer> holders = holderCounts(staffIds);
        for (ParsedPositionNeed item : parsed) {
            int available = holders.getOrDefault(item.skill().getId(), 0);
            if (item.count() > available) {
                throw new BusinessException("Vị trí " + item.skill().getSkillName() + " cần " + item.count()
                        + " người nhưng cửa hàng chỉ có " + available
                        + " nhân viên Staff có chứng chỉ phù hợp.");
            }
        }
        return parsed;
    }

    static String positionSumMessage(int sum, int total) {
        return "Tổng nhu cầu theo vị trí (" + sum + ") phải bằng tổng chỉ tiêu của ca (" + total
                + "). Hãy điều chỉnh số lượng từng vị trí.";
    }

    private int parseNonNegative(String raw) {
        if (raw == null || !raw.trim().matches("\\d+")) {
            throw new BusinessException(POSITION_COUNT_MESSAGE);
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(POSITION_COUNT_MESSAGE);
        }
    }

    private Map<Integer, Integer> holderCounts(List<Integer> staffIds) {
        Map<Integer, Set<Integer>> people = new HashMap<>();
        if (staffIds == null || staffIds.isEmpty()) {
            return Map.of();
        }
        for (EmployeePassedSkill row : trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                staffIds, TrainingResult.PASS, TrainingClassStatus.APPROVED)) {
            if (row.getSkillId() == null || row.getEmployeeId() == null) {
                continue;
            }
            people.computeIfAbsent(row.getSkillId(), ignored -> new HashSet<>()).add(row.getEmployeeId());
        }
        Map<Integer, Integer> counts = new HashMap<>();
        people.forEach((skillId, ids) -> counts.put(skillId, ids.size()));
        return counts;
    }

    private void replacePositionNeeds(ShiftSlotLimit limit, List<ParsedPositionNeed> positions) {
        if (limit.getId() == null) {
            throw new BusinessException("Chưa lưu được giới hạn ca. Hãy tải lại trang rồi thử lại.");
        }
        shiftSlotPositionNeedRepository.deleteByLimitId(limit.getId());
        ShiftSlotLimit managed = shiftSlotLimitRepository.findById(limit.getId()).orElse(limit);
        List<ShiftSlotPositionNeed> rows = new ArrayList<>();
        for (ParsedPositionNeed item : positions) {
            rows.add(ShiftSlotPositionNeed.builder()
                    .slotLimit(managed)
                    .skill(item.skill())
                    .requiredCount(item.count())
                    .build());
        }
        shiftSlotPositionNeedRepository.saveAll(rows);
    }

    private TrainingSkill resolveRegistrationSkill(SubmitWorkAvailabilityRequest request,
                                                   Employee employee,
                                                   List<ParsedPatternSlot> slots) {
        boolean needsPosition = false;
        for (ParsedPatternSlot slot : slots) {
            if (!shiftSlotPositionNeedRepository.findByShiftAndDay(slot.shift().getId(), slot.dayOfWeek()).isEmpty()) {
                needsPosition = true;
                break;
            }
        }
        Integer skillId = request != null ? request.getSkillId() : null;
        if (skillId == null) {
            if (needsPosition) {
                throw new BusinessException(POSITION_REQUIRED_MESSAGE);
            }
            return null;
        }
        TrainingSkill skill = trainingSkillRepository.findById(skillId)
                .filter(item -> item.getStatus() == TrainingSkillStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "Vị trí làm việc không có trong danh mục kỹ năng đang hoạt động."));
        if (!passedSkillIds(employee.getId()).contains(skill.getId())) {
            throw new BusinessException(missingCertificateMessage(skill.getSkillName(), true));
        }
        if (needsPosition) {
            for (ParsedPatternSlot slot : slots) {
                List<ShiftSlotPositionNeed> needs = shiftSlotPositionNeedRepository
                        .findByShiftAndDay(slot.shift().getId(), slot.dayOfWeek());
                if (needs.isEmpty()) {
                    continue;
                }
                boolean listed = needs.stream().anyMatch(need -> need.getSkill() != null
                        && skill.getId().equals(need.getSkill().getId())
                        && need.getRequiredCount() != null
                        && need.getRequiredCount() > 0);
                if (!listed) {
                    throw new BusinessException(slotLabel(slot.shift(), slot.dayOfWeek())
                            + " không bố trí vị trí " + skill.getSkillName() + ".");
                }
            }
        }
        return skill;
    }

    private void assertPositionCapacity(PreparedRegistration prepared,
                                        ParsedPatternSlot slot,
                                        String label,
                                        LocalDate date,
                                        List<WorkAvailability> rows,
                                        Set<Integer> replacedIds) {
        if (prepared.skill() == null) {
            return;
        }
        List<ShiftSlotPositionNeed> needs = shiftSlotPositionNeedRepository
                .findByShiftAndDay(slot.shift().getId(), slot.dayOfWeek());
        ShiftSlotPositionNeed need = needs.stream()
                .filter(item -> item.getSkill() != null && prepared.skill().getId().equals(item.getSkill().getId()))
                .findFirst()
                .orElse(null);
        if (need == null || need.getRequiredCount() == null) {
            return;
        }
        Set<Integer> holders = employeesHolding(employeeIds(rows), prepared.skill().getId());
        UsageCount usage = countQualifiedPosition(
                rows, slot.dayOfWeek(), date, replacedIds, prepared.skill().getId(), holders);
        if (usage.pending() + usage.approved() >= need.getRequiredCount()) {
            throw new BusinessException(label + " đã đủ người cho vị trí " + prepared.skill().getSkillName()
                    + " ngày " + date.format(FULL_DATE)
                    + ". Cần " + need.getRequiredCount()
                    + ", đang có " + usage.pending() + " chờ duyệt và "
                    + usage.approved() + " đã duyệt.");
        }
    }

    private boolean registrationUsesPositions(List<WorkAvailability> group) {
        for (WorkAvailability availability : group) {
            if (availability.getSkill() != null && availability.getSkill().getId() != null) {
                return true;
            }
            Integer day = patternDay(availability);
            if (availability.getShift() != null && availability.getShift().getId() != null && day != null
                    && !shiftSlotPositionNeedRepository.findByShiftAndDay(availability.getShift().getId(), day).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void assertConfirmedQuota(List<WorkAvailability> group, Employee employee) {
        TrainingSkill skill = group.stream()
                .map(WorkAvailability::getSkill)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (skill != null && !passedSkillIds(employee.getId()).contains(skill.getId())) {
            throw new BusinessException(missingCertificateMessage(skill.getSkillName(), false));
        }
        List<WorkAvailability> ordered = new ArrayList<>(group);
        ordered.sort(Comparator
                .comparing((WorkAvailability availability) -> availability.getShift() != null
                        ? availability.getShift().getId() : 0)
                .thenComparingInt(availability -> {
                    Integer day = patternDay(availability);
                    return day == null ? 8 : day;
                }));
        for (WorkAvailability availability : ordered) {
            if (resolveStatus(availability.getStatus()) == ApprovalStatus.APPROVED) {
                continue;
            }
            if (availability.getShift() == null || availability.getShift().getId() == null) {
                continue;
            }
            Integer day = patternDay(availability);
            if (day == null) {
                continue;
            }
            Integer shiftId = availability.getShift().getId();
            ShiftSlotLimit limit = shiftSlotLimitRepository.lockByShiftAndDay(shiftId, day).orElse(null);
            List<ShiftSlotPositionNeed> needs = shiftSlotPositionNeedRepository.findByShiftAndDay(shiftId, day);
            if (skill == null && needs.isEmpty()) {
                continue;
            }
            List<WorkAvailability> rows = capacityCandidates(shiftId);
            Set<Integer> holders = skill == null ? Set.of() : employeesHolding(employeeIds(rows), skill.getId());
            int required = -1;
            if (skill != null) {
                required = needs.stream()
                        .filter(need -> need.getSkill() != null && skill.getId().equals(need.getSkill().getId()))
                        .map(ShiftSlotPositionNeed::getRequiredCount)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(-1);
            }
            List<LocalDate> dates = availability.getDayOfWeek() != null
                    ? AvailabilityCoverage.occurrences(day, availability.getValidFrom(), availability.getValidTo())
                    : availability.getWorkDate() == null ? List.of() : List.of(availability.getWorkDate());
            for (LocalDate date : dates) {
                if (date.isBefore(VietnamTime.today())) {
                    continue;
                }
                UsageCount total = countOnDate(rows, day, date, Set.of(), null);
                if (limit != null && limit.getMaxEmployees() != null && total.approved() + 1 > limit.getMaxEmployees()) {
                    throw new BusinessException("Không duyệt được " + slotLabel(availability.getShift(), day)
                            + " ngày " + date.format(FULL_DATE)
                            + " vì sẽ vượt tổng chỉ tiêu " + limit.getMaxEmployees()
                            + ". Đã duyệt " + total.approved()
                            + ". Yêu cầu duyệt khác có thể vừa chiếm chỗ cuối.");
                }
                if (required >= 0) {
                    UsageCount position = countQualifiedPosition(rows, day, date, Set.of(), skill.getId(), holders);
                    if (position.approved() + 1 > required) {
                        throw new BusinessException("Không duyệt được vì vị trí " + skill.getSkillName()
                                + " của " + slotLabel(availability.getShift(), day)
                                + " ngày " + date.format(FULL_DATE)
                                + " sẽ vượt chỉ tiêu. Cần " + required
                                + ", đã duyệt " + position.approved()
                                + ". Yêu cầu duyệt khác có thể vừa chiếm chỗ cuối.");
                    }
                }
            }
        }
    }

    private UsageCount countQualifiedPosition(List<WorkAvailability> rows,
                                              int dayOfWeek,
                                              LocalDate date,
                                              Set<Integer> excludedIds,
                                              Integer skillId,
                                              Set<Integer> holders) {
        Map<String, ApprovalStatus> seats = new LinkedHashMap<>();
        for (WorkAvailability row : rows) {
            if (row.getId() != null && excludedIds.contains(row.getId())) {
                continue;
            }
            if (!occupiesSlot(row, dayOfWeek, date)) {
                continue;
            }
            if (row.getSkill() == null || !skillId.equals(row.getSkill().getId())) {
                continue;
            }
            Employee occupant = row.getEmployee();
            if (occupant == null || !isCertified(occupant) || !holders.contains(occupant.getId())) {
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
        return new UsageCount(pending, approved, false);
    }

    private Set<Integer> employeeIds(List<WorkAvailability> rows) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (WorkAvailability row : rows) {
            if (row.getEmployee() != null && row.getEmployee().getId() != null) {
                ids.add(row.getEmployee().getId());
            }
        }
        return ids;
    }

    private Set<Integer> employeesHolding(Collection<Integer> employeeIds, Integer skillId) {
        if (employeeIds == null || employeeIds.isEmpty() || skillId == null) {
            return Set.of();
        }
        return trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                        employeeIds, TrainingResult.PASS, TrainingClassStatus.APPROVED).stream()
                .filter(row -> skillId.equals(row.getSkillId()))
                .map(EmployeePassedSkill::getEmployeeId)
                .collect(Collectors.toSet());
    }

    private Set<Integer> passedSkillIds(Integer employeeId) {
        if (employeeId == null) {
            return Set.of();
        }
        return trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                        List.of(employeeId), TrainingResult.PASS, TrainingClassStatus.APPROVED).stream()
                .map(EmployeePassedSkill::getSkillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String missingCertificateMessage(String skillName, boolean staffFacing) {
        if (staffFacing) {
            return "Bạn chưa có chứng chỉ hợp lệ cho vị trí " + skillName + " nên không thể đăng ký ca này.";
        }
        return "Nhân viên chưa có chứng chỉ hợp lệ cho vị trí " + skillName + " nên không thể duyệt đăng ký này.";
    }

    private void applyContractQuota(WeeklyAvailabilityView grid, Integer employeeId, LocalDate weekStart) {
        ContractShiftQuota.WeekQuota quota = ContractShiftQuota.describe(contractsOf(employeeId), weekStart);
        grid.setContractWeeklyShifts(quota.required());
        grid.setContractShiftMessage(quota.blockMessage());
    }

    private List<EmploymentContract> contractsOf(Integer employeeId) {
        return employmentContractRepository.findByEmployeeId(employeeId);
    }

    private void enforceContractShiftCount(Employee employee,
                                           LocalDate validFrom,
                                           LocalDate validTo,
                                           List<ParsedPatternSlot> slots,
                                           List<WorkAvailability> existing,
                                           List<WorkAvailability> replaced) {
        java.util.Set<Integer> replacedIds = new java.util.HashSet<>();
        for (WorkAvailability row : replaced) {
            if (row.getId() != null) {
                replacedIds.add(row.getId());
            }
        }
        List<WorkAvailability> kept = new java.util.ArrayList<>();
        for (WorkAvailability row : existing) {
            ApprovalStatus status = resolveStatus(row.getStatus());
            if (status != ApprovalStatus.PENDING && status != ApprovalStatus.APPROVED) {
                continue;
            }
            if (row.getId() != null && replacedIds.contains(row.getId())) {
                continue;
            }
            kept.add(row);
        }
        List<String> keys = slotKeys(slots);
        enforceMaxShiftsPerDay(validFrom, validTo, keys, kept);
        ContractShiftQuota.requireExact(
                contractsOf(employee.getId()),
                validFrom,
                validTo,
                shiftCounts(validFrom, validTo, keys, kept));
    }

    private void assertApprovedSetMatchesContract(Employee employee, List<WorkAvailability> group) {
        LocalDate validFrom = group.getFirst().getValidFrom();
        LocalDate validTo = group.getFirst().getValidTo();
        if (validFrom == null || validTo == null || group.getFirst().getDayOfWeek() == null) {
            return;
        }
        java.util.Set<Integer> groupIds = new java.util.HashSet<>();
        List<String> keys = new java.util.ArrayList<>();
        for (WorkAvailability row : group) {
            if (row.getId() != null) {
                groupIds.add(row.getId());
            }
            if (row.getShift() != null && row.getShift().getId() != null && row.getDayOfWeek() != null) {
                keys.add(row.getShift().getId() + ":" + row.getDayOfWeek());
            }
        }
        List<WorkAvailability> kept = new java.util.ArrayList<>();
        for (WorkAvailability row : workAvailabilityRepository.findVisibleByEmployee(
                employee.getId(), VietnamTime.today(), ApprovalStatus.PENDING)) {
            if (row.getId() != null && groupIds.contains(row.getId())) {
                continue;
            }
            if (resolveStatus(row.getStatus()) == ApprovalStatus.APPROVED) {
                kept.add(row);
            }
        }
        enforceMaxShiftsPerDay(validFrom, validTo, keys, kept);
        ContractShiftQuota.requireExact(
                contractsOf(employee.getId()), validFrom, validTo, shiftCounts(validFrom, validTo, keys, kept));
    }

    private void enforceMaxShiftsPerDay(LocalDate validFrom,
                                        LocalDate validTo,
                                        List<String> newKeys,
                                        List<WorkAvailability> kept) {
        Map<Integer, Set<String>> byDay = new LinkedHashMap<>();
        for (String key : newKeys) {
            addShiftDay(byDay, key);
        }
        for (WorkAvailability row : kept) {
            if (!overlapsRegistration(row, validFrom, validTo)
                    || row.getShift() == null
                    || row.getShift().getId() == null) {
                continue;
            }
            Integer day = row.getDayOfWeek();
            if (day == null && row.getWorkDate() != null) {
                day = row.getWorkDate().getDayOfWeek().getValue();
            }
            if (day == null) {
                continue;
            }
            addShiftDay(byDay, row.getShift().getId() + ":" + day);
        }
        for (Map.Entry<Integer, Set<String>> entry : byDay.entrySet()) {
            if (entry.getValue().size() <= MAX_SHIFTS_PER_DAY) {
                continue;
            }
            int day = entry.getKey();
            String dayName = day >= 1 && day <= VI_DAYS.length ? VI_DAYS[day - 1] : "Ngày đã chọn";
            throw new BusinessException(dayName + " chỉ được đăng ký tối đa 2 ca/ngày.");
        }
    }

    private static void addShiftDay(Map<Integer, Set<String>> byDay, String key) {
        int separator = key.indexOf(':');
        if (separator < 0 || separator == key.length() - 1) {
            return;
        }
        int day = Integer.parseInt(key.substring(separator + 1));
        byDay.computeIfAbsent(day, ignored -> new LinkedHashSet<>()).add(key);
    }

    private static List<String> slotKeys(List<ParsedPatternSlot> slots) {
        List<String> keys = new java.util.ArrayList<>();
        for (ParsedPatternSlot slot : slots) {
            keys.add(slot.shift().getId() + ":" + slot.dayOfWeek());
        }
        return keys;
    }

    private Map<LocalDate, Integer> shiftCounts(LocalDate validFrom,
                                                LocalDate validTo,
                                                List<String> newKeys,
                                                List<WorkAvailability> kept) {
        Map<LocalDate, java.util.Set<String>> byWeek = new java.util.LinkedHashMap<>();
        LocalDate week = validFrom.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = validTo.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!week.isAfter(last)) {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            LocalDate weekEnd = week.plusDays(6);
            for (String key : newKeys) {
                int day = Integer.parseInt(key.substring(key.indexOf(':') + 1));
                LocalDate date = week.plusDays(day - 1L);
                if (!date.isBefore(validFrom) && !date.isAfter(validTo) && !date.isAfter(weekEnd)) {
                    keys.add(key);
                }
            }
            for (WorkAvailability row : kept) {
                if (row.getShift() == null || row.getShift().getId() == null) {
                    continue;
                }
                for (int day = 1; day <= 7; day++) {
                    LocalDate date = week.plusDays(day - 1L);
                    if (date.isBefore(validFrom) || date.isAfter(validTo)) {
                        continue;
                    }
                    if (AvailabilityCoverage.appliesOn(row, date)) {
                        keys.add(row.getShift().getId() + ":" + day);
                    }
                }
            }
            byWeek.put(week, keys);
            week = week.plusWeeks(1);
        }
        Map<LocalDate, Integer> counts = new java.util.LinkedHashMap<>();
        byWeek.forEach((start, keys) -> counts.put(start, keys.size()));
        return counts;
    }

    private record ParsedPatternSlot(Shift shift, int dayOfWeek) {
    }

    private record PreparedRegistration(AvailabilityDuration duration,
                                        LocalDate validFrom,
                                        LocalDate validTo,
                                        List<ParsedPatternSlot> slots,
                                        List<WorkAvailability> pendingToReplace,
                                        TrainingSkill skill) {
    }

    private record ParsedPositionNeed(TrainingSkill skill, int count) {
    }

    private record SeatDraft(Integer employeeId, ApprovalStatus status, Integer skillId, boolean certified) {
    }

    private record StaffingContext(Map<String, List<PositionStaffing.Requirement>> requirements,
                                   Set<String> qualifiedPairs) {
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
        private final Map<String, SeatDraft> drafts = new LinkedHashMap<>();

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
