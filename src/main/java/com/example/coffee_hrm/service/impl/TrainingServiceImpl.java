package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import com.example.coffee_hrm.common.enums.TrainingType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AddTrainingClassStudentsRequest;
import com.example.coffee_hrm.dto.request.CreateCentralizedTrainingRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.EvaluateTrainingStudentRequest;
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
import com.example.coffee_hrm.dto.response.EmployeeTrainingClassResponse;
import com.example.coffee_hrm.dto.response.TrainingClassResponse;
import com.example.coffee_hrm.dto.response.TrainingClassStudentResponse;
import com.example.coffee_hrm.dto.response.TrainingEmployeeOption;
import com.example.coffee_hrm.dto.response.TrainingSkillResponse;
import com.example.coffee_hrm.dto.response.TrainingTrainerOption;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import com.example.coffee_hrm.entity.TrainingSkill;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeOpenSkill;
import com.example.coffee_hrm.repository.EmployeePassedSkill;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.repository.TrainingClassRepository;
import com.example.coffee_hrm.repository.TrainingSkillRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingServiceImpl implements TrainingService {

    private final TrainingSkillRepository trainingSkillRepository;
    private final TrainingClassRepository trainingClassRepository;
    private final TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TRAINING_CLASS_REF = "TRAINING_CLASS";
    static final int CLASS_LIST_PAGE_SIZE = 6;

    @Override
    public List<TrainingSkillResponse> listSkills() {
        return trainingSkillRepository.findAllWithCreator().stream()
                .map(this::toSkillResponse)
                .toList();
    }

    @Override
    public List<TrainingSkillResponse> listActiveSkills() {
        return trainingSkillRepository.findByStatusWithCreator(TrainingSkillStatus.ACTIVE).stream()
                .map(this::toSkillResponse)
                .toList();
    }

    @Override
    @Transactional
    public TrainingSkillResponse createSkill(CreateTrainingSkillRequest request, AuthenticatedUser actor) {
        requireManager(actor);
        String skillName = requireSkillName(request.getSkillName());
        if (trainingSkillRepository.existsBySkillNameIgnoreCase(skillName)) {
            throw new BusinessException("Tên kỹ năng đã tồn tại.");
        }

        TrainingSkill skill = TrainingSkill.builder()
                .skillName(skillName)
                .description(blankToNull(request.getDescription()))
                .requirements(blankToNull(request.getRequirements()))
                .status(TrainingSkillStatus.ACTIVE)
                .createdBy(loadUser(actor.getUserId()))
                .build();
        return toSkillResponse(trainingSkillRepository.save(skill));
    }

    @Override
    @Transactional
    public TrainingSkillResponse updateSkill(Integer skillId, UpdateTrainingSkillRequest request, AuthenticatedUser actor) {
        requireManager(actor);
        TrainingSkill skill = trainingSkillRepository.findById(skillId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy kỹ năng đào tạo."));
        if (skill.getStatus() == TrainingSkillStatus.INACTIVE) {
            throw new BusinessException("Không thể sửa kỹ năng đã ngừng sử dụng.");
        }

        String skillName = requireSkillName(request.getSkillName());
        if (trainingSkillRepository.existsBySkillNameIgnoreCaseAndIdNot(skillName, skillId)) {
            throw new BusinessException("Tên kỹ năng đã tồn tại.");
        }

        skill.setSkillName(skillName);
        skill.setDescription(blankToNull(request.getDescription()));
        skill.setRequirements(blankToNull(request.getRequirements()));
        return toSkillResponse(skill);
    }

    @Override
    @Transactional
    public TrainingSkillResponse deactivateSkill(Integer skillId, AuthenticatedUser actor) {
        requireManager(actor);
        TrainingSkill skill = trainingSkillRepository.findById(skillId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy kỹ năng đào tạo."));
        if (skill.getStatus() == TrainingSkillStatus.INACTIVE) {
            throw new BusinessException("Kỹ năng này đã ngừng sử dụng.");
        }
        skill.setStatus(TrainingSkillStatus.INACTIVE);
        return toSkillResponse(skill);
    }

    @Override
    @Transactional
    public TrainingSkillResponse activateSkill(Integer skillId, AuthenticatedUser actor) {
        requireManager(actor);
        TrainingSkill skill = trainingSkillRepository.findById(skillId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy kỹ năng đào tạo."));
        if (skill.getStatus() == TrainingSkillStatus.ACTIVE) {
            throw new BusinessException("Kỹ năng này đang được sử dụng.");
        }
        skill.setStatus(TrainingSkillStatus.ACTIVE);
        return toSkillResponse(skill);
    }

    @Override
    public boolean managerHasAssignedStore(AuthenticatedUser manager) {
        requireManager(manager);
        return resolveManagerStore(manager) != null;
    }

    @Override
    public List<TrainingClassStudentResponse> listEmployeesForNewStoreClass(AuthenticatedUser manager) {
        requireManager(manager);
        Store store = resolveManagerStore(manager);
        if (store == null) {
            return List.of();
        }
        List<Employee> employees = excludeManagerAccounts(
                employeeRepository.findByStore_IdAndStatus(store.getId(), EmployeeStatus.ACTIVE));
        Map<Integer, Set<Integer>> passedSkills = passedSkillIdsByEmployee(employees);
        Map<Integer, List<EmployeeOpenSkill>> openSkills = openSkillsByEmployee(employees, null);
        return employees.stream()
                .sorted(Comparator.comparing(Employee::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(employee -> toAvailableEmployee(employee, passedSkills, openSkills, null))
                .toList();
    }

    @Override
    public List<Store> listActiveStores(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo tập trung.");
        return storeRepository.findByIsActiveTrueOrderByStoreNameAsc();
    }

    @Override
    public List<TrainingTrainerOption> listTrainerCandidates(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo tập trung.");
        return userRepository.findActiveByRoleName(RoleName.MANAGER).stream()
                .map(user -> TrainingTrainerOption.builder()
                        .userId(user.getId())
                        .fullName(userDisplayName(user))
                        .storeName(user.getEmployee() != null && user.getEmployee().getStore() != null
                                ? user.getEmployee().getStore().getStoreName()
                                : null)
                        .build())
                .sorted(Comparator.comparing(TrainingTrainerOption::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public List<TrainingEmployeeOption> listActiveEmployees(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo tập trung.");
        List<Employee> employees = excludeManagerAccounts(
                employeeRepository.findByStatusWithStore(EmployeeStatus.ACTIVE)).stream()
                .filter(employee -> employee.getStore() != null && employee.getStore().getId() != null)
                .toList();
        Map<Integer, Set<Integer>> passedSkills = passedSkillIdsByEmployee(employees);
        Map<Integer, List<EmployeeOpenSkill>> openSkills = openSkillsByEmployee(employees, null);
        return employees.stream()
                .map(employee -> TrainingEmployeeOption.builder()
                        .employeeId(employee.getId())
                        .fullName(employee.getFullName())
                        .email(employee.getEmail())
                        .storeId(employee.getStore().getId())
                        .storeName(employee.getStore().getStoreName())
                        .certifiedSkillIds(certifiedSkillIdsFor(employee.getId(), passedSkills))
                        .studyingSkillIds(studyingSkillIdsFor(employee.getId(), openSkills))
                        .build())
                .toList();
    }

    @Override
    public Page<TrainingClassResponse> listClassesForManager(AuthenticatedUser manager,
                                                             Integer skillId,
                                                             LocalDate date,
                                                             String keyword,
                                                             String sortDir,
                                                             int page) {
        requireManager(manager);
        Store store = resolveManagerStore(manager);
        Sort sort = classListSort(date, sortDir);
        int pageIndex = Math.max(page, 1) - 1;
        Pageable pageable = PageRequest.of(pageIndex, CLASS_LIST_PAGE_SIZE, sort);
        String normalizedKeyword = blankToNull(keyword);
        Integer storeId = store == null ? -1 : store.getId();

        LocalTime currentTime = VietnamTime.currentTime();
        Page<TrainingClass> result = trainingClassRepository.searchApprovedActiveForManager(
                storeId,
                manager.getUserId(),
                TrainingType.STORE_TRAINING,
                TrainingClassStatus.APPROVED,
                VietnamTime.today(),
                currentTime,
                skillId,
                date,
                normalizedKeyword,
                pageable);
        if (result.getTotalPages() > 0 && pageIndex >= result.getTotalPages()) {
            pageable = PageRequest.of(result.getTotalPages() - 1, CLASS_LIST_PAGE_SIZE, sort);
            result = trainingClassRepository.searchApprovedActiveForManager(
                    storeId,
                    manager.getUserId(),
                    TrainingType.STORE_TRAINING,
                    TrainingClassStatus.APPROVED,
                    VietnamTime.today(),
                    currentTime,
                    skillId,
                    date,
                    normalizedKeyword,
                    pageable);
        }
        return result.map(this::toClassResponse);
    }

    @Override
    public List<TrainingClassResponse> listSubmittedClassesForManager(AuthenticatedUser manager) {
        requireManager(manager);
        Store store = resolveManagerStore(manager);
        Integer storeId = store == null ? -1 : store.getId();
        return trainingClassRepository.findOpenRequestsForManager(
                        storeId,
                        manager.getUserId(),
                        TrainingType.STORE_TRAINING,
                        VietnamTime.today(),
                        VietnamTime.currentTime()).stream()
                .map(this::toClassResponse)
                .toList();
    }

    @Override
    public List<TrainingClassResponse> listEndedClassesForManager(AuthenticatedUser manager) {
        requireManager(manager);
        Store store = resolveManagerStore(manager);
        Integer storeId = store == null ? -1 : store.getId();
        return trainingClassRepository.findEndedApprovedForManager(
                        storeId,
                        manager.getUserId(),
                        TrainingType.STORE_TRAINING,
                        TrainingClassStatus.APPROVED,
                        VietnamTime.today(),
                        VietnamTime.currentTime()).stream()
                .map(this::toClassResponse)
                .toList();
    }

    @Override
    public TrainingClassResponse getApprovedClassDetailForManager(Integer classId, AuthenticatedUser manager) {
        TrainingClass trainingClass = requireManagedApprovedClass(classId, manager);
        List<TrainingClassEnrollment> enrollments = trainingClassEnrollmentRepository.findByClassIdWithEmployee(classId);
        return toClassResponse(trainingClass, enrollments).toBuilder()
                .evaluable(isTrainer(trainingClass, manager))
                .build();
    }

    @Override
    public List<TrainingClassStudentResponse> listAvailableEmployeesForClass(Integer classId, AuthenticatedUser manager) {
        TrainingClass trainingClass = requireManagedApprovedClass(classId, manager);
        Set<Integer> enrolledIds = new HashSet<>(trainingClassEnrollmentRepository.findEmployeeIdsByClassId(classId));
        List<Employee> employees = employeesAllowedForClass(trainingClass).stream()
                .filter(employee -> !enrolledIds.contains(employee.getId()))
                .toList();
        Map<Integer, Set<Integer>> passedSkills = passedSkillIdsByEmployee(employees);
        Map<Integer, List<EmployeeOpenSkill>> openSkills = openSkillsByEmployee(employees, classId);
        Set<Integer> classSkillIds = skillIdsOf(trainingClass);
        return employees.stream()
                .sorted(Comparator.comparing(Employee::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(employee -> toAvailableEmployee(employee, passedSkills, openSkills, classSkillIds))
                .toList();
    }

    @Override
    @Transactional
    public int addStudentsToClass(Integer classId, AddTrainingClassStudentsRequest request, AuthenticatedUser manager) {
        TrainingClass trainingClass = requireManagedApprovedClass(classId, manager);
        if (!isTrainer(trainingClass, manager)) {
            throw new BusinessException("Chỉ người đào tạo của lớp mới được thêm học viên.");
        }
        List<Integer> employeeIds = request == null ? List.of() : request.getEmployeeIds();
        return enrollEmployees(trainingClass, employeeIds, true);
    }

    @Override
    @Transactional
    public void evaluateStudent(Integer classId,
                                Integer employeeId,
                                EvaluateTrainingStudentRequest request,
                                AuthenticatedUser manager) {
        requireManager(manager);
        if (request == null || request.getResult() == null) {
            throw new BusinessException("Vui lòng chọn kết quả đánh giá.");
        }
        if (request.getResult() != TrainingResult.PASS && request.getResult() != TrainingResult.NOT_PASS) {
            throw new BusinessException("Kết quả đánh giá chỉ được là Đạt hoặc Không đạt.");
        }
        String note = blankToNull(request.getNote());
        if (note != null && note.length() > 500) {
            throw new BusinessException("Ghi chú tối đa 500 ký tự.");
        }

        TrainingClass trainingClass = requireManagedApprovedClass(classId, manager);
        if (!isTrainer(trainingClass, manager)) {
            throw new BusinessException("Chỉ người đào tạo của lớp mới được đánh giá học viên.");
        }
        if (!hasEnded(trainingClass)) {
            throw new BusinessException("Chỉ được đánh giá sau khi lớp đào tạo kết thúc.");
        }

        TrainingClassEnrollment enrollment = trainingClassEnrollmentRepository
                .findByClassAndEmployee(classId, employeeId)
                .orElseThrow(() -> new BusinessException("Nhân viên không thuộc lớp đào tạo này."));
        Employee employee = enrollment.getEmployee();
        if (!employeeBelongsToClass(trainingClass, employee)) {
            throw new BusinessException(trainingClass.getTrainingType() == TrainingType.CENTRALIZED_TRAINING
                    ? "Nhân viên không thuộc các cửa hàng của lớp đào tạo."
                    : "Nhân viên không thuộc cửa hàng của bạn.");
        }

        boolean alreadyEvaluated = enrollment.getResult() != null;
        if (alreadyEvaluated && !request.isUpdating()) {
            throw new BusinessException("Nhân viên này đã được đánh giá. Hãy dùng chức năng cập nhật kết quả.");
        }
        if (!alreadyEvaluated && request.isUpdating()) {
            throw new BusinessException("Nhân viên chưa được đánh giá.");
        }

        enrollment.setResult(request.getResult());
        enrollment.setEvaluationNote(note);
        enrollment.setEvaluatedAt(VietnamTime.now());
        enrollment.setEvaluatedBy(loadUser(manager.getUserId()));
        employee.setCertificationStatus(request.getResult() == TrainingResult.PASS
                ? CertificationStatus.CERTIFIED
                : CertificationStatus.NOTCERTIFIED);
    }

    @Override
    public List<EmployeeTrainingClassResponse> listClassesForEmployee(AuthenticatedUser employee) {
        requireEmployee(employee);
        return trainingClassEnrollmentRepository
                .findByEmployeeAndStatusWithClass(employee.getEmployeeId(), TrainingClassStatus.APPROVED)
                .stream()
                .map(this::toEmployeeClass)
                .sorted(Comparator
                        .comparing(EmployeeTrainingClassResponse::isEnded)
                        .thenComparing(EmployeeTrainingClassResponse::getStartDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EmployeeTrainingClassResponse::getStartTime,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public EmployeeTrainingClassResponse getClassForEmployee(Integer classId, AuthenticatedUser employee) {
        requireEmployee(employee);
        TrainingClassEnrollment enrollment = trainingClassEnrollmentRepository
                .findOwnedByClassAndEmployee(classId, employee.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy lớp đào tạo."));
        TrainingClass trainingClass = enrollment.getTrainingClass();
        if (trainingClass == null || trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        return toEmployeeClass(enrollment);
    }

    @Override
    public long countOngoingClassesForEmployee(AuthenticatedUser employee) {
        if (employee == null || employee.getRoleName() != RoleName.STAFF || employee.getEmployeeId() == null) {
            return 0;
        }
        return trainingClassEnrollmentRepository.findClassesEndingOnOrAfter(
                        employee.getEmployeeId(),
                        TrainingClassStatus.APPROVED,
                        VietnamTime.today())
                .stream()
                .filter(trainingClass -> !hasEnded(trainingClass))
                .count();
    }

    @Override
    @Transactional
    public void deleteClassForManager(Integer classId, AuthenticatedUser manager) {
        TrainingClass trainingClass = requireVisibleApprovedClass(classId, manager);
        if (trainingClass.getTrainingType() == TrainingType.CENTRALIZED_TRAINING) {
            throw new BusinessException("Không thể xóa lớp đào tạo tập trung.");
        }
        trainingClassRepository.delete(trainingClass);
    }

    @Override
    @Transactional
    public TrainingClassResponse createClass(CreateTrainingClassRequest request, AuthenticatedUser manager) {
        requireManager(manager);
        Store store = requireManagerStore(manager);
        if (manager.getStoreId() != null && !manager.getStoreId().equals(store.getId())) {
            throw new BusinessException("Bạn chỉ được tạo lớp đào tạo cho cửa hàng của mình.");
        }
        validateSchedule(request.getStartDate(), request.getEndDate(), request.getStartTime(), request.getEndTime());
        String className = requireUniqueOpenClassName(request.getClassName());
        List<TrainingSkill> skills = requireActiveSkills(request.getSkillIds());
        ensureNoScheduleOverlap(List.of(store.getId()), request.getStartDate(), request.getEndDate(),
                request.getStartTime(), request.getEndTime(), null);

        User creator = loadUser(manager.getUserId());
        TrainingClass trainingClass = TrainingClass.builder()
                .trainingType(TrainingType.STORE_TRAINING)
                .skills(new LinkedHashSet<>(skills))
                .store(store)
                .participatingStores(new LinkedHashSet<>(List.of(store)))
                .className(className)
                .trainer(creator)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .maxParticipants(request.getMaxParticipants())
                .notes(blankToNull(request.getNotes()))
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .createdBy(creator)
                .build();

        TrainingClass saved = trainingClassRepository.save(trainingClass);
        enrollEmployees(saved, request.getEmployeeIds(), false);
        TrainingClass detailed = trainingClassRepository.findByIdWithDetails(saved.getId()).orElse(saved);
        notifyAdminsNewClass(detailed, manager);
        return toClassResponse(detailed);
    }

    @Override
    @Transactional
    public TrainingClassResponse createCentralizedClass(CreateCentralizedTrainingRequest request,
                                                        AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo tập trung.");
        validateSchedule(request.getStartDate(), request.getEndDate(), request.getStartTime(), request.getEndTime());
        String className = requireUniqueOpenClassName(request.getClassName());
        List<TrainingSkill> skills = requireActiveSkills(request.getSkillIds());
        List<Store> stores = requireStores(request.getStoreIds());
        if (stores.size() < 2) {
            throw new BusinessException("Đào tạo tập trung phải chọn ít nhất hai cửa hàng.");
        }
        User trainer = requireManagerTrainer(request.getTrainerId());
        if (trainer.getId().equals(admin.getUserId())) {
            throw new BusinessException("Người tạo lớp và người đào tạo là hai vai trò khác nhau.");
        }
        ensureNoScheduleOverlap(stores.stream().map(Store::getId).toList(),
                request.getStartDate(), request.getEndDate(), request.getStartTime(), request.getEndTime(), null);

        User creator = loadUser(admin.getUserId());
        TrainingClass trainingClass = TrainingClass.builder()
                .trainingType(TrainingType.CENTRALIZED_TRAINING)
                .skills(new LinkedHashSet<>(skills))
                .participatingStores(new LinkedHashSet<>(stores))
                .className(className)
                .trainer(trainer)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .maxParticipants(request.getMaxParticipants())
                .notes(blankToNull(request.getNotes()))
                .status(TrainingClassStatus.APPROVED)
                .createdBy(creator)
                .approvedBy(creator)
                .approvedAt(VietnamTime.now())
                .build();

        TrainingClass saved = trainingClassRepository.save(trainingClass);
        int enrolled = enrollEmployees(saved, request.getEmployeeIds(), true);
        if (enrolled < 1) {
            throw new BusinessException("Vui lòng chọn ít nhất một nhân viên.");
        }
        TrainingClass detailed = trainingClassRepository.findByIdWithDetails(saved.getId()).orElse(saved);
        notifyAssignedTrainer(detailed, trainer);
        return toClassResponse(detailed);
    }

    @Override
    public List<TrainingClassResponse> listPendingClasses() {
        return trainingClassRepository.findByStatusWithDetails(TrainingClassStatus.PENDING_APPROVAL).stream()
                .map(this::toClassResponse)
                .toList();
    }

    @Override
    public List<TrainingClassResponse> listAllClassesForAdmin() {
        return trainingClassRepository.findAllWithDetails().stream()
                .map(this::toClassResponse)
                .toList();
    }

    @Override
    @Transactional
    public TrainingClassResponse approveClass(Integer classId, AuthenticatedUser admin) {
        return reviewClass(classId, admin, TrainingClassStatus.APPROVED);
    }

    @Override
    @Transactional
    public TrainingClassResponse rejectClass(Integer classId, AuthenticatedUser admin) {
        return reviewClass(classId, admin, TrainingClassStatus.REJECTED);
    }

    @Override
    public long countPendingClasses() {
        return trainingClassRepository.countByStatus(TrainingClassStatus.PENDING_APPROVAL);
    }

    private TrainingClassResponse reviewClass(Integer classId, AuthenticatedUser admin, TrainingClassStatus targetStatus) {
        requireAdmin(admin);
        TrainingClass trainingClass = trainingClassRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lớp đào tạo."));
        if (trainingClass.getStatus() != TrainingClassStatus.PENDING_APPROVAL) {
            throw new BusinessException("Chỉ được duyệt lớp đang chờ phê duyệt.");
        }

        User reviewer = loadUser(admin.getUserId());
        trainingClass.setStatus(targetStatus);
        trainingClass.setApprovedBy(reviewer);
        trainingClass.setApprovedAt(VietnamTime.now());
        notifyManagerReviewResult(trainingClass, targetStatus);
        return toClassResponse(trainingClass);
    }

    private void validateSchedule(LocalDate startDate, LocalDate endDate, LocalTime startTime, LocalTime endTime) {
        if (startDate == null || endDate == null || startTime == null || endTime == null) {
            throw new BusinessException("Vui lòng chọn đầy đủ ngày và giờ của lớp.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("Giờ bắt đầu phải nhỏ hơn giờ kết thúc.");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        if (VietnamTime.isBeforeNow(startDate, startTime)) {
            throw new BusinessException(
                    "Thời gian bắt đầu đã qua. Vui lòng chọn thời điểm từ hiện tại trở đi theo giờ Việt Nam.");
        }
    }

    private void ensureNoScheduleOverlap(Collection<Integer> storeIds,
                                         LocalDate startDate,
                                         LocalDate endDate,
                                         LocalTime startTime,
                                         LocalTime endTime,
                                         Integer excludeClassId) {
        boolean overlapped = storeIds.stream()
                .distinct()
                .flatMap(storeId -> trainingClassRepository
                        .findActiveByStoreId(storeId, TrainingClassStatus.REJECTED).stream())
                .filter(existing -> excludeClassId == null || !excludeClassId.equals(existing.getId()))
                .anyMatch(existing -> overlaps(startDate, endDate, startTime, endTime, existing));
        if (overlapped) {
            throw new BusinessException("Lịch lớp bị trùng với lớp đào tạo khác của cửa hàng.");
        }
    }

    private boolean overlaps(LocalDate startDate,
                             LocalDate endDate,
                             LocalTime startTime,
                             LocalTime endTime,
                             TrainingClass existing) {
        boolean datesOverlap = !startDate.isAfter(existing.getEndDate())
                && !endDate.isBefore(existing.getStartDate());
        if (!datesOverlap) {
            return false;
        }

        LocalTime existingStartTime = existing.getStartTime() != null ? existing.getStartTime() : LocalTime.MIN;
        LocalTime existingEndTime = existing.getEndTime() != null ? existing.getEndTime() : LocalTime.MAX;
        return startTime.isBefore(existingEndTime) && endTime.isAfter(existingStartTime);
    }

    private Sort classListSort(LocalDate date, String sortDir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort startTimeSort = Sort.by(direction, "startTime").and(Sort.by(Sort.Direction.ASC, "id"));
        if (date != null) {
            return startTimeSort;
        }
        return Sort.by(Sort.Direction.ASC, "startDate").and(startTimeSort);
    }

    private void notifyAdminsNewClass(TrainingClass trainingClass, AuthenticatedUser actor) {
        List<User> admins = userRepository.findActiveByRoleName(RoleName.ADMIN);
        String actorName = actor.getDisplayName() != null ? actor.getDisplayName() : actor.getUsername();
        String roleLabel = actor.getRoleName() == RoleName.ADMIN ? "Admin " : "Manager ";
        String schedule = formatClassSchedule(trainingClass);
        String message = roleLabel + actorName
                + " đã tạo lớp \"" + trainingClass.getClassName() + "\""
                + (schedule.isEmpty() ? "" : " (" + schedule + ")")
                + ". Vui lòng kiểm tra và duyệt/từ chối.";
        notificationService.notifyUsers(
                admins,
                "Lớp đào tạo chờ duyệt",
                message,
                NotificationType.TRAINING_CLASS_CREATED,
                TRAINING_CLASS_REF,
                trainingClass.getId());
    }

    private void notifyAssignedTrainer(TrainingClass trainingClass, User trainer) {
        if (trainer == null) {
            return;
        }
        notificationService.notifyUsers(
                List.of(trainer),
                "Bạn được phân công đào tạo",
                "Admin đã phân công bạn làm người đào tạo lớp \"" + trainingClass.getClassName()
                        + "\". Lớp xuất hiện trong danh sách đào tạo của bạn.",
                NotificationType.TRAINING_CLASS_CREATED,
                TRAINING_CLASS_REF,
                trainingClass.getId());
    }

    private void notifyEnrolledEmployees(TrainingClass trainingClass, List<User> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        String className = trainingClass.getClassName() == null ? "" : trainingClass.getClassName().trim();
        notificationService.notifyUsers(
                recipients,
                "Bạn được thêm vào lớp đào tạo",
                "Bạn đã được thêm vào lớp đào tạo " + className + ".",
                NotificationType.TRAINING_CLASS_ENROLLED,
                TRAINING_CLASS_REF,
                trainingClass.getId());
    }

    private void notifyManagerReviewResult(TrainingClass trainingClass, TrainingClassStatus targetStatus) {
        List<User> recipients = new ArrayList<>();
        User creator = trainingClass.getCreatedBy();
        if (creator != null) {
            recipients.add(creator);
        }
        User trainer = trainingClass.getTrainer();
        if (trainer != null && recipients.stream().noneMatch(user -> user.getId().equals(trainer.getId()))) {
            recipients.add(trainer);
        }
        if (recipients.isEmpty()) {
            return;
        }
        boolean approved = targetStatus == TrainingClassStatus.APPROVED;
        String title = approved ? "Lớp đào tạo đã được duyệt" : "Lớp đào tạo bị từ chối";
        String message = approved
                ? "Lớp \"" + trainingClass.getClassName() + "\" đã được Admin duyệt."
                : "Lớp \"" + trainingClass.getClassName() + "\" đã bị từ chối.";
        notificationService.notifyUsers(
                recipients,
                title,
                message,
                approved ? NotificationType.TRAINING_CLASS_APPROVED : NotificationType.TRAINING_CLASS_REJECTED,
                TRAINING_CLASS_REF,
                trainingClass.getId());
    }

    private String formatClassSchedule(TrainingClass trainingClass) {
        StringBuilder builder = new StringBuilder();
        if (trainingClass.getStartDate() != null) {
            builder.append(DATE_FORMAT.format(trainingClass.getStartDate()));
            if (trainingClass.getEndDate() != null && !trainingClass.getEndDate().equals(trainingClass.getStartDate())) {
                builder.append(" → ").append(DATE_FORMAT.format(trainingClass.getEndDate()));
            }
        }
        if (trainingClass.getStartTime() != null) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(TIME_FORMAT.format(trainingClass.getStartTime()));
            if (trainingClass.getEndTime() != null) {
                builder.append("–").append(TIME_FORMAT.format(trainingClass.getEndTime()));
            }
        }
        return builder.toString();
    }

    private TrainingClass requireVisibleApprovedClass(Integer classId, AuthenticatedUser manager) {
        TrainingClass trainingClass = requireManagedApprovedClass(classId, manager);
        if (trainingClass.getEndDate() != null && trainingClass.getEndDate().isBefore(VietnamTime.today())) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        return trainingClass;
    }

    private TrainingClass requireManagedApprovedClass(Integer classId, AuthenticatedUser manager) {
        requireManager(manager);
        TrainingClass trainingClass = trainingClassRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lớp đào tạo."));
        if (!canViewClass(trainingClass, manager)) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        if (trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        return trainingClass;
    }

    private boolean canViewClass(TrainingClass trainingClass, AuthenticatedUser manager) {
        if (isTrainer(trainingClass, manager)) {
            return true;
        }
        Store store = resolveManagerStore(manager);
        return isStoreTraining(trainingClass)
                && trainingClass.getStore() != null
                && store != null
                && store.getId().equals(trainingClass.getStore().getId());
    }

    private boolean isTrainer(TrainingClass trainingClass, AuthenticatedUser manager) {
        if (trainingClass.getTrainer() != null && trainingClass.getTrainer().getId() != null) {
            return trainingClass.getTrainer().getId().equals(manager.getUserId());
        }
        Store store = resolveManagerStore(manager);
        return isStoreTraining(trainingClass)
                && trainingClass.getStore() != null
                && store != null
                && store.getId().equals(trainingClass.getStore().getId());
    }

    private boolean isStoreTraining(TrainingClass trainingClass) {
        return trainingClass.getTrainingType() != TrainingType.CENTRALIZED_TRAINING;
    }

    private boolean hasEnded(TrainingClass trainingClass) {
        if (trainingClass.getEndDate() == null) {
            return false;
        }
        LocalDate today = VietnamTime.today();
        if (trainingClass.getEndDate().isBefore(today)) {
            return true;
        }
        if (trainingClass.getEndDate().isAfter(today)) {
            return false;
        }
        LocalTime endTime = trainingClass.getEndTime();
        if (endTime == null) {
            return false;
        }
        return !VietnamTime.currentTime().isBefore(endTime);
    }

    private Store requireManagerStore(AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        if (store == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gán cửa hàng.");
        }
        return store;
    }

    private Store resolveManagerStore(AuthenticatedUser manager) {
        if (manager.getEmployeeId() == null) {
            return null;
        }
        return storeRepository.findByManager_Id(manager.getEmployeeId()).orElse(null);
    }

    private User loadUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản."));
    }

    private String requireSkillName(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new BusinessException("Vui lòng nhập tên kỹ năng.");
        }
        String trimmed = skillName.trim();
        if (trimmed.matches(".*[0-9].*")) {
            throw new BusinessException("Tên kỹ năng không được chứa số.");
        }
        return trimmed;
    }

    private void requireEmployee(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.STAFF || actor.getEmployeeId() == null) {
            throw new BusinessException("Chỉ nhân viên mới được xem lớp đào tạo của mình.");
        }
    }

    private boolean hasStarted(TrainingClass trainingClass) {
        if (trainingClass.getStartDate() == null) {
            return true;
        }
        LocalDate today = VietnamTime.today();
        if (trainingClass.getStartDate().isAfter(today)) {
            return false;
        }
        if (trainingClass.getStartDate().isBefore(today)) {
            return true;
        }
        LocalTime startTime = trainingClass.getStartTime();
        if (startTime == null) {
            return true;
        }
        return !VietnamTime.currentTime().isBefore(startTime);
    }

    private String participationStatus(TrainingClass trainingClass) {
        if (hasEnded(trainingClass)) {
            return "Đã kết thúc";
        }
        if (hasStarted(trainingClass)) {
            return "Đang tham gia";
        }
        return "Chưa bắt đầu";
    }

    private EmployeeTrainingClassResponse toEmployeeClass(TrainingClassEnrollment enrollment) {
        TrainingClass trainingClass = enrollment.getTrainingClass();
        TrainingResult result = enrollment.getResult();
        return EmployeeTrainingClassResponse.builder()
                .id(trainingClass.getId())
                .className(trainingClass.getClassName())
                .skillName(skillLabel(trainingClass))
                .storeName(storeLabel(trainingClass))
                .startDate(trainingClass.getStartDate())
                .endDate(trainingClass.getEndDate())
                .startTime(trainingClass.getStartTime())
                .endTime(trainingClass.getEndTime())
                .trainer(userDisplayName(trainingClass.getTrainer()))
                .supervisorName(userDisplayName(trainingClass.getCreatedBy()))
                .location(trainingClass.getLocation())
                .notes(trainingClass.getNotes())
                .participationStatus(participationStatus(trainingClass))
                .ended(hasEnded(trainingClass))
                .result(result)
                .resultLabel(result == null ? "Chưa đánh giá" : result.getLabel())
                .evaluationNote(enrollment.getEvaluationNote())
                .build();
    }

    private void requireManager(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Chỉ Manager mới được thực hiện thao tác này.");
        }
    }

    private void requireAdmin(AuthenticatedUser actor) {
        requireAdmin(actor, "Chỉ Admin mới được duyệt lớp đào tạo.");
    }

    private void requireAdmin(AuthenticatedUser actor, String message) {
        if (actor == null || actor.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException(message);
        }
    }

    private String userDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getEmployee() != null && user.getEmployee().getFullName() != null
                && !user.getEmployee().getFullName().isBlank()) {
            return user.getEmployee().getFullName();
        }
        return user.getUsername();
    }

    private int enrollEmployees(TrainingClass trainingClass, List<Integer> employeeIds, boolean requireAtLeastOne) {
        List<Integer> ids = employeeIds == null
                ? List.of()
                : employeeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            if (requireAtLeastOne) {
                throw new BusinessException("Vui lòng chọn ít nhất một nhân viên.");
            }
            return 0;
        }

        Integer maxParticipants = trainingClass.getMaxParticipants();
        if (maxParticipants != null && trainingClass.getId() != null) {
            long currentCount = trainingClassEnrollmentRepository.countByTrainingClass_Id(trainingClass.getId());
            if (currentCount + ids.size() > maxParticipants) {
                throw new BusinessException("Số học viên vượt quá sĩ số tối đa của lớp.");
            }
        } else if (maxParticipants != null && ids.size() > maxParticipants) {
            throw new BusinessException("Số học viên vượt quá sĩ số tối đa của lớp.");
        }

        Set<Integer> classSkillIds = skillIdsOf(trainingClass);
        Map<Integer, Set<Integer>> passedSkills = passedSkillIdsByEmployeeIds(ids);
        Map<Integer, List<EmployeeOpenSkill>> openSkills = openSkillsByEmployeeIds(ids, trainingClass.getId());
        Set<Integer> managerIds = managerEmployeeIds();
        List<User> recipients = new ArrayList<>();
        for (Integer employeeId : ids) {
            if (managerIds.contains(employeeId)) {
                throw new BusinessException(
                        "Không thể thêm tài khoản Manager vào lớp. Manager chỉ được chọn làm người đào tạo khi Admin tạo lớp.");
            }
            Employee employee = employeeRepository.findByIdWithStore(employeeId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
            if (!employeeBelongsToClass(trainingClass, employee)) {
                throw new BusinessException(trainingClass.getTrainingType() == TrainingType.CENTRALIZED_TRAINING
                        ? "Nhân viên không thuộc các cửa hàng của lớp đào tạo."
                        : "Nhân viên không thuộc cửa hàng của bạn.");
            }
            if (employee.getStatus() != EmployeeStatus.ACTIVE) {
                throw new BusinessException("Chỉ được thêm nhân viên đang làm việc tại cửa hàng.");
            }
            if (trainingClass.getId() != null
                    && trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(
                    trainingClass.getId(), employeeId)) {
                throw new BusinessException("Nhân viên " + employee.getFullName() + " đã được ghi danh vào lớp này.");
            }
            if (alreadyCertifiedForClassSkills(employeeId, classSkillIds, passedSkills)) {
                throw new BusinessException("Nhân viên " + employee.getFullName()
                        + " đã có chứng chỉ cho kỹ năng của lớp này.");
            }
            EmployeeOpenSkill conflict = conflictingOpenSkill(employeeId, classSkillIds, openSkills);
            if (conflict != null) {
                throw new BusinessException("Nhân viên " + employee.getFullName()
                        + " đang học kỹ năng " + skillLabel(conflict)
                        + " ở lớp \"" + conflict.getClassName()
                        + "\" còn hiệu lực, nên không thể tham gia lớp khác cùng kỹ năng.");
            }
            trainingClassEnrollmentRepository.save(TrainingClassEnrollment.builder()
                    .trainingClass(trainingClass)
                    .employee(employee)
                    .build());
            Optional<User> account = userRepository.findByEmployee_Id(employeeId);
            if (account != null && account.isPresent() && account.get().getId() != null) {
                recipients.add(account.get());
            }
        }
        notifyEnrolledEmployees(trainingClass, recipients);
        return ids.size();
    }

    private List<Employee> employeesAllowedForClass(TrainingClass trainingClass) {
        if (trainingClass.getTrainingType() == TrainingType.CENTRALIZED_TRAINING) {
            List<Integer> storeIds = trainingClass.getParticipatingStores() == null
                    ? List.of()
                    : trainingClass.getParticipatingStores().stream().map(Store::getId).toList();
            if (storeIds.isEmpty()) {
                return List.of();
            }
            return excludeManagerAccounts(
                    employeeRepository.findByStoreIdsAndStatus(storeIds, EmployeeStatus.ACTIVE));
        }
        if (trainingClass.getStore() == null) {
            return List.of();
        }
        return excludeManagerAccounts(employeeRepository.findByStore_IdAndStatus(
                trainingClass.getStore().getId(), EmployeeStatus.ACTIVE));
    }

    private List<Employee> excludeManagerAccounts(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) {
            return List.of();
        }
        Set<Integer> managerIds = managerEmployeeIds();
        if (managerIds.isEmpty()) {
            return employees;
        }
        return employees.stream()
                .filter(employee -> employee.getId() == null || !managerIds.contains(employee.getId()))
                .toList();
    }

    private Set<Integer> managerEmployeeIds() {
        List<Integer> ids = userRepository.findEmployeeIdsByRoleName(RoleName.MANAGER);
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(ids);
    }

    private boolean employeeBelongsToClass(TrainingClass trainingClass, Employee employee) {
        if (employee == null || employee.getStore() == null || employee.getStore().getId() == null) {
            return false;
        }
        Integer storeId = employee.getStore().getId();
        if (trainingClass.getTrainingType() == TrainingType.CENTRALIZED_TRAINING) {
            return trainingClass.getParticipatingStores() != null
                    && trainingClass.getParticipatingStores().stream().anyMatch(store -> storeId.equals(store.getId()));
        }
        return trainingClass.getStore() != null && storeId.equals(trainingClass.getStore().getId());
    }

    private List<TrainingSkill> requireActiveSkills(List<Integer> skillIds) {
        List<Integer> ids = skillIds == null
                ? List.of()
                : skillIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một kỹ năng đào tạo.");
        }
        List<TrainingSkill> skills = new ArrayList<>();
        for (Integer skillId : ids) {
            TrainingSkill skill = trainingSkillRepository.findById(skillId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy kỹ năng đào tạo."));
            if (skill.getStatus() != TrainingSkillStatus.ACTIVE) {
                throw new BusinessException("Không thể chọn kỹ năng đã ngừng sử dụng.");
            }
            skills.add(skill);
        }
        return skills;
    }

    private List<Store> requireStores(List<Integer> storeIds) {
        List<Integer> ids = storeIds == null
                ? List.of()
                : storeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một cửa hàng.");
        }
        List<Store> stores = new ArrayList<>();
        for (Integer storeId : ids) {
            stores.add(storeRepository.findById(storeId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy cửa hàng.")));
        }
        return stores;
    }

    private User requireManagerTrainer(Integer trainerId) {
        if (trainerId == null) {
            throw new BusinessException("Vui lòng chọn người đào tạo.");
        }
        User trainer = userRepository.findByIdWithRole(trainerId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người đào tạo."));
        if (!Boolean.TRUE.equals(trainer.getIsActive())
                || trainer.getRole() == null
                || trainer.getRole().getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Người đào tạo phải là Manager đang hoạt động.");
        }
        return trainer;
    }

    private String requireUniqueOpenClassName(String className) {
        if (className == null || className.isBlank()) {
            throw new BusinessException("Vui lòng nhập tên lớp.");
        }
        String trimmed = className.trim();
        if (trainingClassRepository.existsByClassNameIgnoreCaseAndStatusNotAndEndDateGreaterThanEqual(
                trimmed, TrainingClassStatus.REJECTED, VietnamTime.today())) {
            throw new BusinessException("Tên lớp đã tồn tại.");
        }
        return trimmed;
    }

    private String skillLabel(TrainingClass trainingClass) {
        if (trainingClass.getSkills() == null || trainingClass.getSkills().isEmpty()) {
            return null;
        }
        return trainingClass.getSkills().stream()
                .map(TrainingSkill::getSkillName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    private String storeLabel(TrainingClass trainingClass) {
        if (trainingClass.getTrainingType() == TrainingType.CENTRALIZED_TRAINING) {
            if (trainingClass.getParticipatingStores() == null || trainingClass.getParticipatingStores().isEmpty()) {
                return null;
            }
            return trainingClass.getParticipatingStores().stream()
                    .map(Store::getStoreName)
                    .filter(name -> name != null && !name.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));
        }
        return trainingClass.getStore() != null ? trainingClass.getStore().getStoreName() : null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TrainingSkillResponse toSkillResponse(TrainingSkill skill) {
        String createdByName = null;
        if (skill.getCreatedBy() != null) {
            createdByName = skill.getCreatedBy().getUsername();
        }
        return TrainingSkillResponse.builder()
                .id(skill.getId())
                .skillName(skill.getSkillName())
                .description(skill.getDescription())
                .requirements(skill.getRequirements())
                .status(skill.getStatus())
                .createdByName(createdByName)
                .createdAt(skill.getCreatedAt())
                .build();
    }

    private TrainingClassResponse toClassResponse(TrainingClass trainingClass) {
        return toClassResponse(trainingClass, List.of());
    }

    private TrainingClassResponse toClassResponse(TrainingClass trainingClass, List<TrainingClassEnrollment> enrollments) {
        TrainingSkill firstSkill = trainingClass.getSkills() == null || trainingClass.getSkills().isEmpty()
                ? null
                : trainingClass.getSkills().iterator().next();
        return TrainingClassResponse.builder()
                .id(trainingClass.getId())
                .trainingType(trainingClass.getTrainingType())
                .trainingTypeLabel(trainingClass.getTrainingType() == null
                        ? null
                        : trainingClass.getTrainingType().getLabel())
                .skillId(firstSkill != null ? firstSkill.getId() : null)
                .skillName(skillLabel(trainingClass))
                .storeId(trainingClass.getStore() != null ? trainingClass.getStore().getId() : null)
                .storeName(storeLabel(trainingClass))
                .className(trainingClass.getClassName())
                .trainerUserId(trainingClass.getTrainer() != null ? trainingClass.getTrainer().getId() : null)
                .trainer(userDisplayName(trainingClass.getTrainer()))
                .startDate(trainingClass.getStartDate())
                .endDate(trainingClass.getEndDate())
                .startTime(trainingClass.getStartTime())
                .endTime(trainingClass.getEndTime())
                .location(trainingClass.getLocation())
                .maxParticipants(trainingClass.getMaxParticipants())
                .notes(trainingClass.getNotes())
                .status(trainingClass.getStatus())
                .ended(hasEnded(trainingClass))
                .createdByName(userDisplayName(trainingClass.getCreatedBy()))
                .students(toStudentResponses(enrollments))
                .approvedByName(userDisplayName(trainingClass.getApprovedBy()))
                .approvedAt(trainingClass.getApprovedAt())
                .createdAt(trainingClass.getCreatedAt())
                .build();
    }

    private List<TrainingClassStudentResponse> toStudentResponses(List<TrainingClassEnrollment> enrollments) {
        return enrollments.stream()
                .map(enrollment -> {
                    Employee employee = enrollment.getEmployee();
                    TrainingResult result = enrollment.getResult();
                    CertificationStatus certificationStatus = employee.getCertificationStatus() == null
                            ? CertificationStatus.NOTCERTIFIED
                            : employee.getCertificationStatus();
                    return TrainingClassStudentResponse.builder()
                            .employeeId(employee.getId())
                            .fullName(employee.getFullName())
                            .email(employee.getEmail())
                            .phone(employee.getPhone())
                            .result(result)
                            .resultLabel(result == null ? "Chưa đánh giá" : result.getLabel())
                            .certificationStatus(certificationStatus)
                            .certificationLabel(certificationStatus.getLabel())
                            .evaluationNote(enrollment.getEvaluationNote())
                            .build();
                })
                .toList();
    }

    private TrainingClassStudentResponse toAvailableEmployee(Employee employee,
                                                             Map<Integer, Set<Integer>> passedSkills,
                                                             Map<Integer, List<EmployeeOpenSkill>> openSkills,
                                                             Set<Integer> classSkillIds) {
        List<Integer> certifiedSkillIds = certifiedSkillIdsFor(employee.getId(), passedSkills);
        List<Integer> studyingSkillIds = studyingSkillIdsFor(employee.getId(), openSkills);
        boolean skillsSelected = classSkillIds != null && !classSkillIds.isEmpty();
        return TrainingClassStudentResponse.builder()
                .employeeId(employee.getId())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .resultLabel("Chưa đánh giá")
                .certifiedSkillIds(certifiedSkillIds)
                .hasCertificate(skillsSelected
                        ? alreadyCertifiedForClassSkills(employee.getId(), classSkillIds, passedSkills)
                        : null)
                .studyingSkillIds(studyingSkillIds)
                .studyingSameSkill(skillsSelected
                        ? conflictingOpenSkill(employee.getId(), classSkillIds, openSkills) != null
                        : null)
                .build();
    }

    /**
     * Chứng chỉ theo kỹ năng = đã Đạt một lớp đã duyệt có kỹ năng đó.
     * Không dùng Employees.CertificationStatus vì cờ đó là trạng thái chung, không gắn với từng kỹ năng.
     */
    private Map<Integer, Set<Integer>> passedSkillIdsByEmployee(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) {
            return Map.of();
        }
        return passedSkillIdsByEmployeeIds(employees.stream().map(Employee::getId).toList());
    }

    private Map<Integer, Set<Integer>> passedSkillIdsByEmployeeIds(Collection<Integer> employeeIds) {
        List<Integer> ids = employeeIds == null
                ? List.of()
                : employeeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<EmployeePassedSkill> rows = trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                ids, TrainingResult.PASS, TrainingClassStatus.APPROVED);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Set<Integer>> passed = new HashMap<>();
        for (EmployeePassedSkill row : rows) {
            if (row == null || row.getEmployeeId() == null || row.getSkillId() == null) {
                continue;
            }
            passed.computeIfAbsent(row.getEmployeeId(), ignored -> new HashSet<>()).add(row.getSkillId());
        }
        return passed;
    }

    private List<Integer> certifiedSkillIdsFor(Integer employeeId, Map<Integer, Set<Integer>> passedSkills) {
        Set<Integer> skillIds = passedSkills.getOrDefault(employeeId, Set.of());
        if (skillIds.isEmpty()) {
            return List.of();
        }
        return skillIds.stream().filter(Objects::nonNull).sorted().toList();
    }

    private Set<Integer> skillIdsOf(TrainingClass trainingClass) {
        if (trainingClass.getSkills() == null || trainingClass.getSkills().isEmpty()) {
            return Set.of();
        }
        return trainingClass.getSkills().stream()
                .map(TrainingSkill::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean alreadyCertifiedForClassSkills(Integer employeeId,
                                                   Set<Integer> classSkillIds,
                                                   Map<Integer, Set<Integer>> passedSkills) {
        if (classSkillIds == null || classSkillIds.isEmpty()) {
            return false;
        }
        return passedSkills.getOrDefault(employeeId, Set.of()).containsAll(classSkillIds);
    }

    private Map<Integer, List<EmployeeOpenSkill>> openSkillsByEmployee(List<Employee> employees,
                                                                       Integer excludeClassId) {
        if (employees == null || employees.isEmpty()) {
            return Map.of();
        }
        return openSkillsByEmployeeIds(employees.stream().map(Employee::getId).toList(), excludeClassId);
    }

    private Map<Integer, List<EmployeeOpenSkill>> openSkillsByEmployeeIds(Collection<Integer> employeeIds,
                                                                          Integer excludeClassId) {
        List<Integer> ids = employeeIds == null
                ? List.of()
                : employeeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<EmployeeOpenSkill> rows = trainingClassEnrollmentRepository.findOpenSkillEnrollments(
                ids,
                TrainingClassStatus.REJECTED,
                excludeClassId == null ? 0 : excludeClassId,
                VietnamTime.today(),
                VietnamTime.currentTime());
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<EmployeeOpenSkill>> open = new HashMap<>();
        for (EmployeeOpenSkill row : rows) {
            if (row == null || row.getEmployeeId() == null || row.getSkillId() == null) {
                continue;
            }
            open.computeIfAbsent(row.getEmployeeId(), ignored -> new ArrayList<>()).add(row);
        }
        return open;
    }

    private List<Integer> studyingSkillIdsFor(Integer employeeId, Map<Integer, List<EmployeeOpenSkill>> openSkills) {
        List<EmployeeOpenSkill> rows = openSkills.getOrDefault(employeeId, List.of());
        if (rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(EmployeeOpenSkill::getSkillId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private EmployeeOpenSkill conflictingOpenSkill(Integer employeeId,
                                                   Set<Integer> classSkillIds,
                                                   Map<Integer, List<EmployeeOpenSkill>> openSkills) {
        if (classSkillIds == null || classSkillIds.isEmpty()) {
            return null;
        }
        return openSkills.getOrDefault(employeeId, List.of()).stream()
                .filter(row -> row.getSkillId() != null && classSkillIds.contains(row.getSkillId()))
                .min(Comparator.comparing(EmployeeOpenSkill::getSkillId)
                        .thenComparing(row -> row.getClassName() == null ? "" : row.getClassName()))
                .orElse(null);
    }

    private String skillLabel(EmployeeOpenSkill conflict) {
        if (conflict.getSkillName() == null || conflict.getSkillName().isBlank()) {
            return "này";
        }
        return conflict.getSkillName();
    }
}
