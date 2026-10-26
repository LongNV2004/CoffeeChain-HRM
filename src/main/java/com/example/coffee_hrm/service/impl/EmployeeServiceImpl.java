package com.example.coffee_hrm.service.impl;
import com.example.coffee_hrm.dto.request.CreateEmployeeRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDetail;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDirectory;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmployeeSkillTrainingFact;
import com.example.coffee_hrm.repository.RoleRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.coffee_hrm.dto.request.UpdateEmployeeRequest;
import java.text.Collator;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    static final String ACCESS_DENIED = "Access denied";
    static final String EMPLOYEE_NOT_FOUND = "Employee not found";
    static final String STORE_NOT_FOUND = "Store not found";
    static final String EMPLOYEE_NOT_IN_STORE = "Nhân viên không thuộc cửa hàng này";
    static final String INVALID_ROLE = "Role không hợp lệ. Chỉ chấp nhận MANAGER hoặc STAFF";
    static final String STORE_ALREADY_HAS_MANAGER =
            "Cửa hàng đã có Manager. Hãy chuyển Manager hiện tại về STAFF trước khi chỉ định Manager mới";
    static final String ACCOUNT_REQUIRED = "Nhân viên chưa có tài khoản đăng nhập nên không thể phân quyền";
    static final String ADMIN_ROLE_LOCKED = "Không thể thay đổi role của tài khoản Admin";
    static final String ROLE_UNCHANGED = "Nhân viên đã có role %s";
    static final String TERMINATED_CANNOT_BE_MANAGER = "Nhân viên đã nghỉ việc không thể được chỉ định làm Manager";
    static final String REHIRE_THROUGH_RECRUITMENT =
            "Nhân viên đã nghỉ việc chỉ được trở lại làm việc khi Admin duyệt đề xuất tuyển dụng lại.";
    static final String INACTIVE_ACCOUNT_CANNOT_BE_MANAGER =
            "Tài khoản của nhân viên đang bị khóa nên không thể chỉ định làm Manager";
    static final String ROLE_NOT_CONFIGURED = "Role %s chưa được cấu hình trong hệ thống";
    static final String STORE_NOT_ASSIGNED = "Bạn chưa được phân công quản lý cửa hàng nào.";
    static final String MANAGED_EMPLOYEE_NOT_FOUND = "Không tìm thấy nhân viên.";
    private static final int UPCOMING_SHIFT_LIMIT = 8;
    private static final int RECENT_ATTENDANCE_LIMIT = 10;
    private static final DateTimeFormatter SHIFT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final PasswordEncoder passwordEncoder;
    private static final Set<RoleName> ASSIGNABLE_ROLES = EnumSet.of(RoleName.MANAGER, RoleName.STAFF);

    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;

    @Override
    public List<EmployeeResponse> getAllEmployees(AuthenticatedUser actor) {
        requireAdmin(actor);

        return employeeRepository
                .findAllForAdmin(EmployeeStatus.TERMINATED)
                .stream()
                .map(employee -> toResponse(
                        employee,
                        employee.getUser(),
                        employee.getStore()
                ))
                .toList();
    }
    @Override
    public List<EmployeeResponse> getStoreEmployees(AuthenticatedUser actor, Integer storeId) {
        requireAdmin(actor);
        Store store = storeRepository.findByIdWithManager(storeId)
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
        List<EmployeeResponse> employees = employeeRepository.findByStoreIdWithAccount(storeId).stream()
                .map(employee -> toResponse(employee, employee.getUser(), store))
                .toList();
        return orderedForStoreView(employees);
    }

    @Override
    public EmployeeResponse getStoreEmployee(AuthenticatedUser actor, Integer storeId, Integer employeeId) {
        requireAdmin(actor);
        Store store = storeRepository.findByIdWithManager(storeId)
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
        Employee employee = employeeRepository.findByIdWithStoreAccount(employeeId)
                .orElseThrow(() -> new BusinessException(EMPLOYEE_NOT_FOUND));
        if (employee.getStore() == null || !store.getId().equals(employee.getStore().getId())) {
            throw new BusinessException(EMPLOYEE_NOT_IN_STORE);
        }
        return toResponse(employee, employee.getUser(), store);
    }

    @Override
    public ManagedEmployeeDirectory getManagedDirectory(AuthenticatedUser actor) {
        Store store = requireManagedStore(actor);
        List<EmployeeResponse> employees = orderedForStoreView(
                employeeRepository.findByStoreIdWithAccount(store.getId()).stream()
                        .map(employee -> toResponse(employee, employee.getUser(), store))
                        .toList());
        return toDirectory(store, employees);
    }

    @Override
    public ManagedEmployeeDetail getManagedEmployee(AuthenticatedUser actor, Integer employeeId) {
        Store store = requireManagedStore(actor);
        Employee employee = employeeRepository.findByIdWithStoreAccount(employeeId)
                .orElseThrow(() -> new BusinessException(MANAGED_EMPLOYEE_NOT_FOUND));
        if (employee.getStore() == null || !store.getId().equals(employee.getStore().getId())) {
            throw new BusinessException(EMPLOYEE_NOT_IN_STORE);
        }
        EmployeeResponse profile = toResponse(employee, employee.getUser(), store);
        String certificationLabel = employee.getCertificationStatus() == null
                ? null
                : employee.getCertificationStatus().getLabel();
        return new ManagedEmployeeDetail(
                profile,
                certificationLabel,
                upcomingShifts(employee.getId()),
                recentAttendances(employee.getId()),
                participatedTrainings(employee.getId()),
                skillLines(employee.getId()));
    }

    @Override
    @Transactional

    public EmployeeResponse createEmployee(
            AuthenticatedUser actor,
            CreateEmployeeRequest request
    ) {
        requireAdmin(actor);

        RoleName role = parseAssignableRole(request.getRole());

        if (role == RoleName.MANAGER && !request.isCreateAccount()) {
            throw new BusinessException(
                    "Nhân viên cần có tài khoản để được chỉ định làm Manager"
            );
        }

        String email = request.getEmail() == null
                ? "" : request.getEmail().trim();

        if (email.isBlank()) {
            throw new BusinessException("Email không được để trống");
        }

        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email đã được sử dụng");
        }

        String username = null;

        if (request.isCreateAccount()) {
            username = request.getUsername() == null
                    ? "" : request.getUsername().trim();

            if (username.isBlank()
                    || request.getTemporaryPassword() == null
                    || request.getTemporaryPassword().isBlank()) {
                throw new BusinessException(
                        "Vui lòng nhập tên đăng nhập và mật khẩu tạm thời"
                );
            }

            if (userRepository.findByUsername(username).isPresent()) {
                throw new BusinessException(
                        "Tên đăng nhập đã tồn tại"
                );
            }
        }

        Store store = role == RoleName.MANAGER
                ? storeRepository.findByIdForUpdate(request.getStoreId())
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND))
                : storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));

        Employee employee = Employee.builder()
                .fullName(request.getFullName().trim())
                .phone(request.getPhone().trim())
                .email(email)
                .address(request.getAddress())
                .avatarUrl(request.getAvatarUrl())
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .hireDate(request.getHireDate())
                .build();

        employeeRepository.save(employee);

        User account = null;

        if (request.isCreateAccount()) {
            account = User.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(
                            request.getTemporaryPassword()
                    ))
                    .employee(employee)
                    .role(requireRole(RoleName.STAFF))
                    .build();

            userRepository.save(account);
            employee.setUser(account);

            if (role == RoleName.MANAGER) {
                promoteToManager(employee, account, store);
            }
        }

        return toResponse(employee, account, store);
    }
    @Override
    @Transactional
    public EmployeeResponse updateEmployee(
            AuthenticatedUser actor,
            Integer storeId,
            Integer employeeId,
            UpdateEmployeeRequest request
    ) {
        requireAdmin(actor);

        Employee employee = employeeRepository
                .findByIdWithStoreAndManager(employeeId)
                .orElseThrow(() -> new BusinessException(EMPLOYEE_NOT_FOUND));

        if (!storeId.equals(employee.getStore().getId())) {
            throw new BusinessException(EMPLOYEE_NOT_IN_STORE);
        }

        RoleName targetRole = parseAssignableRole(request.getRole());
        User account = userRepository.findByEmployee_Id(employeeId).orElse(null);

        if (account != null
                && account.getRole().getRoleName() == RoleName.ADMIN) {
            throw new BusinessException(ADMIN_ROLE_LOCKED);
        }

        if (targetRole == RoleName.MANAGER && account == null) {
            throw new BusinessException(ACCOUNT_REQUIRED);
        }

        if (targetRole == RoleName.MANAGER
                && request.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException(TERMINATED_CANNOT_BE_MANAGER);
        }

        List<Store> managedStores =
                storeRepository.findAllByManager_Id(employeeId);

        boolean currentlyManager = !managedStores.isEmpty()
                || (account != null
                && account.getRole().getRoleName() == RoleName.MANAGER);

        if (targetRole == RoleName.MANAGER
                && currentlyManager
                && !storeId.equals(request.getStoreId())) {
            throw new BusinessException(
                    "Hãy chuyển Manager về Staff trước khi chuyển cửa hàng"
            );
        }

        if (employee.getStatus() == EmployeeStatus.TERMINATED
                && request.getStatus() != EmployeeStatus.TERMINATED) {
            throw new BusinessException(REHIRE_THROUGH_RECRUITMENT);
        }

        if (request.getStatus() == EmployeeStatus.TERMINATED) {
            LocalDate terminationDate = request.getTerminationDate();

            if (terminationDate == null
                    || terminationDate.isBefore(request.getHireDate())) {
                throw new BusinessException(
                        "Ngày nghỉ việc phải có và không được trước ngày vào làm"
                );
            }
        }

        String email = request.getEmail().trim();

        if ((employee.getEmail() == null
                || !employee.getEmail().equalsIgnoreCase(email))
                && employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email đã được sử dụng");
        }

        if (targetRole == RoleName.STAFF && currentlyManager) {
            if (account == null) {
                throw new BusinessException(ACCOUNT_REQUIRED);
            }
            demoteToStaff(employee, account);
        }

        Store targetStore = storeRepository
                .findByIdForUpdate(request.getStoreId())
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));

        employee.setFullName(request.getFullName().trim());
        employee.setPhone(request.getPhone().trim());
        employee.setEmail(email);
        employee.setAddress(request.getAddress());
        employee.setAvatarUrl(request.getAvatarUrl());
        employee.setStore(targetStore);
        employee.setHireDate(request.getHireDate());
        employee.setStatus(request.getStatus());
        employee.setTerminationDate(
                request.getStatus() == EmployeeStatus.TERMINATED
                        ? request.getTerminationDate() : null
        );
        if (employee.getStatus() == EmployeeStatus.TERMINATED
                && account != null
                && account.getRole() != null
                && account.getRole().getRoleName() != RoleName.ADMIN
                && account.getRole().getRoleName() != RoleName.MANAGER) {
            account.setIsActive(false);
        }

        if (targetRole == RoleName.MANAGER
                && !targetStore.getId().equals(
                managedStores.isEmpty()
                        ? null : managedStores.get(0).getId()
        )) {
            promoteToManager(employee, account, targetStore);
        }

        return toResponse(employee, account, targetStore);
    }
    @Override
    @Transactional
    public EmployeeResponse changeEmployeeRole(AuthenticatedUser actor, Integer storeId, Integer employeeId, String role) {
        requireAdmin(actor);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(EMPLOYEE_NOT_FOUND));
        // Khóa dòng Store để hai Admin không thể đồng thời chỉ định hai Manager cho cùng một cửa hàng.
        Store store = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
        if (employee.getStore() == null || !store.getId().equals(employee.getStore().getId())) {
            throw new BusinessException(EMPLOYEE_NOT_IN_STORE);
        }
        RoleName targetRole = parseAssignableRole(role);

        User user = userRepository.findByEmployee_Id(employeeId)
                .orElseThrow(() -> new BusinessException(ACCOUNT_REQUIRED));
        if (!ASSIGNABLE_ROLES.contains(user.getRole().getRoleName())) {
            throw new BusinessException(ADMIN_ROLE_LOCKED);
        }

        if (targetRole == RoleName.MANAGER) {
            promoteToManager(employee, user, store);
        } else {
            demoteToStaff(employee, user);
        }
        return toResponse(employee, user, store);
    }

    private void promoteToManager(Employee employee, User user, Store store) {
        Employee currentManager = store.getManager();
        boolean linkedToStore = currentManager != null && currentManager.getId().equals(employee.getId());
        if (user.getRole().getRoleName() == RoleName.MANAGER && linkedToStore) {
            throw new BusinessException(ROLE_UNCHANGED.formatted(RoleName.MANAGER.name()));
        }
        boolean storeHasOtherManager = (currentManager != null && !linkedToStore)
                || userRepository.countOtherStoreMembersWithRole(store.getId(), employee.getId(), RoleName.MANAGER) > 0;
        if (storeHasOtherManager) {
            throw new BusinessException(STORE_ALREADY_HAS_MANAGER);
        }
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException(TERMINATED_CANNOT_BE_MANAGER);
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(INACTIVE_ACCOUNT_CANNOT_BE_MANAGER);
        }

        // Manager chỉ được gắn với cửa hàng của chính mình: gỡ mọi liên kết Manager ở cửa hàng khác.
        List<Store> otherManagedStores = storeRepository.findAllByManager_Id(employee.getId()).stream()
                .filter(managed -> !managed.getId().equals(store.getId()))
                .toList();
        if (!otherManagedStores.isEmpty()) {
            otherManagedStores.forEach(managed -> managed.setManager(null));
            // Phải ghi trước để không vi phạm UX_Stores_ManagerId khi gán cho cửa hàng hiện tại.
            storeRepository.flush();
        }

        user.setRole(requireRole(RoleName.MANAGER));
        store.setManager(employee);
    }

    private void demoteToStaff(Employee employee, User user) {
        List<Store> managedStores = storeRepository.findAllByManager_Id(employee.getId());
        if (user.getRole().getRoleName() == RoleName.STAFF && managedStores.isEmpty()) {
            throw new BusinessException(ROLE_UNCHANGED.formatted(RoleName.STAFF.name()));
        }
        managedStores.forEach(managed -> managed.setManager(null));
        user.setRole(requireRole(RoleName.STAFF));
    }

    private RoleName parseAssignableRole(String role) {
        if (role != null && !role.isBlank()) {
            try {
                RoleName parsed = RoleName.valueOf(role.trim().toUpperCase(Locale.ROOT));
                if (ASSIGNABLE_ROLES.contains(parsed)) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // Rơi xuống thông báo role không hợp lệ bên dưới.
            }
        }
        throw new BusinessException(INVALID_ROLE);
    }

    private Role requireRole(RoleName roleName) {
        return roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new BusinessException(ROLE_NOT_CONFIGURED.formatted(roleName.name())));
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException(ACCESS_DENIED);
        }
    }

    /**
     * Cửa hàng của Manager lấy từ {@code Stores.ManagerId}. Không dùng storeId trên request hay trong session.
     */
    private Store requireManagedStore(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER || actor.getEmployeeId() == null) {
            throw new BusinessException(ACCESS_DENIED);
        }
        Store managed = storeRepository.findByManager_Id(actor.getEmployeeId())
                .orElseThrow(() -> new BusinessException(STORE_NOT_ASSIGNED));
        return storeRepository.findByIdWithManager(managed.getId())
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
    }

    private ManagedEmployeeDirectory toDirectory(Store store, List<EmployeeResponse> employees) {
        long managers = employees.stream().filter(EmployeeServiceImpl::isListedManager).count();
        long staff = employees.stream().filter(EmployeeServiceImpl::isListedStaff).count();
        long noAccount = employees.stream().filter(EmployeeServiceImpl::hasNoAccount).count();
        return new ManagedEmployeeDirectory(
                store.getId(),
                store.getStoreName(),
                store.getAddress(),
                employees.size(),
                countStatus(employees, EmployeeStatus.ACTIVE),
                countStatus(employees, EmployeeStatus.ON_LEAVE),
                countStatus(employees, EmployeeStatus.TERMINATED),
                managers,
                staff,
                noAccount,
                employees.size() - managers - staff - noAccount,
                employees);
    }

    private static long countStatus(List<EmployeeResponse> employees, EmployeeStatus status) {
        return employees.stream().filter(employee -> employee.getStatus() == status).count();
    }

    private static boolean isListedManager(EmployeeResponse employee) {
        return storeViewRank(employee) == 0;
    }

    private static boolean isListedStaff(EmployeeResponse employee) {
        return storeViewRank(employee) == 1;
    }

    private static boolean hasNoAccount(EmployeeResponse employee) {
        return !isListedManager(employee) && employee.getRoleName() == null;
    }

    private List<ManagedEmployeeDetail.ShiftLine> upcomingShifts(Integer employeeId) {
        return shiftAssignmentRepository.findUpcomingByEmployee(
                        employeeId,
                        VietnamTime.today(),
                        AssignmentStatus.ASSIGNED,
                        PageRequest.of(0, UPCOMING_SHIFT_LIMIT))
                .stream()
                .map(this::toShiftLine)
                .toList();
    }

    private ManagedEmployeeDetail.ShiftLine toShiftLine(ShiftAssignment assignment) {
        Shift shift = assignment.getShift();
        return new ManagedEmployeeDetail.ShiftLine(
                assignment.getWorkDate(),
                shift == null ? null : shift.getShiftName(),
                timeRange(shift == null ? null : shift.getStartTime(), shift == null ? null : shift.getEndTime()));
    }

    private List<ManagedEmployeeDetail.AttendanceLine> recentAttendances(Integer employeeId) {
        return attendanceRepository.findRecentByEmployee(employeeId, PageRequest.of(0, RECENT_ATTENDANCE_LIMIT))
                .stream()
                .map(this::toAttendanceLine)
                .toList();
    }

    private ManagedEmployeeDetail.AttendanceLine toAttendanceLine(Attendance attendance) {
        Shift shift = attendance.getShift();
        String shiftName = shift != null && shift.getShiftName() != null
                ? shift.getShiftName()
                : attendance.getScheduledShiftName();
        return new ManagedEmployeeDetail.AttendanceLine(
                attendance.getWorkDate(),
                shiftName,
                attendanceStatusLabel(attendance.getStatus()),
                attendance.getCheckInTime(),
                attendance.getCheckOutTime());
    }

    private List<ManagedEmployeeDetail.TrainingLine> participatedTrainings(Integer employeeId) {
        return trainingClassEnrollmentRepository
                .findParticipatedByEmployee(employeeId, TrainingClassStatus.REJECTED)
                .stream()
                .map(this::toTrainingLine)
                .toList();
    }

    private ManagedEmployeeDetail.TrainingLine toTrainingLine(TrainingClassEnrollment enrollment) {
        TrainingClass trainingClass = enrollment.getTrainingClass();
        String resultLabel = enrollment.getResult() == null
                ? "Chưa đánh giá"
                : enrollment.getResult().getLabel();
        String statusLabel = trainingClass.getStatus() == null
                ? null
                : trainingClass.getStatus().getLabel();
        return new ManagedEmployeeDetail.TrainingLine(
                trainingClass.getClassName(),
                trainingClass.getStartDate(),
                trainingClass.getEndDate(),
                statusLabel,
                resultLabel);
    }

    private List<ManagedEmployeeDetail.SkillLine> skillLines(Integer employeeId) {
        Map<Integer, EmployeeSkillTrainingFact> bestBySkill = new LinkedHashMap<>();
        for (EmployeeSkillTrainingFact fact : trainingClassEnrollmentRepository.findSkillTrainingFacts(
                List.of(employeeId), TrainingClassStatus.REJECTED)) {
            bestBySkill.merge(fact.getSkillId(), fact, EmployeeServiceImpl::preferSkillFact);
        }
        return bestBySkill.values().stream()
                .map(fact -> new ManagedEmployeeDetail.SkillLine(fact.getSkillName(), skillStatusLabel(fact)))
                .toList();
    }

    private static EmployeeSkillTrainingFact preferSkillFact(
            EmployeeSkillTrainingFact current,
            EmployeeSkillTrainingFact incoming) {
        return skillRank(incoming) < skillRank(current) ? incoming : current;
    }

    private static int skillRank(EmployeeSkillTrainingFact fact) {
        if (fact.getResult() == TrainingResult.PASS) {
            return 0;
        }
        if (fact.getResult() == null) {
            return 1;
        }
        return 2;
    }

    private static String skillStatusLabel(EmployeeSkillTrainingFact fact) {
        if (fact.getResult() == TrainingResult.PASS) {
            return "Đã đạt";
        }
        if (fact.getResult() == TrainingResult.NOT_PASS) {
            return "Không đạt";
        }
        return "Đang học";
    }

    private static String attendanceStatusLabel(AttendanceStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PRESENT -> "Có mặt";
            case ON_TIME -> "Đúng giờ";
            case LATE -> "Đi muộn";
            case EARLY_LEAVE -> "Về sớm";
            case LATE_AND_EARLY -> "Đi muộn và về sớm";
            case LATE_CHECKOUT -> "Check-out muộn";
            case LATE_AND_LATE_CHECKOUT -> "Đi muộn và check-out muộn";
            case NOT_PARTICIPATED -> "Không tham gia";
            case ABSENT -> "Vắng";
        };
    }

    private static String timeRange(LocalTime start, LocalTime end) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null) {
            return SHIFT_TIME.format(end);
        }
        if (end == null) {
            return SHIFT_TIME.format(start);
        }
        return SHIFT_TIME.format(start) + "–" + SHIFT_TIME.format(end);
    }

    /**
     * Manager của cửa hàng đứng trước Staff. Chỉ sắp xếp bản ghi trả về, không đổi role hay dữ liệu.
     */
    private List<EmployeeResponse> orderedForStoreView(List<EmployeeResponse> employees) {
        Collator nameOrder = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
        nameOrder.setStrength(Collator.PRIMARY);
        return employees.stream()
                .sorted(Comparator
                        .comparingInt(EmployeeServiceImpl::storeViewRank)
                        .thenComparing(EmployeeResponse::getFullName, Comparator.nullsLast(nameOrder))
                        .thenComparing(EmployeeResponse::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static int storeViewRank(EmployeeResponse employee) {
        if (employee.isStoreManager() || employee.getRoleName() == RoleName.MANAGER) {
            return 0;
        }
        if (employee.getRoleName() == RoleName.STAFF) {
            return 1;
        }
        return 2;
    }

    private EmployeeResponse toResponse(Employee employee, User user, Store store) {
        Employee manager = store.getManager();
        return EmployeeResponse.builder()
                .id(employee.getId())
                .fullName(employee.getFullName())
                .phone(employee.getPhone())
                .email(employee.getEmail())
                .address(employee.getAddress())
                .avatarUrl(employee.getAvatarUrl())
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .status(employee.getStatus())
                .hireDate(employee.getHireDate())
                .terminationDate(employee.getTerminationDate())
                .createdAt(employee.getCreatedAt())
                .userId(user != null ? user.getId() : null)
                .roleName(user != null && user.getRole() != null ? user.getRole().getRoleName() : null)
                .accountActive(user == null ? null : Boolean.TRUE.equals(user.getIsActive()))
                .hasCertificate(Boolean.TRUE.equals(employee.getHasCertificate()))
                .storeManager(manager != null && manager.getId().equals(employee.getId()))
                .build();
    }
}
