package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AvailabilityDuration;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.ContractTermType;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.response.StaffAllocationRoster;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.StaffAllocationService;
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
import com.example.coffee_hrm.service.support.AvailabilityCoverage;
import com.example.coffee_hrm.service.support.ContractShiftQuota;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Busy;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Candidate;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Group;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Pick;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Slot;
import lombok.RequiredArgsConstructor;
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
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAllocationServiceImpl implements StaffAllocationService {

    /** Ghi vào {@code WorkAvailabilities.Note} để phân biệt đơn do Manager phân bổ với đơn nhân viên tự gửi. */
    public static final String ALLOCATION_NOTE = "Quản lý phân bổ";

    static final String MANAGER_ONLY_MESSAGE = "Chỉ Manager được phân bổ nhân sự cho cửa hàng mình quản lý.";
    static final String NO_SHIFT_MESSAGE = "Cửa hàng chưa có ca làm việc nên chưa thể phân bổ nhân sự.";
    static final String NO_LIMIT_MESSAGE =
            "Chưa ca nào trong tuần được cấu hình số nhân viên tối đa. Hãy cấu hình nhu cầu ca trước khi phân bổ.";
    static final String NOTHING_SELECTED_MESSAGE = "Chưa chọn nhân viên nào để phân bổ.";
    static final String OTHER_STORE_EMPLOYEE_MESSAGE = "Nhân viên không thuộc cửa hàng bạn quản lý.";

    // Lý do không đủ điều kiện (mục 3.2 của yêu cầu nghiệp vụ).
    static final String REASON_NO_CONTRACT = "Chưa có hợp đồng lao động.";
    static final String REASON_NOT_STARTED = "Hợp đồng chưa có hiệu lực.";
    static final String REASON_EXPIRED = "Hợp đồng đã hết hạn.";
    static final String REASON_TERMINATED_CONTRACT = "Hợp đồng đã chấm dứt.";
    static final String REASON_NOT_RENEWED = "Hợp đồng không được gia hạn và đã hết hiệu lực.";
    static final String REASON_RESIGNED = "Nhân viên đã nghỉ việc.";
    static final String REASON_CERTIFICATE = "Chưa đáp ứng điều kiện chứng chỉ.";
    static final String REASON_LIMIT = "Đã đạt giới hạn số ca đăng ký.";
    static final String REASON_SCHEDULE = "Không đáp ứng điều kiện lịch làm việc.";

    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<ApprovalStatus> OCCUPYING = List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);
    private static final int MESSAGE_LIMIT = 950;

    private final StoreRepository storeRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftSlotLimitRepository shiftSlotLimitRepository;
    private final WorkAvailabilityRepository workAvailabilityRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmploymentContractRepository employmentContractRepository;
    private final TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    private final ApprovedLeaveCoverage approvedLeaveCoverage;
    private final ScheduleService scheduleService;
    private final NotificationService notificationService;

    @Override
    public StaffAllocationRoster roster(AuthenticatedUser manager, LocalDate weekStart) {
        Store store = resolveManagerStore(manager);
        LocalDate monday = proposalWeek(weekStart);
        Analysis analysis = analyze(store, monday);
        return StaffAllocationRoster.builder()
                .weekStart(monday)
                .weekEnd(monday.plusDays(6))
                .blockedMessage(analysis.blockedMessage)
                .rows(List.copyOf(analysis.rows))
                .build();
    }

    @Override
    @Transactional
    public Outcome allocate(AuthenticatedUser manager, LocalDate weekStart, List<Integer> employeeIds) {
        Store store = resolveManagerStore(manager);
        LocalDate monday = proposalWeek(weekStart);
        TreeSet<Integer> selected = new TreeSet<>();
        if (employeeIds != null) {
            employeeIds.stream().filter(Objects::nonNull).forEach(selected::add);
        }
        if (selected.isEmpty()) {
            throw new BusinessException(NOTHING_SELECTED_MESSAGE);
        }

        // Khóa theo cùng thứ tự với luồng đăng ký và duyệt ca: nhân viên trước, ô ca sau.
        Map<Integer, Employee> employees = new HashMap<>();
        for (Integer employeeId : selected) {
            Employee employee = employeeRepository.lockById(employeeId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
            if (employee.getStore() == null || !Objects.equals(employee.getStore().getId(), store.getId())) {
                throw new BusinessException(OTHER_STORE_EMPLOYEE_MESSAGE);
            }
            employees.put(employeeId, employee);
        }
        List<ShiftSlotLimit> limits = new ArrayList<>(shiftSlotLimitRepository.findByStoreId(store.getId()));
        limits.sort(Comparator.comparing((ShiftSlotLimit limit) -> limit.getShift().getId())
                .thenComparing(ShiftSlotLimit::getDayOfWeek));
        for (ShiftSlotLimit limit : limits) {
            shiftSlotLimitRepository.lockByShiftAndDay(limit.getShift().getId(), limit.getDayOfWeek());
        }

        // Đọc lại sau khi giữ khóa: dữ liệu có thể đã đổi từ lúc Manager mở danh sách.
        Analysis analysis = analyze(store, monday);
        if (analysis.blockedMessage != null) {
            throw new BusinessException(analysis.blockedMessage);
        }
        List<String> noLongerEligible = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (Integer employeeId : selected) {
            StaffAllocationRoster.Row row = analysis.row(employeeId);
            Candidate candidate = analysis.candidates.get(employeeId);
            if (row == null || !row.isEligible() || candidate == null) {
                String name = employees.get(employeeId).getFullName();
                noLongerEligible.add(name + " (" + (row != null && row.getReason() != null
                        ? stripDot(row.getReason()) : "không thuộc danh sách Staff của cửa hàng") + ")");
                continue;
            }
            candidates.add(candidate);
        }
        if (!noLongerEligible.isEmpty()) {
            throw new BusinessException("Chưa phân bổ ai vì có nhân viên không còn đủ điều kiện: "
                    + String.join("; ", noLongerEligible) + ". Hãy tải lại danh sách và chọn lại.");
        }

        StaffAllocationPlanner.Result plan = StaffAllocationPlanner.plan(
                analysis.slots, candidates, approvedLeaveCoverage::blocksShift);
        List<String> notCovered = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (plan.remainingAfterByEmployee().getOrDefault(candidate.employeeId(), 0) > 0) {
                notCovered.add(candidate.fullName());
            }
        }
        if (!notCovered.isEmpty()) {
            throw new BusinessException("Chưa phân bổ ai vì không đủ ca còn trống để xếp đủ số ca theo hợp đồng cho: "
                    + String.join(", ", notCovered) + ". Hãy bỏ chọn bớt nhân viên rồi phân bổ lại.");
        }

        Map<Integer, List<Pick>> byEmployee = new LinkedHashMap<>();
        for (Pick pick : plan.picks()) {
            byEmployee.computeIfAbsent(pick.candidate().employeeId(), ignored -> new ArrayList<>()).add(pick);
        }
        Map<Integer, Shift> shifts = new HashMap<>();
        for (Shift shift : shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId())) {
            shifts.put(shift.getId(), shift);
        }
        int shiftCount = 0;
        for (Map.Entry<Integer, List<Pick>> entry : byEmployee.entrySet()) {
            Employee employee = employees.get(entry.getKey());
            List<Pick> mine = entry.getValue();
            mine.sort(Comparator.comparing((Pick pick) -> pick.slot().date()).thenComparing(pick -> pick.slot().start()));
            String registrationKey = UUID.randomUUID().toString();
            List<WorkAvailability> rows = new ArrayList<>();
            for (Pick pick : mine) {
                rows.add(WorkAvailability.builder()
                        .employee(employee)
                        .shift(shifts.get(pick.slot().shiftId()))
                        .dayOfWeek(pick.slot().dayOfWeek())
                        .validFrom(monday)
                        .validTo(monday.plusDays(6))
                        .durationCode(AvailabilityDuration.WEEK_1.getCode())
                        .registrationKey(registrationKey)
                        .note(ALLOCATION_NOTE)
                        .status(ApprovalStatus.APPROVED)
                        .build());
            }
            workAvailabilityRepository.saveAll(rows);
            // Xếp thẳng vào lịch làm việc; ScheduleService kiểm tra lại hợp đồng, chứng chỉ, trùng giờ và nghỉ phép.
            for (Pick pick : mine) {
                try {
                    scheduleService.assignShiftFromApprovedAvailability(AssignShiftRequest.builder()
                            .employeeId(employee.getId())
                            .shiftId(pick.slot().shiftId())
                            .workDate(pick.slot().date())
                            .build(), manager);
                } catch (BusinessException ex) {
                    throw new BusinessException("Không xếp được " + employee.getFullName() + " vào "
                            + pick.slot().shiftName() + " ngày " + pick.slot().date().format(FULL_DATE)
                            + ": " + ex.getMessage() + " Chưa phân bổ ai.");
                }
            }
            shiftCount += rows.size();
            notifyAllocated(employee, monday, mine);
        }
        return new Outcome(byEmployee.size(), shiftCount, monday);
    }

    // ---------------------------------------------------------------- phân tích

    private Analysis analyze(Store store, LocalDate monday) {
        Analysis analysis = new Analysis();
        LocalDate sunday = monday.plusDays(6);
        LocalDate today = VietnamTime.today();
        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId());

        // Ca từng nhân viên đang có trong tuần (đơn chờ duyệt, đã duyệt, ca đã phân) và người chiếm từng ô ca.
        Map<Integer, Map<String, Busy>> busyByEmployee = new HashMap<>();
        Map<String, Map<Integer, Boolean>> seatsBySlot = new HashMap<>();
        for (WorkAvailability row : workAvailabilityRepository.findCapacityCandidatesByStore(
                store.getId(), today, ApprovalStatus.PENDING, OCCUPYING)) {
            Shift shift = row.getShift();
            Integer day = patternDay(row);
            if (shift == null || shift.getId() == null || day == null || row.getEmployee() == null) {
                continue;
            }
            LocalDate date = monday.plusDays(day - 1L);
            if (!StaffAvailabilityServiceImpl.occupiesSlot(row, day, date)) {
                continue;
            }
            occupy(busyByEmployee, seatsBySlot, row.getEmployee().getId(), shift, date,
                    row.getStatus() == ApprovalStatus.APPROVED);
        }
        for (ShiftAssignment assignment : shiftAssignmentRepository.findWeeklyAssignmentsForStore(
                store.getId(), monday, sunday, AssignmentStatus.ASSIGNED)) {
            if (assignment.getShift() != null && assignment.getEmployee() != null) {
                occupy(busyByEmployee, seatsBySlot, assignment.getEmployee().getId(),
                        assignment.getShift(), assignment.getWorkDate(), true);
            }
        }

        // Ô ca có cấu hình số nhân viên tối đa và chưa bắt đầu.
        Map<String, Integer> limits = new HashMap<>();
        for (ShiftSlotLimit limit : shiftSlotLimitRepository.findByStoreId(store.getId())) {
            if (limit.getShift() != null && limit.getDayOfWeek() != null && limit.getMaxEmployees() != null) {
                limits.put(limit.getShift().getId() + ":" + limit.getDayOfWeek(), limit.getMaxEmployees());
            }
        }
        for (Shift shift : shifts) {
            for (int day = 1; day <= 7; day++) {
                Integer max = limits.get(shift.getId() + ":" + day);
                LocalDate date = monday.plusDays(day - 1L);
                if (max == null || max < 1 || VietnamTime.isBeforeNow(date, shift.getStartTime())) {
                    continue;
                }
                Map<Integer, Boolean> seats = seatsBySlot.getOrDefault(slotKey(shift.getId(), date), Map.of());
                int approved = (int) seats.values().stream().filter(Boolean::booleanValue).count();
                analysis.slots.add(new Slot(shift.getId(), shift.getShiftName(), shift.getStartTime(),
                        shift.getEndTime(), date, day, max, approved, seats.size() - approved));
            }
        }
        if (shifts.isEmpty()) {
            analysis.blockedMessage = NO_SHIFT_MESSAGE;
        } else if (analysis.slots.isEmpty()) {
            analysis.blockedMessage = NO_LIMIT_MESSAGE;
        }

        // Staff của cửa hàng, kể cả người đã nghỉ việc (hiển thị nhưng không chọn được).
        Integer managerEmployeeId = store.getManager() != null ? store.getManager().getId() : null;
        List<Employee> staff = new ArrayList<>();
        for (Employee employee : employeeRepository.findByStoreIdWithAccount(store.getId())) {
            User account = employee.getUser();
            if (Objects.equals(employee.getId(), managerEmployeeId)
                    || account == null || account.getRole() == null
                    || account.getRole().getRoleName() != RoleName.STAFF) {
                continue;
            }
            staff.add(employee);
        }
        Map<Integer, List<EmploymentContract>> contracts = new HashMap<>();
        if (!staff.isEmpty()) {
            for (EmploymentContract contract : employmentContractRepository.findByEmployeeIds(
                    staff.stream().map(Employee::getId).toList())) {
                contracts.computeIfAbsent(contract.getEmployee().getId(), ignored -> new ArrayList<>()).add(contract);
            }
        }

        List<StaffAllocationRoster.Row> eligibleRows = new ArrayList<>();
        List<StaffAllocationRoster.Row> otherRows = new ArrayList<>();
        for (Employee employee : staff) {
            List<EmploymentContract> owned = contracts.getOrDefault(employee.getId(), List.of());
            Map<String, Busy> busy = busyByEmployee.getOrDefault(employee.getId(), Map.of());
            int registered = busy.size();
            StaffAllocationRoster.Row.RowBuilder row = StaffAllocationRoster.Row.builder()
                    .employeeId(employee.getId())
                    .employeeCode(String.format("NV%03d", employee.getId()))
                    .fullName(employee.getFullName())
                    .initials(initials(employee.getFullName()))
                    .registeredShifts(registered)
                    .registrationKey("na")
                    .registrationLabel("Không áp dụng");
            describeContract(row, owned, monday, today);

            ContractShiftQuota.WeekQuota quota = quotaOf(owned, monday);
            if (!quota.blocked()) {
                int required = quota.required();
                row.requiredShifts(required).missingShifts(Math.max(0, required - registered));
                if (registered == 0) {
                    row.registrationKey("none").registrationLabel("Chưa đăng ký (0/" + required + " ca)");
                } else if (registered < required) {
                    row.registrationKey("short").registrationLabel(
                            "Thiếu " + (required - registered) + " ca (" + registered + "/" + required + ")");
                } else {
                    row.registrationKey("enough").registrationLabel("Đủ số ca (" + registered + "/" + required + ")");
                }
            }

            String[] reason = ineligibility(employee, owned, quota, registered, monday);
            if (reason == null && analysis.blockedMessage == null) {
                int required = quota.required();
                Candidate candidate = new Candidate(employee.getId(), employee.getFullName(),
                        registered == 0 ? Group.UNREGISTERED : Group.SHORT, required - registered,
                        contractDays(owned, monday), busyWithTraining(employee.getId(), busy.values(), monday));
                // Kiểm tra riêng từng người: trùng ca, lớp đào tạo, nghỉ phép, tối đa 2 ca/ngày, ca còn chỗ.
                StaffAllocationPlanner.Result alone = StaffAllocationPlanner.plan(
                        analysis.slots, List.of(candidate), approvedLeaveCoverage::blocksShift);
                int left = alone.remainingAfterByEmployee().getOrDefault(employee.getId(), 0);
                if (left > 0) {
                    int missing = required - registered;
                    reason = new String[]{REASON_SCHEDULE + " Chỉ xếp được " + (missing - left) + "/" + missing
                            + " ca còn thiếu do trùng giờ, lớp đào tạo, nghỉ phép hoặc ca đã đủ người.", "bad"};
                } else {
                    analysis.candidates.put(employee.getId(), candidate);
                }
            }
            if (reason == null) {
                eligibleRows.add(row.eligible(true).build());
            } else {
                otherRows.add(row.eligible(false).reason(reason[0]).reasonTone(reason[1]).build());
            }
        }
        Comparator<StaffAllocationRoster.Row> byName = Comparator.comparing(
                StaffAllocationRoster.Row::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        eligibleRows.sort(Comparator.comparing((StaffAllocationRoster.Row r) -> "none".equals(r.getRegistrationKey()) ? 0 : 1)
                .thenComparing(byName));
        otherRows.sort(byName);
        analysis.rows.addAll(eligibleRows);
        analysis.rows.addAll(otherRows);
        return analysis;
    }

    /**
     * Lý do không đủ điều kiện và màu hiển thị, theo đúng thứ tự kiểm tra; {@code null} khi qua hết.
     * Hợp đồng dùng quy tắc đăng ký ca hiện tại: phải bao trùm trọn tuần và có số ca/tuần.
     */
    private static String[] ineligibility(Employee employee, List<EmploymentContract> owned,
                                          ContractShiftQuota.WeekQuota quota, int registered, LocalDate monday) {
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            return new String[]{REASON_RESIGNED, "bad"};
        }
        if (owned.isEmpty()) {
            return new String[]{REASON_NO_CONTRACT, "warn"};
        }
        if (quota.blocked()) {
            return contractReason(owned, monday, quota.blockMessage());
        }
        if (employee.getStatus() == EmployeeStatus.PENDING_CONTRACT) {
            return new String[]{REASON_NOT_STARTED, "warn"};
        }
        if (employee.getUser() == null || !Boolean.TRUE.equals(employee.getUser().getIsActive())) {
            return new String[]{"Tài khoản đang bị khóa.", "bad"};
        }
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            return new String[]{"Nhân viên đang " + employee.getStatus().getLabel().toLowerCase() + ".", "bad"};
        }
        if (employee.getCertificationStatus() != CertificationStatus.CERTIFIED) {
            return new String[]{REASON_CERTIFICATE, "bad"};
        }
        if (registered >= quota.required()) {
            return new String[]{REASON_LIMIT, "bad"};
        }
        return null;
    }

    /** Diễn giải vì sao không có hợp đồng hợp lệ cho tuần phân bổ. */
    static String[] contractReason(List<EmploymentContract> contracts, LocalDate monday, String blockMessage) {
        if (ContractShiftQuota.NO_QUOTA.equals(blockMessage)) {
            return new String[]{"Hợp đồng chưa quy định số ca làm việc mỗi tuần.", "bad"};
        }
        if (ContractShiftQuota.AMBIGUOUS.equals(blockMessage)) {
            return new String[]{"Tuần này nằm trong nhiều hợp đồng có số ca khác nhau.", "bad"};
        }
        boolean upcoming = contracts.stream()
                .anyMatch(contract -> contract.getStartDate() != null && contract.getStartDate().isAfter(monday));
        EmploymentContract latest = contracts.stream()
                .filter(contract -> contract.getStartDate() != null && !contract.getStartDate().isAfter(monday))
                .max(Comparator.comparing((EmploymentContract contract) -> {
                    LocalDate last = ContractValidityRules.lastWorkDay(contract);
                    return last == null ? LocalDate.MAX : last;
                }))
                .orElse(null);
        if (latest == null) {
            return upcoming ? new String[]{REASON_NOT_STARTED, "warn"} : new String[]{REASON_NO_CONTRACT, "warn"};
        }
        LocalDate last = ContractValidityRules.lastWorkDay(latest);
        if (last != null && !last.isBefore(monday)) {
            if (upcoming) {
                return new String[]{REASON_NOT_STARTED, "warn"};
            }
            return new String[]{"Hợp đồng hết hiệu lực trong tuần phân bổ (ngày làm cuối "
                    + last.format(FULL_DATE) + ").", "bad"};
        }
        if (upcoming) {
            return new String[]{REASON_NOT_STARTED, "warn"};
        }
        if (latest.getTerminatedDate() != null) {
            return new String[]{REASON_TERMINATED_CONTRACT, "bad"};
        }
        if (latest.getRenewalDeclinedAt() != null) {
            return new String[]{REASON_NOT_RENEWED, "bad"};
        }
        return new String[]{REASON_EXPIRED, "bad"};
    }

    /** Hợp đồng hiển thị: bản đang áp dụng hôm nay, nếu không có thì bản cho tuần phân bổ, cuối cùng là bản mới nhất. */
    private static void describeContract(StaffAllocationRoster.Row.RowBuilder row, List<EmploymentContract> owned,
                                         LocalDate monday, LocalDate today) {
        EmploymentContract shown = ContractValidityRules.applicable(owned, today);
        if (shown == null) {
            shown = ContractValidityRules.applicable(owned, monday);
        }
        if (shown == null) {
            shown = owned.stream()
                    .filter(contract -> contract.getStartDate() != null)
                    .max(Comparator.comparing(EmploymentContract::getStartDate)
                            .thenComparing(contract -> contract.getId() == null ? 0 : contract.getId()))
                    .orElse(null);
        }
        if (shown == null) {
            row.contractStatusKey("NONE").contractStatusLabel("Chưa có hợp đồng").contractTone("warn");
            return;
        }
        ContractValidityStatus status = ContractValidityRules.resolve(shown, today,
                ContractValidityRules.hasDirectRenewal(owned, shown.getId()));
        ContractTermType term = shown.getContractTerm() != null
                ? shown.getContractTerm() : ContractTermType.fromEndDate(shown.getEndDate());
        String tone = switch (status) {
            case DANG_HIEU_LUC, CHO_GIA_HAN, DA_GIA_HAN -> "ok";
            case CHO_HIEU_LUC, KHONG_GIA_HAN -> "warn";
            case DA_HET_HAN, DA_CHAM_DUT -> "bad";
        };
        row.contractStatusKey(status.name())
                .contractStatusLabel(status.getLabel())
                .contractTone(tone)
                .contractTermLabel(term.getLabel())
                .position(shown.getJobTitle() != null && !shown.getJobTitle().isBlank() ? shown.getJobTitle().trim() : null);
    }

    private static ContractShiftQuota.WeekQuota quotaOf(List<EmploymentContract> owned, LocalDate monday) {
        try {
            return ContractShiftQuota.describe(owned, monday);
        } catch (BusinessException ex) {
            return new ContractShiftQuota.WeekQuota(null, ContractShiftQuota.AMBIGUOUS);
        }
    }

    private static Set<LocalDate> contractDays(List<EmploymentContract> owned, LocalDate monday) {
        Set<LocalDate> days = new LinkedHashSet<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = monday.plusDays(offset);
            if (owned.stream().anyMatch(contract -> ContractValidityRules.coversWorkDay(contract, date))) {
                days.add(date);
            }
        }
        return days;
    }

    /** Ca đang có cộng các buổi học của lớp đào tạo đã duyệt trong tuần (giờ học chặn ca trùng giờ). */
    private List<Busy> busyWithTraining(Integer employeeId, java.util.Collection<Busy> shifts, LocalDate monday) {
        List<Busy> busy = new ArrayList<>(shifts);
        LocalDate sunday = monday.plusDays(6);
        for (TrainingClass trainingClass : trainingClassEnrollmentRepository.findClassesEndingOnOrAfter(
                employeeId, TrainingClassStatus.APPROVED, monday)) {
            if (trainingClass.getStartDate() == null || trainingClass.getEndDate() == null
                    || trainingClass.getStartTime() == null || trainingClass.getEndTime() == null
                    || trainingClass.getStartDate().isAfter(sunday)) {
                continue;
            }
            LocalDate from = trainingClass.getStartDate().isBefore(monday) ? monday : trainingClass.getStartDate();
            LocalDate to = trainingClass.getEndDate().isAfter(sunday) ? sunday : trainingClass.getEndDate();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                busy.add(new Busy(null, date, trainingClass.getStartTime(), trainingClass.getEndTime()));
            }
        }
        return busy;
    }

    private void occupy(Map<Integer, Map<String, Busy>> busyByEmployee,
                        Map<String, Map<Integer, Boolean>> seatsBySlot,
                        Integer employeeId, Shift shift, LocalDate date, boolean approved) {
        if (employeeId == null || shift.getId() == null || date == null) {
            return;
        }
        String key = slotKey(shift.getId(), date);
        busyByEmployee.computeIfAbsent(employeeId, ignored -> new LinkedHashMap<>())
                .putIfAbsent(key, new Busy(shift.getId(), date, shift.getStartTime(), shift.getEndTime()));
        seatsBySlot.computeIfAbsent(key, ignored -> new HashMap<>()).merge(employeeId, approved, Boolean::logicalOr);
    }

    private void notifyAllocated(Employee employee, LocalDate monday, List<Pick> picks) {
        User account = employee.getUser();
        if (account == null || !Boolean.TRUE.equals(account.getIsActive())) {
            return;
        }
        StringBuilder shifts = new StringBuilder();
        for (Pick pick : picks) {
            if (!shifts.isEmpty()) {
                shifts.append("; ");
            }
            shifts.append(AvailabilityCoverage.dayName(pick.slot().dayOfWeek())).append(' ')
                    .append(pick.slot().date().format(DAY_MONTH)).append(": ")
                    .append(pick.slot().shiftName()).append(" (")
                    .append(pick.slot().start().format(TIME)).append(" – ").append(pick.slot().end().format(TIME))
                    .append(')');
        }
        String message = "Quản lý đã phân bổ cho bạn " + picks.size() + " ca trong tuần "
                + monday.format(DAY_MONTH) + " – " + monday.plusDays(6).format(FULL_DATE) + ": "
                + shifts + ". Các ca đã có trên lịch làm việc.";
        if (message.length() > MESSAGE_LIMIT) {
            message = message.substring(0, MESSAGE_LIMIT - 1) + "…";
        }
        notificationService.notifyUsers(List.of(account), "Bạn được phân bổ ca làm việc", message,
                NotificationType.WORK_AVAILABILITY_APPROVED, "WORK_AVAILABILITY", employee.getId());
    }

    // ---------------------------------------------------------------- tiện ích

    private Store resolveManagerStore(AuthenticatedUser manager) {
        if (manager == null || manager.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException(MANAGER_ONLY_MESSAGE);
        }
        if (manager.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản Manager chưa gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(manager.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Tài khoản Manager chưa được gán cửa hàng."));
    }

    /** Tuần phân bổ là tuần đang xem nếu chưa bắt đầu, nếu không thì tuần tiếp theo. */
    static LocalDate proposalWeek(LocalDate weekStart) {
        LocalDate next = VietnamTime.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        if (weekStart == null) {
            return next;
        }
        LocalDate monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.isBefore(next) ? next : monday;
    }

    private static Integer patternDay(WorkAvailability availability) {
        if (availability.getDayOfWeek() != null) {
            return availability.getDayOfWeek();
        }
        return availability.getWorkDate() != null ? availability.getWorkDate().getDayOfWeek().getValue() : null;
    }

    private static String slotKey(Integer shiftId, LocalDate date) {
        return shiftId + ":" + date;
    }

    private static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "NV";
        }
        String[] parts = fullName.trim().split("\\s+");
        String last = parts[parts.length - 1];
        String first = parts.length > 1 ? parts[0] : "";
        return ((first.isEmpty() ? "" : first.substring(0, 1)) + last.substring(0, 1)).toUpperCase();
    }

    private static String stripDot(String text) {
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static final class Analysis {
        private String blockedMessage;
        private final List<Slot> slots = new ArrayList<>();
        private final List<StaffAllocationRoster.Row> rows = new ArrayList<>();
        private final Map<Integer, Candidate> candidates = new HashMap<>();

        StaffAllocationRoster.Row row(Integer employeeId) {
            return rows.stream().filter(row -> Objects.equals(row.getEmployeeId(), employeeId)).findFirst().orElse(null);
        }
    }
}
