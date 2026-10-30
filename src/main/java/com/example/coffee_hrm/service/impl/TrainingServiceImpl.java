package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificateKind;
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
import com.example.coffee_hrm.dto.request.CreateManagementTrainingRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.EvaluateTrainingStudentRequest;
import com.example.coffee_hrm.dto.request.SkillFilePayload;
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.SkillDocumentDownload;
import com.example.coffee_hrm.dto.response.SkillDocumentResponse;
import com.example.coffee_hrm.dto.response.ManagementTrainingView;
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
import com.example.coffee_hrm.entity.TrainingSkillDocument;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeOpenSkill;
import com.example.coffee_hrm.repository.EmployeePassedSkill;
import com.example.coffee_hrm.repository.EmployeeSkillTrainingFact;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.NotificationRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.repository.TrainingClassRepository;
import com.example.coffee_hrm.repository.TrainingSkillDocumentRepository;
import com.example.coffee_hrm.repository.TrainingSkillRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.support.ManagementTrainingRules;
import com.example.coffee_hrm.service.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final TrainingSkillDocumentRepository trainingSkillDocumentRepository;
    private final SkillDocumentStore skillDocumentStore;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TRAINING_CLASS_REF = "TRAINING_CLASS";
    static final int CLASS_LIST_PAGE_SIZE = 6;

    @Override
    public List<TrainingSkillResponse> listSkills() {
        return listSkills(CertificateKind.STAFF);
    }

    @Override
    public List<TrainingSkillResponse> listSkills(CertificateKind certificateKind) {
        CertificateKind kind = certificateKind == null ? CertificateKind.STAFF : certificateKind;
        return trainingSkillRepository.findAllWithCreator().stream()
                .filter(skill -> kindOf(skill) == kind)
                .map(this::toSkillResponse)
                .toList();
    }

    @Override
    public List<TrainingSkillResponse> listActiveSkills() {
        return trainingSkillRepository.findByStatusWithCreator(TrainingSkillStatus.ACTIVE).stream()
                .filter(skill -> kindOf(skill) == CertificateKind.STAFF)
                .map(this::toSkillResponse)
                .toList();
    }

    @Override
    @Transactional
    public TrainingSkillResponse createSkill(CreateTrainingSkillRequest request, AuthenticatedUser actor) {
        return createSkill(request, actor, List.of());
    }

    @Override
    @Transactional
    public TrainingSkillResponse createSkill(CreateTrainingSkillRequest request, AuthenticatedUser actor,
                                             List<SkillFilePayload> files) {
        requireAdmin(actor, "Chỉ Admin mới được tạo kỹ năng đào tạo.");
        String skillName = requireSkillName(request.getSkillName());
        if (trainingSkillRepository.existsBySkillNameIgnoreCase(skillName)) {
            throw new BusinessException("Tên kỹ năng đã tồn tại.");
        }
        List<SkillDocumentStore.Stored> prepared = skillDocumentStore.prepare(files, 0);

        TrainingSkill skill = TrainingSkill.builder()
                .skillName(skillName)
                .description(blankToNull(request.getDescription()))
                .requirements(blankToNull(request.getRequirements()))
                .status(TrainingSkillStatus.ACTIVE)
                .certificateKind(request.getCertificateKind() == null
                        ? CertificateKind.STAFF
                        : request.getCertificateKind())
                .createdBy(loadUser(actor.getUserId()))
                .build();
        TrainingSkill saved = trainingSkillRepository.save(skill);
        persistDocuments(saved, prepared);
        return toSkillResponse(saved);
    }

    @Override
    @Transactional
    public TrainingSkillResponse updateSkill(Integer skillId, UpdateTrainingSkillRequest request, AuthenticatedUser actor) {
        return updateSkill(skillId, request, actor, List.of());
    }

    @Override
    @Transactional
    public TrainingSkillResponse updateSkill(Integer skillId, UpdateTrainingSkillRequest request,
                                             AuthenticatedUser actor, List<SkillFilePayload> files) {
        requireAdmin(actor, "Chỉ Admin mới được sửa kỹ năng đào tạo.");
        TrainingSkill skill = trainingSkillRepository.findById(skillId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy kỹ năng đào tạo."));
        if (skill.getStatus() == TrainingSkillStatus.INACTIVE) {
            throw new BusinessException("Không thể sửa kỹ năng đã ngừng sử dụng.");
        }

        String skillName = requireSkillName(request.getSkillName());
        if (trainingSkillRepository.existsBySkillNameIgnoreCaseAndIdNot(skillName, skillId)) {
            throw new BusinessException("Tên kỹ năng đã tồn tại.");
        }
        int alreadyStored = (int) trainingSkillDocumentRepository.countBySkill_Id(skillId);
        List<SkillDocumentStore.Stored> prepared = skillDocumentStore.prepare(files, alreadyStored);

        skill.setSkillName(skillName);
        if (request.getDescription() != null) {
            skill.setDescription(blankToNull(request.getDescription()));
        }
        skill.setRequirements(blankToNull(request.getRequirements()));
        persistDocuments(skill, prepared);
        return toSkillResponse(skill);
    }

    @Override
    @Transactional
    public TrainingSkillResponse deactivateSkill(Integer skillId, AuthenticatedUser actor) {
        requireAdmin(actor, "Chỉ Admin mới được ngừng kỹ năng đào tạo.");
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
        requireAdmin(actor, "Chỉ Admin mới được kích hoạt kỹ năng đào tạo.");
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
        Map<Integer, Map<Integer, SkillSnapshot>> snapshots = skillSnapshotsByEmployee(employees);
        return employees.stream()
                .sorted(Comparator.comparing(Employee::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(employee -> toAvailableEmployee(employee, passedSkills, openSkills, null, snapshots))
                .toList();
    }

    @Override
    public List<Store> listActiveStores(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo.");
        return storeRepository.findByIsActiveTrueOrderByStoreNameAsc();
    }

    @Override
    public List<TrainingTrainerOption> listTrainerCandidates(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo.");
        List<TrainingTrainerOption> trainers = new ArrayList<>();
        List<User> admins = userRepository.findActiveByRoleName(RoleName.ADMIN);
        if (admins != null) {
            admins.stream().map(this::toTrainerOption).flatMap(Optional::stream).forEach(trainers::add);
        }
        List<User> managers = userRepository.findActiveByRoleName(RoleName.MANAGER);
        if (managers != null) {
            managers.stream()
                    .filter(this::hasManagementCertificate)
                    .map(this::toTrainerOption)
                    .flatMap(Optional::stream)
                    .forEach(trainers::add);
        }
        trainers.sort(Comparator.comparing(TrainingTrainerOption::getFullName, String.CASE_INSENSITIVE_ORDER));
        return trainers;
    }

    @Override
    public List<TrainingEmployeeOption> listActiveEmployees(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo.");
        List<Employee> employees = excludeManagerAccounts(
                employeeRepository.findByStatusWithStore(EmployeeStatus.ACTIVE)).stream()
                .filter(employee -> employee.getStore() != null && employee.getStore().getId() != null)
                .toList();
        Map<Integer, Set<Integer>> passedSkills = passedSkillIdsByEmployee(employees);
        Map<Integer, List<EmployeeOpenSkill>> openSkills = openSkillsByEmployee(employees, null);
        Map<Integer, Map<Integer, SkillSnapshot>> snapshots = skillSnapshotsByEmployee(employees);
        return employees.stream()
                .map(employee -> toEmployeeOption(employee, passedSkills, openSkills, snapshots, RoleName.STAFF))
                .toList();
    }

    @Override
    public List<TrainingEmployeeOption> listClassCandidates(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo.");
        List<Employee> employees = employeeRepository.findByStatusWithStore(EmployeeStatus.ACTIVE);
        Set<Integer> managers = managerEmployeeIds();
        Set<Integer> admins = new HashSet<>(userRepository.findEmployeeIdsByRoleName(RoleName.ADMIN));
        Set<Integer> staff = new HashSet<>(userRepository.findEmployeeIdsByRoleName(RoleName.STAFF));
        List<Employee> candidates = employees.stream()
                .filter(employee -> admins.contains(employee.getId())
                        || (employee.getStore() != null
                        && employee.getStore().getId() != null
                        && (managers.contains(employee.getId()) || staff.contains(employee.getId()))))
                .toList();
        Map<Integer, Set<Integer>> passedSkills = passedSkillIdsByEmployee(candidates);
        Map<Integer, List<EmployeeOpenSkill>> openSkills = openSkillsByEmployee(candidates, null);
        Map<Integer, Map<Integer, SkillSnapshot>> snapshots = skillSnapshotsByEmployee(candidates);
        return candidates.stream()
                .map(employee -> toEmployeeOption(
                        employee,
                        passedSkills,
                        openSkills,
                        snapshots,
                        roleOfCandidate(employee.getId(), admins, managers)))
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
        Integer learnerEmployeeId = manager.getEmployeeId() == null ? -1 : manager.getEmployeeId();
        Page<TrainingClass> result = trainingClassRepository.searchApprovedActiveForManager(
                storeId,
                manager.getUserId(),
                learnerEmployeeId,
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
                    learnerEmployeeId,
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
    public List<TrainingClassResponse> listEndedClassesForManager(AuthenticatedUser manager) {
        requireManager(manager);
        Store store = resolveManagerStore(manager);
        Integer storeId = store == null ? -1 : store.getId();
        Integer learnerEmployeeId = manager.getEmployeeId() == null ? -1 : manager.getEmployeeId();
        List<TrainingClassResponse> classes = trainingClassRepository.findEndedApprovedForManager(
                        storeId,
                        manager.getUserId(),
                        learnerEmployeeId,
                        TrainingType.STORE_TRAINING,
                        TrainingClassStatus.APPROVED,
                        VietnamTime.today(),
                        VietnamTime.currentTime()).stream()
                .map(trainingClass -> toClassResponse(trainingClass).toBuilder()
                        .evaluable(isTrainer(trainingClass, manager))
                        .build())
                .toList();
        return markEvaluated(classes);
    }

    @Override
    public TrainingClassResponse getApprovedClassDetailForManager(Integer classId, AuthenticatedUser manager) {
        TrainingClass trainingClass = requireManagedApprovedClass(classId, manager);
        List<TrainingClassEnrollment> enrollments = trainingClassEnrollmentRepository.findByClassIdWithEmployee(classId);
        boolean trainer = isTrainer(trainingClass, manager);
        return toClassResponse(trainingClass, enrollments).toBuilder()
                .evaluable(trainer)
                .rosterVisible(trainer)
                .materials(trainer ? materialsOf(trainingClass) : List.of())
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
        Map<Integer, Map<Integer, SkillSnapshot>> snapshots = skillSnapshotsByEmployee(employees);
        Set<Integer> classSkillIds = skillIdsOf(trainingClass);
        return employees.stream()
                .sorted(Comparator.comparing(Employee::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(employee -> toAvailableEmployee(employee, passedSkills, openSkills, trainingClass, snapshots))
                .toList();
    }

    @Override
    @Transactional
    public int addStudentsToClass(Integer classId, AddTrainingClassStudentsRequest request, AuthenticatedUser manager) {
        TrainingClass trainingClass = requireManagedApprovedClass(classId, manager);
        if (!isTrainer(trainingClass, manager)) {
            throw new BusinessException("Chỉ người đào tạo của lớp mới được thêm học viên.");
        }
        if (hasEnded(trainingClass)) {
            throw new BusinessException("Không thể thêm học viên vào lớp đã kết thúc.");
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

        TrainingClass trainingClass = manager != null && manager.getRoleName() == RoleName.ADMIN
                ? requireTrainableClass(classId, manager)
                : requireManagedApprovedClass(classId, manager);
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
        if (trainingClass.getTrainingType() != TrainingType.MANAGEMENT_TRAINING
                && !employeeBelongsToClass(trainingClass, employee)) {
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
        User recorder = loadUser(manager.getUserId());
        enrollment.setEvaluatedBy(recorder);
        if (trainingClass.getTrainingType() == TrainingType.MANAGEMENT_TRAINING) {
            recordManagementResult(enrollment, employee, request.getResult(), recorder);
        } else {
            syncEmployeeCertificationRollup(employee, request.getResult());
        }
        notifyEvaluatedEmployee(trainingClass, employee, request.getResult(), note, alreadyEvaluated);
    }

    /**
     * Chỉ có Chứng chỉ nhân viên. Đạt thì cấp và giữ ngày cấp lần đầu.
     * Không đạt thì chưa có chứng chỉ. Không suy ra từ chứng chỉ kỹ năng cũ.
     */
    private void syncEmployeeCertificationRollup(Employee employee, TrainingResult result) {
        if (employee == null) {
            return;
        }
        if (result == TrainingResult.PASS) {
            employee.setCertificationStatus(CertificationStatus.CERTIFIED);
            employee.setHasCertificate(true);
            if (employee.getCertificateIssuedOn() == null) {
                employee.setCertificateIssuedOn(VietnamTime.today());
            }
            return;
        }
        employee.setCertificationStatus(CertificationStatus.NOTCERTIFIED);
        employee.setHasCertificate(false);
        employee.setCertificateIssuedOn(null);
    }

    @Override
    public List<EmployeeTrainingClassResponse> listClassesForEmployee(AuthenticatedUser employee) {
        requireEmployee(employee);
        return trainingClassEnrollmentRepository
                .findByEmployeeAndStatusWithClass(employee.getEmployeeId(), TrainingClassStatus.APPROVED)
                .stream()
                .filter(enrollment -> enrollment.getTrainingClass() == null
                        || enrollment.getTrainingClass().getTrainingType() != TrainingType.MANAGEMENT_TRAINING)
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
        if (trainingClass.getTrainingType() == TrainingType.MANAGEMENT_TRAINING) {
            throw new BusinessException(ManagementTrainingRules.MANAGER_ONLY);
        }
        return toEmployeeClass(enrollment);
    }

    @Override
    public List<EmployeeTrainingClassResponse> listOwnClasses(AuthenticatedUser actor) {
        requireLeadershipLearner(actor);
        return trainingClassEnrollmentRepository
                .findByEmployeeAndStatusWithClass(actor.getEmployeeId(), TrainingClassStatus.APPROVED)
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
    public EmployeeTrainingClassResponse getOwnClass(Integer classId, AuthenticatedUser actor) {
        requireLeadershipLearner(actor);
        TrainingClassEnrollment enrollment = trainingClassEnrollmentRepository
                .findOwnedByClassAndEmployee(classId, actor.getEmployeeId())
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
                .filter(trainingClass -> trainingClass.getTrainingType() != TrainingType.MANAGEMENT_TRAINING)
                .filter(trainingClass -> !hasEnded(trainingClass))
                .count();
    }

    @Override
    public List<EmployeeSkillStatusLine> listCurrentSkillStatuses(Integer employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return List.of();
        }
        boolean certified = employee.getCertificationStatus() == CertificationStatus.CERTIFIED;
        return List.of(EmployeeSkillStatusLine.builder()
                .skillName("Chứng chỉ nhân viên")
                .certificationStatus(certified ? CertificationStatus.CERTIFIED : CertificationStatus.NOTCERTIFIED)
                .certificationLabel(certified ? "Đã có chứng chỉ" : "Chưa có chứng chỉ")
                .certifiedDateLabel(employee.getCertificateIssuedOn() == null
                        ? null
                        : employee.getCertificateIssuedOn().format(DATE_FORMAT))
                .build());
    }

    @Override
    @Transactional
    public void deleteClassForManager(Integer classId, AuthenticatedUser manager) {
        throw new BusinessException("Manager không được xóa lớp đào tạo.");
    }

    @Override
    public TrainingClassResponse createClass(CreateTrainingClassRequest request, AuthenticatedUser actor) {
        throw new BusinessException("Chỉ Admin mới được tạo lớp đào tạo.");
    }

    @Override
    @Transactional
    public TrainingClassResponse createCentralizedClass(CreateCentralizedTrainingRequest request,
                                                        AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp đào tạo.");
        validateSchedule(request.getStartDate(), request.getEndDate(), request.getStartTime(), request.getEndTime());
        String className = requireUniqueOpenClassName(request.getClassName());
        boolean management = request.getCertificateKind() == CertificateKind.MANAGEMENT;
        List<TrainingSkill> skills = management ? List.of() : optionalActiveSkills(request.getSkillIds());
        List<Store> stores = requireStores(request.getStoreIds());
        boolean singleStore = stores.size() == 1;
        User trainer = requireEligibleTrainer(request.getTrainerId(), request.getCertificateKind(), stores);
        rejectTrainerWhoIsAlsoStudent(trainer, request.getEmployeeIds());
        ensureNoScheduleOverlap(stores.stream().map(Store::getId).toList(),
                request.getStartDate(), request.getEndDate(), request.getStartTime(), request.getEndTime(), null);

        User creator = loadUser(admin.getUserId());
        TrainingClass trainingClass = TrainingClass.builder()
                .trainingType(management
                        ? TrainingType.MANAGEMENT_TRAINING
                        : (singleStore ? TrainingType.STORE_TRAINING : TrainingType.CENTRALIZED_TRAINING))
                .skills(new LinkedHashSet<>(skills))
                .store(singleStore ? stores.get(0) : null)
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
                .build();

        TrainingClass saved = trainingClassRepository.save(trainingClass);
        int enrolled = management
                ? enrollManagersForCertificate(saved, request.getEmployeeIds(), stores)
                : enrollEmployees(saved, request.getEmployeeIds(), true);
        if (enrolled < 1) {
            throw new BusinessException("Vui lòng chọn ít nhất một nhân viên.");
        }
        TrainingClass detailed = trainingClassRepository.findByIdWithDetails(saved.getId()).orElse(saved);
        notifyAssignedTrainer(detailed, trainer);
        return toClassResponse(detailed);
    }

    @Override
    @Transactional
    public ManagementTrainingView createManagementClass(CreateManagementTrainingRequest request,
                                                        AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được tạo lớp chứng chỉ quản lý.");
        validateSchedule(request.getStartDate(), request.getEndDate(), request.getStartTime(), request.getEndTime());
        String className = requireUniqueOpenClassName(request.getClassName());
        User creator = loadUser(admin.getUserId());
        TrainingClass trainingClass = TrainingClass.builder()
                .trainingType(TrainingType.MANAGEMENT_TRAINING)
                .skills(new LinkedHashSet<>())
                .participatingStores(new LinkedHashSet<>())
                .className(className)
                .trainer(creator)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .maxParticipants(request.getMaxParticipants())
                .notes(blankToNull(request.getNotes()))
                .status(TrainingClassStatus.APPROVED)
                .createdBy(creator)
                .build();
        TrainingClass saved = trainingClassRepository.save(trainingClass);
        List<Integer> ids = request.getEmployeeIds() == null ? List.of() : request.getEmployeeIds();
        for (Integer employeeId : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            enrollManager(saved, employeeId);
        }
        return toManagementView(saved, null);
    }

    @Override
    public List<ManagementTrainingView> listManagementClasses(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được xem lớp chứng chỉ quản lý.");
        return trainingClassRepository
                .findByTrainingTypeAndStatusOrderByStartDateDescIdDesc(
                        TrainingType.MANAGEMENT_TRAINING, TrainingClassStatus.APPROVED)
                .stream()
                .map(trainingClass -> toManagementView(trainingClass, null))
                .toList();
    }

    @Override
    public List<ManagementTrainingView.Candidate> listManagementCandidates(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được xem danh sách Manager.");
        return userRepository.findActiveByRoleName(RoleName.MANAGER).stream()
                .filter(user -> user.getEmployee() != null)
                .map(user -> {
                    Employee employee = user.getEmployee();
                    Store store = employee.getStore();
                    return ManagementTrainingView.Candidate.builder()
                            .employeeId(employee.getId())
                            .fullName(employee.getFullName())
                            .storeName(store == null ? null : store.getStoreName())
                            .managementCertified(Boolean.TRUE.equals(employee.getManagementCertified()))
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public void enrollManagerInManagementClass(Integer classId, Integer employeeId, AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được ghi danh Manager vào lớp chứng chỉ quản lý.");
        TrainingClass trainingClass = requireManagementClass(classId);
        if (hasEnded(trainingClass)) {
            throw new BusinessException("Lớp đã kết thúc nên không thể ghi danh thêm.");
        }
        enrollManager(trainingClass, employeeId);
    }

    @Override
    @Transactional
    public void enrollSelfInManagementClass(Integer classId, AuthenticatedUser manager) {
        if (manager == null || manager.getRoleName() != RoleName.MANAGER || manager.getEmployeeId() == null) {
            throw new BusinessException(ManagementTrainingRules.MANAGER_ONLY);
        }
        TrainingClass trainingClass = requireManagementClass(classId);
        if (hasEnded(trainingClass)) {
            throw new BusinessException("Lớp đã kết thúc nên không thể đăng ký.");
        }
        enrollManager(trainingClass, manager.getEmployeeId());
    }

    @Override
    public List<ManagementTrainingView> listManagementClassesForManager(AuthenticatedUser manager) {
        if (manager == null || manager.getRoleName() != RoleName.MANAGER || manager.getEmployeeId() == null) {
            throw new BusinessException(ManagementTrainingRules.MANAGER_ONLY);
        }
        return trainingClassRepository
                .findByTrainingTypeAndStatusOrderByStartDateDescIdDesc(
                        TrainingType.MANAGEMENT_TRAINING, TrainingClassStatus.APPROVED)
                .stream()
                .map(trainingClass -> toManagementView(trainingClass, manager.getEmployeeId()))
                .toList();
    }

    @Override
    @Transactional
    public void evaluateManagementStudent(Integer classId,
                                          Integer employeeId,
                                          EvaluateTrainingStudentRequest request,
                                          AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin được ghi nhận kết quả chứng chỉ quản lý.");
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
        evaluateStudent(classId, employeeId, request, admin);
    }

    @Override
    public Page<TrainingClassResponse> listActiveClassesForAdmin(AuthenticatedUser admin,
                                                                 Integer skillId,
                                                                 LocalDate date,
                                                                 String keyword,
                                                                 String sortDir,
                                                                 int page) {
        requireAdmin(admin, "Chỉ Admin mới được xem danh sách lớp đang hoạt động.");
        Sort sort = classListSort(date, sortDir);
        int pageIndex = Math.max(page, 1) - 1;
        Pageable pageable = PageRequest.of(pageIndex, CLASS_LIST_PAGE_SIZE, sort);
        String normalizedKeyword = blankToNull(keyword);
        LocalTime currentTime = VietnamTime.currentTime();
        Page<TrainingClass> result = trainingClassRepository.searchApprovedActiveForAdmin(
                TrainingClassStatus.APPROVED,
                VietnamTime.today(),
                currentTime,
                skillId,
                date,
                normalizedKeyword,
                pageable);
        if (result.getTotalPages() > 0 && pageIndex >= result.getTotalPages()) {
            pageable = PageRequest.of(result.getTotalPages() - 1, CLASS_LIST_PAGE_SIZE, sort);
            result = trainingClassRepository.searchApprovedActiveForAdmin(
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
    public TrainingClassResponse getApprovedClassDetailForAdmin(Integer classId, AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được xem lớp đào tạo.");
        TrainingClass trainingClass = trainingClassRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lớp đào tạo."));
        if (trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        List<TrainingClassEnrollment> enrollments = trainingClassEnrollmentRepository.findByClassIdWithEmployee(classId);
        boolean trainer = isTrainer(trainingClass, admin);
        boolean creator = trainingClass.getCreatedBy() != null
                && admin.getUserId() != null
                && admin.getUserId().equals(trainingClass.getCreatedBy().getId());
        return toClassResponse(trainingClass, enrollments).toBuilder()
                .evaluable(trainer)
                .rosterVisible(trainer || creator)
                .materials(trainer ? materialsOf(trainingClass) : List.of())
                .build();
    }

    @Override
    public List<TrainingClassResponse> listEndedClassesForAdmin(AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được xem danh sách lớp đã kết thúc.");
        List<TrainingClassResponse> classes = trainingClassRepository.findAllWithDetails().stream()
                .filter(trainingClass -> trainingClass.getStatus() == TrainingClassStatus.APPROVED)
                .filter(this::hasEnded)
                .sorted(Comparator
                        .comparing(TrainingClass::getEndDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TrainingClass::getEndTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(trainingClass -> trainingClass.getClassName() == null
                                        ? ""
                                        : trainingClass.getClassName(),
                                String.CASE_INSENSITIVE_ORDER))
                .map(trainingClass -> toClassResponse(trainingClass).toBuilder()
                        .evaluable(isTrainer(trainingClass, admin))
                        .build())
                .toList();
        return markEvaluated(classes);
    }

    @Override
    public List<TrainingClassResponse> listAllClassesForAdmin() {
        List<TrainingClassResponse> classes = trainingClassRepository.findAllWithDetails().stream()
                .map(this::toClassResponse)
                .toList();
        return markEvaluated(classes);
    }

    @Override
    @Transactional
    public void notifyEndedClasses() {
        List<TrainingClass> classes = trainingClassRepository.findApprovedEndedSince(
                TrainingClassStatus.APPROVED,
                VietnamTime.today().minusDays(1),
                VietnamTime.today(),
                VietnamTime.currentTime());
        if (classes == null || classes.isEmpty()) {
            return;
        }
        for (TrainingClass trainingClass : classes) {
            if (trainingClass.getId() == null
                    || notificationRepository.existsByTypeAndReferenceTypeAndReferenceId(
                    NotificationType.TRAINING_CLASS_ENDED, TRAINING_CLASS_REF, trainingClass.getId())) {
                continue;
            }
            notifyClassEnded(trainingClass);
        }
    }

    private void notifyClassEnded(TrainingClass trainingClass) {
        List<Integer> employeeIds = trainingClassEnrollmentRepository.findEmployeeIdsByClassId(trainingClass.getId());
        List<User> recipients = new ArrayList<>();
        if (employeeIds != null && !employeeIds.isEmpty()) {
            List<User> learners = userRepository.findByEmployeeIds(employeeIds);
            if (learners != null) {
                recipients.addAll(learners);
            }
        }
        User trainer = trainingClass.getTrainer();
        if (trainer != null && trainer.getId() != null
                && recipients.stream().noneMatch(user -> trainer.getId().equals(user.getId()))) {
            recipients.add(trainer);
        }
        if (recipients.isEmpty()) {
            return;
        }
        String className = trainingClass.getClassName() == null ? "" : trainingClass.getClassName().trim();
        notificationService.notifyUsers(
                recipients,
                "Lớp đào tạo đã kết thúc",
                "Lớp đào tạo \"" + className + "\" đã kết thúc.",
                NotificationType.TRAINING_CLASS_ENDED,
                TRAINING_CLASS_REF,
                trainingClass.getId());
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

    private void notifyEvaluatedEmployee(TrainingClass trainingClass,
                                         Employee employee,
                                         TrainingResult result,
                                         String note,
                                         boolean updated) {
        if (employee == null || employee.getId() == null) {
            return;
        }
        Optional<User> account = userRepository.findByEmployee_Id(employee.getId());
        if (account == null || account.isEmpty() || account.get().getId() == null) {
            return;
        }
        String className = trainingClass.getClassName() == null ? "" : trainingClass.getClassName().trim();
        String resultLabel = result.getLabel();
        String title = updated
                ? "Kết quả đánh giá đã được cập nhật"
                : "Bạn đã được đánh giá lớp đào tạo";
        StringBuilder message = new StringBuilder(updated
                ? "Kết quả đánh giá lớp \"" + className + "\" đã được cập nhật thành " + resultLabel + "."
                : "Bạn đã được đánh giá lớp \"" + className + "\". Kết quả: " + resultLabel + ".");
        if (note != null && !note.isBlank()) {
            message.append(" Ghi chú: ").append(note.trim()).append(".");
        }
        String certificateName = trainingClass.getTrainingType() == TrainingType.MANAGEMENT_TRAINING
                ? "Chứng chỉ quản lý"
                : "Chứng chỉ nhân viên";
        if (result == TrainingResult.PASS) {
            message.append(" Đã cấp ").append(certificateName).append(".");
        } else {
            message.append(" Chưa cấp ").append(certificateName).append(".");
        }
        notificationService.notifyUsers(
                List.of(account.get()),
                title,
                message.toString(),
                NotificationType.TRAINING_CLASS_EVALUATED,
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
                "Bạn đã được thêm vào lớp đào tạo " + className
                        + ". Kết quả đánh giá sẽ có sau khi lớp kết thúc.",
                NotificationType.TRAINING_CLASS_ENROLLED,
                TRAINING_CLASS_REF,
                trainingClass.getId());
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
        if (isTrainer(trainingClass, manager) || isEnrolledLearner(trainingClass, manager)) {
            return true;
        }
        Store store = resolveManagerStore(manager);
        return isStoreTraining(trainingClass)
                && trainingClass.getStore() != null
                && store != null
                && store.getId().equals(trainingClass.getStore().getId());
    }

    private boolean isEnrolledLearner(TrainingClass trainingClass, AuthenticatedUser actor) {
        return actor != null
                && actor.getEmployeeId() != null
                && trainingClass.getId() != null
                && trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(
                        trainingClass.getId(), actor.getEmployeeId());
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
        return trainingClass.getTrainingType() == TrainingType.STORE_TRAINING;
    }

    private String scheduleLabel(TrainingClass trainingClass) {
        if (hasEnded(trainingClass)) {
            return "Đã kết thúc";
        }
        if (hasStarted(trainingClass)) {
            return "Đang diễn ra";
        }
        return "Sắp tới";
    }

    private String scheduleTone(TrainingClass trainingClass) {
        if (hasEnded(trainingClass)) {
            return "badge-ended";
        }
        if (hasStarted(trainingClass)) {
            return "badge-live";
        }
        return "badge-upcoming";
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

    private RoleName roleOfCandidate(Integer employeeId, Set<Integer> admins, Set<Integer> managers) {
        if (admins.contains(employeeId)) {
            return RoleName.ADMIN;
        }
        if (managers.contains(employeeId)) {
            return RoleName.MANAGER;
        }
        return RoleName.STAFF;
    }

    private TrainingClass requireTrainableClass(Integer classId, AuthenticatedUser trainer) {
        TrainingClass trainingClass = trainingClassRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lớp đào tạo."));
        if (trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        if (!isTrainer(trainingClass, trainer)) {
            throw new BusinessException("Chỉ người đào tạo của lớp mới được đánh giá học viên.");
        }
        return trainingClass;
    }

    private void recordManagementResult(TrainingClassEnrollment enrollment,
                                        Employee employee,
                                        TrainingResult result,
                                        User recorder) {
        User account = employee == null ? null : userRepository.findByEmployee_Id(employee.getId()).orElse(null);
        RoleName currentRole = account == null || account.getRole() == null
                ? null
                : account.getRole().getRoleName();
        if (currentRole != RoleName.MANAGER && currentRole != RoleName.ADMIN) {
            throw new BusinessException(ManagementTrainingRules.MANAGER_ONLY);
        }
        boolean anotherPass = trainingClassEnrollmentRepository.countOtherResults(
                employee.getId(),
                TrainingType.MANAGEMENT_TRAINING,
                TrainingResult.PASS,
                enrollment.getId()) > 0;
        ManagementTrainingRules.applyEvaluation(employee, currentRole, result, recorder, anotherPass);
    }

    private void requireLeadershipLearner(AuthenticatedUser actor) {
        if (actor == null
                || actor.getEmployeeId() == null
                || (actor.getRoleName() != RoleName.ADMIN && actor.getRoleName() != RoleName.MANAGER)) {
            throw new BusinessException("Chỉ Admin hoặc Manager mới được xem lớp đào tạo của mình.");
        }
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

    private String scheduleStatus(TrainingClass trainingClass) {
        if (hasEnded(trainingClass)) {
            return "Đã kết thúc";
        }
        if (hasStarted(trainingClass)) {
            return "Đang diễn ra";
        }
        return "Sắp diễn ra";
    }

    private EmployeeTrainingClassResponse toEmployeeClass(TrainingClassEnrollment enrollment) {
        TrainingClass trainingClass = enrollment.getTrainingClass();
        TrainingResult result = enrollment.getResult();
        boolean ended = hasEnded(trainingClass);
        List<String> skills = skillNames(trainingClass);
        Employee employee = enrollment.getEmployee();
        return EmployeeTrainingClassResponse.builder()
                .id(trainingClass.getId())
                .className(trainingClass.getClassName())
                .trainingTypeLabel(trainingClass.getTrainingType() == null
                        ? null
                        : trainingClass.getTrainingType().getLabel())
                .skillName(certificateSkillLabel(trainingClass))
                .storeName(storeLabel(trainingClass))
                .startDate(trainingClass.getStartDate())
                .endDate(trainingClass.getEndDate())
                .startTime(trainingClass.getStartTime())
                .endTime(trainingClass.getEndTime())
                .trainer(userDisplayName(trainingClass.getTrainer()))
                .supervisorName(userDisplayName(trainingClass.getCreatedBy()))
                .createdByName(userDisplayName(trainingClass.getCreatedBy()))
                .location(trainerPlace(trainingClass))
                .notes(trainingClass.getNotes())
                .dateLabel(trainingDateLabel(trainingClass))
                .placeLabel(placeLabel(trainingClass))
                .employeeName(employee == null ? null : employee.getFullName())
                .enrollmentStatus("Đã ghi danh")
                .completionStatus(ended ? "Đã hoàn thành" : "Chưa hoàn thành")
                .participationStatus(scheduleStatus(trainingClass))
                .ended(ended)
                .result(result)
                .resultLabel(result == null ? "Chưa đánh giá" : result.getLabel())
                .resultCode(resultCode(result))
                .passedSkillNames(result == TrainingResult.PASS ? skills : List.of())
                .notPassedSkillNames(result == TrainingResult.NOT_PASS ? skills : List.of())
                .evaluationNote(enrollment.getEvaluationNote())
                .evaluatedAt(enrollment.getEvaluatedAt())
                .build();
    }

    private String resultCode(TrainingResult result) {
        if (result == null) {
            return null;
        }
        return result == TrainingResult.PASS ? "PASS" : "NOT PASS";
    }

    private String trainingDateLabel(TrainingClass trainingClass) {
        if (trainingClass.getStartDate() == null) {
            return null;
        }
        String start = DATE_FORMAT.format(trainingClass.getStartDate());
        if (trainingClass.getEndDate() == null || trainingClass.getEndDate().equals(trainingClass.getStartDate())) {
            return start;
        }
        return start + " – " + DATE_FORMAT.format(trainingClass.getEndDate());
    }

    private String placeLabel(TrainingClass trainingClass) {
        String location = blankToNull(trainingClass.getLocation());
        String store = storeLabel(trainingClass);
        if (location == null) {
            return store;
        }
        if (store == null) {
            return location;
        }
        return location + " · " + store;
    }

    private CertificateKind kindOf(TrainingSkill skill) {
        return skill == null || skill.getCertificateKind() == null
                ? CertificateKind.STAFF
                : skill.getCertificateKind();
    }

    private int enrollManagersForCertificate(TrainingClass trainingClass,
                                             List<Integer> employeeIds,
                                             List<Store> stores) {
        List<Integer> ids = employeeIds == null
                ? List.of()
                : employeeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một Admin hoặc Manager.");
        }
        Set<Integer> storeIds = stores.stream().map(Store::getId).collect(Collectors.toSet());
        Set<Integer> admins = new HashSet<>(userRepository.findEmployeeIdsByRoleName(RoleName.ADMIN));
        for (Integer employeeId : ids) {
            Employee employee = employeeRepository.findByIdWithStore(employeeId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
            boolean adminAccount = admins.contains(employeeId);
            if (!adminAccount && (employee.getStore() == null || !storeIds.contains(employee.getStore().getId()))) {
                throw new BusinessException("Chỉ được chọn Manager thuộc các cửa hàng đã chọn.");
            }
            enrollManager(trainingClass, employeeId);
        }
        return ids.size();
    }

    private TrainingClass requireManagementClass(Integer classId) {
        TrainingClass trainingClass = trainingClassRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lớp đào tạo."));
        if (trainingClass.getTrainingType() != TrainingType.MANAGEMENT_TRAINING
                || trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            throw new BusinessException("Không tìm thấy lớp chứng chỉ quản lý.");
        }
        return trainingClass;
    }

    private void enrollManager(TrainingClass trainingClass, Integer employeeId) {
        if (employeeId == null) {
            throw new BusinessException("Không tìm thấy nhân viên.");
        }
        Employee employee = employeeRepository.findByIdWithStore(employeeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        User account = userRepository.findByEmployee_Id(employeeId).orElse(null);
        ManagementTrainingRules.requireManagerAccount(account);
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException("Chỉ được ghi danh Admin hoặc Manager đang làm việc.");
        }
        Integer maxParticipants = trainingClass.getMaxParticipants();
        if (maxParticipants != null && trainingClass.getId() != null) {
            long currentCount = trainingClassEnrollmentRepository.countByTrainingClass_Id(trainingClass.getId());
            if (currentCount + 1 > maxParticipants) {
                throw new BusinessException("Số học viên vượt quá sĩ số tối đa của lớp.");
            }
        }
        if (trainingClass.getId() != null
                && trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(
                trainingClass.getId(), employeeId)) {
            throw new BusinessException("Nhân viên " + employee.getFullName() + " đã được ghi danh vào lớp này.");
        }
        trainingClassEnrollmentRepository.save(TrainingClassEnrollment.builder()
                .trainingClass(trainingClass)
                .employee(employee)
                .build());
        if (account != null && account.getId() != null) {
            notifyEnrolledEmployees(trainingClass, List.of(account));
        }
    }

    private ManagementTrainingView toManagementView(TrainingClass trainingClass, Integer viewerEmployeeId) {
        List<TrainingClassEnrollment> enrollments = trainingClass.getId() == null
                ? List.of()
                : trainingClassEnrollmentRepository.findByClassIdWithEmployee(trainingClass.getId());
        TrainingClassEnrollment own = viewerEmployeeId == null
                ? null
                : enrollments.stream()
                .filter(enrollment -> enrollment.getEmployee() != null
                        && viewerEmployeeId.equals(enrollment.getEmployee().getId()))
                .findFirst()
                .orElse(null);
        List<ManagementTrainingView.EnrollmentLine> lines = enrollments.stream()
                .map(enrollment -> {
                    Employee employee = enrollment.getEmployee();
                    Store store = employee == null ? null : employee.getStore();
                    return ManagementTrainingView.EnrollmentLine.builder()
                            .employeeId(employee == null ? null : employee.getId())
                            .fullName(employee == null ? null : employee.getFullName())
                            .storeName(store == null ? null : store.getStoreName())
                            .resultLabel(enrollment.getResult() == null ? "Chưa đánh giá" : enrollment.getResult().getLabel())
                            .evaluatedAt(enrollment.getEvaluatedAt())
                            .evaluatedBy(userDisplayName(enrollment.getEvaluatedBy()))
                            .build();
                })
                .toList();
        return ManagementTrainingView.builder()
                .id(trainingClass.getId())
                .className(trainingClass.getClassName())
                .startDate(trainingClass.getStartDate())
                .endDate(trainingClass.getEndDate())
                .startTime(trainingClass.getStartTime())
                .endTime(trainingClass.getEndTime())
                .notes(trainingClass.getNotes())
                .ended(hasEnded(trainingClass))
                .enrolled(own != null)
                .resultLabel(own == null || own.getResult() == null
                        ? null
                        : own.getResult().getLabel())
                .enrollments(lines)
                .build();
    }

    private void requireManager(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Chỉ Manager mới được thực hiện thao tác này.");
        }
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
        if (trainingClass.getTrainingType() == TrainingType.MANAGEMENT_TRAINING) {
            throw new BusinessException("Lớp chứng chỉ quản lý không nhận nhân viên thường.");
        }
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

    private List<TrainingSkill> optionalActiveSkills(List<Integer> skillIds) {
        List<Integer> ids = skillIds == null
                ? List.of()
                : skillIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
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

    private static final String MANAGEMENT_TRAINER =
            "Người đào tạo phải là Admin hoặc Manager đã có chứng chỉ quản lý.";
    private static final String MANAGEMENT_TRAINER_STORE =
            "Người đào tạo là Manager phải đang công tác tại một trong các cơ sở được chọn cho lớp đào tạo.";
    private static final String STORE_TRAINER_MISSING =
            "Cơ sở được chọn chưa có Manager sở hữu chứng chỉ quản lý hợp lệ để phụ trách lớp đào tạo.";
    private static final String STORE_TRAINER_MISMATCH =
            "Người đào tạo phải là Manager đang phụ trách cửa hàng được chọn và đã có chứng chỉ quản lý.";
    private static final String MULTI_STORE_TRAINER =
            "Người đào tạo phải là Manager có chứng chỉ quản lý hợp lệ thuộc các cửa hàng đã chọn.";
    private static final String TRAINER_ALSO_STUDENT =
            "Một nhân viên không được đồng thời là học viên và người đào tạo trong cùng một lớp.";

    private User requireEligibleTrainer(Integer trainerId, CertificateKind certificateKind, List<Store> stores) {
        if (trainerId == null) {
            throw new BusinessException("Vui lòng chọn người đào tạo.");
        }
        User trainer = userRepository.findByIdWithRole(trainerId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người đào tạo."));
        if (certificateKind == CertificateKind.MANAGEMENT) {
            return requireManagementTrainer(trainer, stores);
        }
        if (stores == null || stores.size() <= 1) {
            Store store = stores == null || stores.isEmpty() ? null : stores.get(0);
            return requireSingleStoreStaffTrainer(store, trainer);
        }
        return requireMultiStoreStaffTrainer(stores, trainer);
    }

    private void rejectTrainerWhoIsAlsoStudent(User trainer, List<Integer> employeeIds) {
        if (trainer == null || trainer.getEmployee() == null || trainer.getEmployee().getId() == null
                || employeeIds == null) {
            return;
        }
        Integer trainerEmployeeId = trainer.getEmployee().getId();
        boolean alsoStudent = employeeIds.stream()
                .filter(Objects::nonNull)
                .anyMatch(trainerEmployeeId::equals);
        if (alsoStudent) {
            throw new BusinessException(TRAINER_ALSO_STUDENT);
        }
    }

    private User requireManagementTrainer(User trainer, List<Store> stores) {
        if (!Boolean.TRUE.equals(trainer.getIsActive()) || trainer.getRole() == null) {
            throw new BusinessException(MANAGEMENT_TRAINER);
        }
        RoleName role = trainer.getRole().getRoleName();
        if (role == RoleName.ADMIN) {
            return trainer;
        }
        if (role != RoleName.MANAGER || !hasManagementCertificate(trainer)) {
            throw new BusinessException(MANAGEMENT_TRAINER);
        }
        Store workplace = managementWorkplace(trainer);
        boolean inScope = workplace != null
                && workplace.getId() != null
                && stores != null
                && stores.stream().anyMatch(store -> workplace.getId().equals(store.getId()));
        if (!inScope) {
            throw new BusinessException(MANAGEMENT_TRAINER_STORE);
        }
        return trainer;
    }

    /**
     * Cơ sở công tác hiện tại của Manager: cửa hàng đang được phân công theo Stores.ManagerId.
     * Chỉ dùng cửa hàng trên hồ sơ khi Manager chưa được gán cửa hàng.
     */
    private Store managementWorkplace(User trainer) {
        if (trainer == null || trainer.getEmployee() == null) {
            return null;
        }
        if (trainer.getEmployee().getId() != null) {
            Optional<Store> managed = storeRepository.findByManager_Id(trainer.getEmployee().getId());
            if (managed != null && managed.isPresent() && managed.get().getId() != null) {
                return managed.get();
            }
        }
        return trainer.getEmployee().getStore();
    }

    private User requireSingleStoreStaffTrainer(Store store, User trainer) {
        Employee assigned = store == null ? null : store.getManager();
        boolean chosenIsAssigned = assigned != null
                && assigned.getId() != null
                && trainer.getEmployee() != null
                && assigned.getId().equals(trainer.getEmployee().getId());
        if (chosenIsAssigned && isActiveCertifiedManager(trainer)) {
            return trainer;
        }
        if (!storeHasCertifiedManager(store, chosenIsAssigned ? trainer : null)) {
            throw new BusinessException(STORE_TRAINER_MISSING);
        }
        throw new BusinessException(STORE_TRAINER_MISMATCH);
    }

    private boolean storeHasCertifiedManager(Store store, User alreadyLoaded) {
        Employee assigned = store == null ? null : store.getManager();
        if (assigned == null || assigned.getId() == null) {
            return false;
        }
        if (alreadyLoaded != null
                && alreadyLoaded.getEmployee() != null
                && assigned.getId().equals(alreadyLoaded.getEmployee().getId())) {
            return isActiveCertifiedManager(alreadyLoaded);
        }
        return isActiveCertifiedManager(userRepository.findByEmployee_Id(assigned.getId()).orElse(null));
    }

    private User requireMultiStoreStaffTrainer(List<Store> stores, User trainer) {
        if (!isActiveCertifiedManager(trainer)) {
            throw new BusinessException(MULTI_STORE_TRAINER);
        }
        Store managed = currentManagedStore(trainer);
        boolean inScope = managed != null
                && managed.getId() != null
                && stores.stream().anyMatch(store -> managed.getId().equals(store.getId()));
        if (!inScope) {
            throw new BusinessException(MULTI_STORE_TRAINER);
        }
        return trainer;
    }

    private boolean isActiveCertifiedManager(User user) {
        return user != null
                && Boolean.TRUE.equals(user.getIsActive())
                && user.getRole() != null
                && user.getRole().getRoleName() == RoleName.MANAGER
                && hasManagementCertificate(user);
    }

    private boolean hasManagementCertificate(User user) {
        return user != null
                && user.getEmployee() != null
                && Boolean.TRUE.equals(user.getEmployee().getManagementCertified());
    }

    private Optional<TrainingTrainerOption> toTrainerOption(User user) {
        if (user == null || user.getRole() == null) {
            return Optional.empty();
        }
        Store managedStore = currentManagedStore(user);
        Store store = managedStore != null
                ? managedStore
                : (user.getEmployee() == null ? null : user.getEmployee().getStore());
        String roleLabel = user.getRole().getRoleName() == RoleName.ADMIN ? "Admin" : "Manager";
        String fullName = userDisplayName(user);
        return Optional.of(TrainingTrainerOption.builder()
                .userId(user.getId())
                .employeeId(user.getEmployee() == null ? null : user.getEmployee().getId())
                .roleName(user.getRole().getRoleName())
                .fullName(store != null && store.getStoreName() != null
                        ? fullName + " · " + roleLabel + " · " + store.getStoreName()
                        : fullName + " · " + roleLabel)
                .storeId(store == null ? null : store.getId())
                .storeName(store == null ? null : store.getStoreName())
                .build());
    }

    /**
     * Cửa hàng Manager đang được phân công quản lý theo Stores.ManagerId.
     * Không dùng cửa hàng trên hồ sơ nhân viên, vì phân công có thể đã đổi.
     */
    private Store currentManagedStore(User user) {
        if (user == null || user.getEmployee() == null || user.getEmployee().getId() == null) {
            return null;
        }
        return storeRepository.findByManager_Id(user.getEmployee().getId()).orElse(null);
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

    private List<String> skillNames(TrainingClass trainingClass) {
        if (trainingClass.getSkills() == null || trainingClass.getSkills().isEmpty()) {
            return List.of();
        }
        return trainingClass.getSkills().stream()
                .map(TrainingSkill::getSkillName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String certificateSkillLabel(TrainingClass trainingClass) {
        CertificateKind kind = trainingClass.getTrainingType() == TrainingType.MANAGEMENT_TRAINING
                ? CertificateKind.MANAGEMENT
                : CertificateKind.STAFF;
        List<TrainingSkill> skills = trainingSkillRepository.findByStatusWithCreator(TrainingSkillStatus.ACTIVE);
        if (skills == null) {
            skills = List.of();
        }
        String names = skills.stream()
                .filter(skill -> kindOf(skill) == kind)
                .map(TrainingSkill::getSkillName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        return names.isBlank() ? kind.getLabel() : names;
    }

    private String trainerPlace(TrainingClass trainingClass) {
        User trainer = trainingClass.getTrainer();
        if (trainer != null && trainer.getRole() != null && trainer.getRole().getRoleName() == RoleName.ADMIN) {
            return "Trụ Sở";
        }
        if (trainer != null && trainer.getEmployee() != null && trainer.getEmployee().getId() != null) {
            Optional<Store> managed = storeRepository.findByManager_Id(trainer.getEmployee().getId());
            if (managed != null && managed.isPresent()
                    && managed.get().getStoreName() != null
                    && !managed.get().getStoreName().isBlank()) {
                return managed.get().getStoreName();
            }
            if (trainer.getEmployee().getStore() != null
                    && trainer.getEmployee().getStore().getStoreName() != null
                    && !trainer.getEmployee().getStore().getStoreName().isBlank()) {
                return trainer.getEmployee().getStore().getStoreName();
            }
        }
        return blankToNull(trainingClass.getLocation());
    }

    private List<TrainingClassResponse> markEvaluated(List<TrainingClassResponse> classes) {
        if (classes == null || classes.isEmpty()) {
            return classes == null ? List.of() : classes;
        }
        List<Integer> ids = classes.stream().map(TrainingClassResponse::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return classes;
        }
        List<Object[]> rows = trainingClassEnrollmentRepository.findResultsByClassIds(ids);
        Map<Integer, List<TrainingResult>> byClass = new HashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row == null || row.length < 2 || !(row[0] instanceof Integer classId)) {
                    continue;
                }
                TrainingResult result = row[1] instanceof TrainingResult value ? value : null;
                byClass.computeIfAbsent(classId, key -> new ArrayList<>()).add(result);
            }
        }
        return classes.stream()
                .map(item -> item.toBuilder()
                        .evaluated(fullyEvaluated(byClass.get(item.getId())))
                        .build())
                .toList();
    }

    private boolean fullyEvaluated(List<TrainingResult> results) {
        return results != null && !results.isEmpty() && results.stream().allMatch(Objects::nonNull);
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
                .certificateKind(kindOf(skill))
                .createdByName(createdByName)
                .createdAt(skill.getCreatedAt())
                .documents(documentsOf(skill.getId()))
                .build();
    }

    @Override
    @Transactional
    public CertificateKind removeSkillDocument(Integer documentId, AuthenticatedUser actor) {
        requireAdmin(actor, "Chỉ Admin mới được xóa tài liệu kỹ năng.");
        TrainingSkillDocument document = trainingSkillDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài liệu kỹ năng."));
        CertificateKind kind = document.getSkill() == null ? CertificateKind.STAFF : kindOf(document.getSkill());
        trainingSkillDocumentRepository.delete(document);
        skillDocumentStore.delete(document.getStoredName());
        return kind;
    }

    @Override
    public Integer skillIdOfDocument(Integer documentId) {
        TrainingSkillDocument document = requireDocument(documentId);
        return document.getSkill() == null ? null : document.getSkill().getId();
    }

    @Override
    public SkillDocumentDownload downloadSkillDocument(Integer documentId, AuthenticatedUser actor) {
        requireAdmin(actor, "Chỉ Admin mới được tải tài liệu kỹ năng.");
        return toDownload(requireDocument(documentId));
    }

    @Override
    public SkillDocumentDownload downloadSkillDocumentForManager(Integer documentId, AuthenticatedUser manager) {
        requireManager(manager);
        return toDownload(requireDocument(documentId));
    }

    @Override
    public SkillDocumentDownload downloadClassMaterialForManager(Integer classId, Integer documentId,
                                                                 AuthenticatedUser manager) {
        TrainingClass trainingClass = requireManagedApprovedClass(classId, manager);
        return downloadForTrainer(trainingClass, documentId, manager);
    }

    @Override
    public SkillDocumentDownload downloadClassMaterialForAdmin(Integer classId, Integer documentId,
                                                               AuthenticatedUser admin) {
        requireAdmin(admin, "Chỉ Admin mới được xem lớp đào tạo.");
        TrainingClass trainingClass = trainingClassRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lớp đào tạo."));
        if (trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        return downloadForTrainer(trainingClass, documentId, admin);
    }

    private SkillDocumentDownload downloadForTrainer(TrainingClass trainingClass, Integer documentId,
                                                     AuthenticatedUser actor) {
        if (!isTrainer(trainingClass, actor)) {
            throw new BusinessException("Chỉ người đào tạo của lớp này mới xem được tài liệu kỹ năng.");
        }
        TrainingSkillDocument document = requireDocument(documentId);
        Integer skillId = document.getSkill() == null ? null : document.getSkill().getId();
        if (skillId == null || !skillIdsOf(trainingClass).contains(skillId)) {
            throw new BusinessException("Tài liệu không thuộc kỹ năng của lớp đào tạo này.");
        }
        return toDownload(document);
    }

    private TrainingSkillDocument requireDocument(Integer documentId) {
        return trainingSkillDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài liệu kỹ năng."));
    }

    private SkillDocumentDownload toDownload(TrainingSkillDocument document) {
        return new SkillDocumentDownload(
                skillDocumentStore.read(document.getStoredName()),
                document.getOriginalName(),
                document.getContentType());
    }

    private void persistDocuments(TrainingSkill skill, List<SkillDocumentStore.Stored> prepared) {
        if (prepared == null || prepared.isEmpty()) {
            return;
        }
        for (SkillDocumentStore.Stored stored : prepared) {
            skillDocumentStore.write(stored);
            trainingSkillDocumentRepository.save(TrainingSkillDocument.builder()
                    .skill(skill)
                    .storedName(stored.storedName())
                    .originalName(stored.originalName())
                    .contentType(stored.contentType())
                    .sizeBytes(stored.size())
                    .build());
        }
    }

    private List<SkillDocumentResponse> documentsOf(Integer skillId) {
        if (skillId == null) {
            return List.of();
        }
        return trainingSkillDocumentRepository.findBySkill_IdOrderByIdAsc(skillId).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    private List<SkillDocumentResponse> materialsOf(TrainingClass trainingClass) {
        Set<Integer> skillIds = skillIdsOf(trainingClass);
        if (skillIds.isEmpty()) {
            return List.of();
        }
        return trainingSkillDocumentRepository.findBySkill_IdInOrderByIdAsc(skillIds).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    private SkillDocumentResponse toDocumentResponse(TrainingSkillDocument document) {
        return SkillDocumentResponse.builder()
                .id(document.getId())
                .originalName(document.getOriginalName())
                .sizeBytes(document.getSizeBytes())
                .build();
    }

    private TrainingClassResponse toClassResponse(TrainingClass trainingClass) {
        return toClassResponse(trainingClass, List.of());
    }

    private TrainingClassResponse toClassResponse(TrainingClass trainingClass, List<TrainingClassEnrollment> enrollments) {
        TrainingSkill firstSkill = trainingClass.getSkills() == null || trainingClass.getSkills().isEmpty()
                ? null
                : trainingClass.getSkills().iterator().next();
        String skills = certificateSkillLabel(trainingClass);
        return TrainingClassResponse.builder()
                .id(trainingClass.getId())
                .trainingType(trainingClass.getTrainingType())
                .trainingTypeLabel(trainingClass.getTrainingType() == null
                        ? null
                        : trainingClass.getTrainingType().getLabel())
                .skillId(firstSkill != null ? firstSkill.getId() : null)
                .skillName(skills)
                .storeId(trainingClass.getStore() != null ? trainingClass.getStore().getId() : null)
                .storeName(storeLabel(trainingClass))
                .className(trainingClass.getClassName())
                .trainerUserId(trainingClass.getTrainer() != null ? trainingClass.getTrainer().getId() : null)
                .trainer(userDisplayName(trainingClass.getTrainer()))
                .startDate(trainingClass.getStartDate())
                .endDate(trainingClass.getEndDate())
                .startTime(trainingClass.getStartTime())
                .endTime(trainingClass.getEndTime())
                .location(trainerPlace(trainingClass))
                .maxParticipants(trainingClass.getMaxParticipants())
                .notes(trainingClass.getNotes())
                .status(trainingClass.getStatus())
                .scheduleLabel(scheduleLabel(trainingClass))
                .scheduleTone(scheduleTone(trainingClass))
                .ended(hasEnded(trainingClass))
                .createdByName(userDisplayName(trainingClass.getCreatedBy()))
                .students(toStudentResponses(trainingClass, enrollments))
                .approvedByName(userDisplayName(trainingClass.getApprovedBy()))
                .approvedAt(trainingClass.getApprovedAt())
                .createdAt(trainingClass.getCreatedAt())
                .build();
    }

    private List<TrainingClassStudentResponse> toStudentResponses(TrainingClass trainingClass,
                                                                  List<TrainingClassEnrollment> enrollments) {
        if (enrollments == null || enrollments.isEmpty()) {
            return List.of();
        }
        Map<Integer, Map<Integer, SkillSnapshot>> snapshots = skillSnapshotsByEmployeeIds(
                enrollments.stream().map(enrollment -> enrollment.getEmployee().getId()).toList());
        List<TrainingSkill> classSkills = sortedSkills(trainingClass);
        return enrollments.stream()
                .map(enrollment -> {
                    Employee employee = enrollment.getEmployee();
                    List<EmployeeSkillStatusLine> lines = skillLines(
                            employee.getId(),
                            classSkills,
                            snapshots.getOrDefault(employee.getId(), Map.of()),
                            Set.of(),
                            trainingClass,
                            enrollment);
                    return TrainingClassStudentResponse.builder()
                            .employeeId(employee.getId())
                            .fullName(employee.getFullName())
                            .email(employee.getEmail())
                            .phone(employee.getPhone())
                            .storeName(employee.getStore() != null ? employee.getStore().getStoreName() : null)
                            .result(enrollment.getResult())
                            .resultLabel(enrollment.getResult() == null ? "Chưa đánh giá" : enrollment.getResult().getLabel())
                            .skillStatuses(lines)
                            .certificationLabel(trainingClass.getTrainingType() == TrainingType.MANAGEMENT_TRAINING
                                    ? managementCertificateLabel(employee)
                                    : employeeCertificateLabel(employee))
                            .evaluationNote(enrollment.getEvaluationNote())
                            .evaluatedAt(enrollment.getEvaluatedAt())
                            .build();
                })
                .toList();
    }

    private TrainingEmployeeOption toEmployeeOption(Employee employee,
                                                    Map<Integer, Set<Integer>> passedSkills,
                                                    Map<Integer, List<EmployeeOpenSkill>> openSkills,
                                                    Map<Integer, Map<Integer, SkillSnapshot>> snapshots,
                                                    RoleName roleName) {
        List<Integer> certifiedSkillIds = certifiedSkillIdsFor(employee.getId(), passedSkills);
        List<Integer> studyingSkillIds = studyingSkillIdsFor(employee.getId(), openSkills);
        return TrainingEmployeeOption.builder()
                .employeeId(employee.getId())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .storeId(employee.getStore() == null ? null : employee.getStore().getId())
                .storeName(employee.getStore() == null ? null : employee.getStore().getStoreName())
                .roleName(roleName)
                .hasCertificate(Boolean.TRUE.equals(employee.getHasCertificate()))
                .managementCertified(Boolean.TRUE.equals(employee.getManagementCertified()))
                .certifiedSkillIds(certifiedSkillIds)
                .studyingSkillIds(studyingSkillIds)
                .skillStatuses(historyLines(
                        snapshots.getOrDefault(employee.getId(), Map.of()),
                        new HashSet<>(studyingSkillIds)))
                .build();
    }

    private TrainingClassStudentResponse toAvailableEmployee(Employee employee,
                                                             Map<Integer, Set<Integer>> passedSkills,
                                                             Map<Integer, List<EmployeeOpenSkill>> openSkills,
                                                             TrainingClass trainingClass,
                                                             Map<Integer, Map<Integer, SkillSnapshot>> snapshots) {
        Set<Integer> classSkillIds = trainingClass == null ? Set.of() : skillIdsOf(trainingClass);
        List<Integer> certifiedSkillIds = certifiedSkillIdsFor(employee.getId(), passedSkills);
        List<Integer> studyingSkillIds = studyingSkillIdsFor(employee.getId(), openSkills);
        boolean skillsSelected = trainingClass != null && !classSkillIds.isEmpty();
        boolean studyingSameSkill = skillsSelected
                && conflictingOpenSkill(employee.getId(), classSkillIds, openSkills) != null;
        boolean employeeCertified = employee.getCertificationStatus() == CertificationStatus.CERTIFIED;
        List<EmployeeSkillStatusLine> lines = skillsSelected
                ? skillLines(
                employee.getId(),
                sortedSkills(trainingClass),
                snapshots.getOrDefault(employee.getId(), Map.of()),
                new HashSet<>(studyingSkillIds),
                null,
                null)
                : historyLines(snapshots.getOrDefault(employee.getId(), Map.of()), new HashSet<>(studyingSkillIds));
        return TrainingClassStudentResponse.builder()
                .employeeId(employee.getId())
                .storeId(employee.getStore() != null ? employee.getStore().getId() : null)
                .storeName(employee.getStore() != null ? employee.getStore().getStoreName() : null)
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .resultLabel("Chưa đánh giá")
                .certifiedSkillIds(certifiedSkillIds)
                .hasCertificate(trainingClass == null ? null : employeeCertified)
                .studyingSkillIds(studyingSkillIds)
                .studyingSameSkill(skillsSelected ? studyingSameSkill : null)
                .skillStatuses(lines)
                .eligibilityLabel(trainingClass == null ? null : eligibilityLabel(studyingSameSkill, employeeCertified))
                .certificationLabel(employeeCertificateLabel(employee))
                .build();
    }

    private String eligibilityLabel(boolean studyingSameSkill, boolean certifiedForClass) {
        if (studyingSameSkill) {
            return "Không thể đăng ký";
        }
        if (certifiedForClass) {
            return "Có thể học lại";
        }
        return "Có thể đăng ký";
    }

    /**
     * Chứng chỉ theo kỹ năng = đã PASS ít nhất một lớp đã duyệt có đúng kỹ năng đó.
     * Ngày chứng chỉ là ngày PASS gần nhất. NOT PASS sau đó không xóa chứng chỉ và không đổi ngày.
     * Không dùng Employees.CertificationStatus vì cờ đó không gắn với từng kỹ năng.
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

    private String managementCertificateLabel(Employee employee) {
        if (employee != null && Boolean.TRUE.equals(employee.getManagementCertified())) {
            if (employee.getManagementCertifiedAt() == null) {
                return "Đã có Chứng chỉ quản lý";
            }
            return "Đã có Chứng chỉ quản lý · " + employee.getManagementCertifiedAt().format(DATE_FORMAT);
        }
        return "Chưa có Chứng chỉ quản lý";
    }

    private String employeeCertificateLabel(Employee employee) {
        if (employee != null && employee.getCertificationStatus() == CertificationStatus.CERTIFIED) {
            if (employee.getCertificateIssuedOn() == null) {
                return "Đã có Chứng chỉ nhân viên";
            }
            return "Đã có Chứng chỉ nhân viên · " + employee.getCertificateIssuedOn().format(DATE_FORMAT);
        }
        return "Chưa có Chứng chỉ nhân viên";
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

    private Map<Integer, Map<Integer, SkillSnapshot>> skillSnapshotsByEmployee(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) {
            return Map.of();
        }
        return skillSnapshotsByEmployeeIds(employees.stream().map(Employee::getId).toList());
    }

    private Map<Integer, Map<Integer, SkillSnapshot>> skillSnapshotsByEmployeeIds(Collection<Integer> employeeIds) {
        List<Integer> ids = employeeIds == null
                ? List.of()
                : employeeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<EmployeeSkillTrainingFact> facts = trainingClassEnrollmentRepository.findSkillTrainingFacts(
                ids, TrainingClassStatus.REJECTED);
        Map<Integer, Map<Integer, SkillSnapshot>> snapshots = new HashMap<>();
        if (facts == null) {
            return snapshots;
        }
        for (EmployeeSkillTrainingFact fact : facts) {
            if (fact == null || fact.getEmployeeId() == null || fact.getSkillId() == null) {
                continue;
            }
            SkillSnapshot snapshot = snapshots
                    .computeIfAbsent(fact.getEmployeeId(), ignored -> new HashMap<>())
                    .computeIfAbsent(fact.getSkillId(), ignored -> new SkillSnapshot(fact.getSkillId(), fact.getSkillName()));
            if (snapshot.skillName == null || snapshot.skillName.isBlank()) {
                snapshot.skillName = fact.getSkillName();
            }
            applyFact(snapshot, fact);
        }
        return snapshots;
    }

    private void applyFact(SkillSnapshot snapshot, EmployeeSkillTrainingFact fact) {
        if (fact.getClassStatus() == TrainingClassStatus.APPROVED && fact.getResult() == TrainingResult.PASS) {
            LocalDate passDate = fact.getEvaluatedAt() != null
                    ? fact.getEvaluatedAt().toLocalDate()
                    : fact.getEndDate();
            snapshot.certified = true;
            if (passDate != null && (snapshot.certifiedDate == null || passDate.isAfter(snapshot.certifiedDate))) {
                snapshot.certifiedDate = passDate;
            }
        }
        LocalDateTime activity = activityTime(fact);
        if (snapshot.latestActivity == null || activity.isAfter(snapshot.latestActivity)) {
            snapshot.latestActivity = activity;
            snapshot.latestCode = latestCode(fact);
            snapshot.latestDate = latestDisplayDate(fact, snapshot.latestCode);
        }
    }

    private LocalDateTime activityTime(EmployeeSkillTrainingFact fact) {
        if (fact.getEvaluatedAt() != null) {
            return fact.getEvaluatedAt();
        }
        if (fact.getStartDate() != null) {
            return fact.getStartDate().atStartOfDay();
        }
        if (fact.getEndDate() != null) {
            return fact.getEndDate().atStartOfDay();
        }
        return LocalDateTime.MIN;
    }

    private String latestCode(EmployeeSkillTrainingFact fact) {
        if (fact.getResult() == TrainingResult.PASS) {
            return "PASS";
        }
        if (fact.getResult() == TrainingResult.NOT_PASS) {
            return "NOT_PASS";
        }
        if (factStillOpen(fact)) {
            return "IN_TRAINING";
        }
        return null;
    }

    private LocalDate latestDisplayDate(EmployeeSkillTrainingFact fact, String latestCode) {
        if ("IN_TRAINING".equals(latestCode)) {
            return fact.getStartDate() != null ? fact.getStartDate() : fact.getEndDate();
        }
        if (fact.getEvaluatedAt() != null) {
            return fact.getEvaluatedAt().toLocalDate();
        }
        return fact.getEndDate();
    }

    private boolean factStillOpen(EmployeeSkillTrainingFact fact) {
        if (fact.getResult() != null || fact.getEndDate() == null) {
            return fact.getResult() == null;
        }
        LocalDate today = VietnamTime.today();
        if (fact.getEndDate().isAfter(today)) {
            return true;
        }
        if (fact.getEndDate().isBefore(today)) {
            return false;
        }
        LocalTime endTime = fact.getEndTime();
        return endTime == null || endTime.isAfter(VietnamTime.currentTime());
    }

    private List<TrainingSkill> sortedSkills(TrainingClass trainingClass) {
        if (trainingClass == null || trainingClass.getSkills() == null || trainingClass.getSkills().isEmpty()) {
            return List.of();
        }
        return trainingClass.getSkills().stream()
                .filter(skill -> skill != null && skill.getId() != null)
                .sorted(Comparator.comparing(
                        skill -> skill.getSkillName() == null ? "" : skill.getSkillName(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<EmployeeSkillStatusLine> skillLines(Integer employeeId,
                                                     List<TrainingSkill> skills,
                                                     Map<Integer, SkillSnapshot> snapshots,
                                                     Set<Integer> studyingSkillIds,
                                                     TrainingClass currentClass,
                                                     TrainingClassEnrollment currentEnrollment) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<EmployeeSkillStatusLine> lines = new ArrayList<>();
        for (TrainingSkill skill : skills) {
            SkillSnapshot snapshot = copySnapshot(snapshots.get(skill.getId()), skill);
            if (currentClass != null && currentEnrollment != null) {
                applyFact(snapshot, new EmployeeSkillTrainingFact(
                        employeeId,
                        skill.getId(),
                        skill.getSkillName(),
                        currentEnrollment.getResult(),
                        currentEnrollment.getEvaluatedAt(),
                        currentClass.getStartDate(),
                        currentClass.getEndDate(),
                        currentClass.getEndTime(),
                        currentClass.getStatus()));
            }
            boolean studying = studyingSkillIds != null && studyingSkillIds.contains(skill.getId());
            if (studying && !"IN_TRAINING".equals(snapshot.latestCode)) {
                snapshot.latestCode = "IN_TRAINING";
                snapshot.latestDate = null;
            }
            lines.add(toSkillLine(snapshot));
        }
        return lines;
    }

    private List<EmployeeSkillStatusLine> historyLines(Map<Integer, SkillSnapshot> snapshots,
                                                       Set<Integer> studyingSkillIds) {
        if ((snapshots == null || snapshots.isEmpty()) && (studyingSkillIds == null || studyingSkillIds.isEmpty())) {
            return List.of();
        }
        Map<Integer, SkillSnapshot> merged = new HashMap<>();
        if (snapshots != null) {
            snapshots.forEach((skillId, snapshot) -> merged.put(skillId, copySnapshot(snapshot, null)));
        }
        if (studyingSkillIds != null) {
            for (Integer skillId : studyingSkillIds) {
                SkillSnapshot snapshot = merged.computeIfAbsent(skillId, ignored -> new SkillSnapshot(skillId, null));
                if (!"IN_TRAINING".equals(snapshot.latestCode)) {
                    snapshot.latestCode = "IN_TRAINING";
                    snapshot.latestDate = null;
                }
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(
                        snapshot -> snapshot.skillName == null ? "" : snapshot.skillName,
                        String.CASE_INSENSITIVE_ORDER))
                .map(this::toSkillLine)
                .toList();
    }

    private SkillSnapshot copySnapshot(SkillSnapshot source, TrainingSkill skill) {
        SkillSnapshot copy = new SkillSnapshot(
                skill != null ? skill.getId() : (source == null ? null : source.skillId),
                skill != null && skill.getSkillName() != null
                        ? skill.getSkillName()
                        : (source == null ? null : source.skillName));
        if (source != null) {
            copy.certified = source.certified;
            copy.certifiedDate = source.certifiedDate;
            copy.latestCode = source.latestCode;
            copy.latestDate = source.latestDate;
            copy.latestActivity = source.latestActivity;
        }
        return copy;
    }

    private EmployeeSkillStatusLine toSkillLine(SkillSnapshot snapshot) {
        boolean certified = snapshot.certified;
        String skillName = snapshot.skillName == null || snapshot.skillName.isBlank() ? "Kỹ năng" : snapshot.skillName;
        CertificationStatus status = certified ? CertificationStatus.CERTIFIED : CertificationStatus.NOTCERTIFIED;
        String latestCode = "IN_TRAINING".equals(snapshot.latestCode) || "PASS".equals(snapshot.latestCode)
                || "NOT_PASS".equals(snapshot.latestCode)
                ? snapshot.latestCode
                : null;
        return EmployeeSkillStatusLine.builder()
                .skillId(snapshot.skillId)
                .skillName(skillName)
                .certificationStatus(status)
                .certificationLabel(status.name() + " - " + skillName)
                .certifiedDateLabel(certified ? formatDate(snapshot.certifiedDate) : null)
                .latestResultCode(latestCode)
                .latestResultLabel(latestLabel(latestCode))
                .latestDateLabel(latestCode == null ? null : formatDate(snapshot.latestDate))
                .build();
    }

    private String latestLabel(String latestCode) {
        if ("PASS".equals(latestCode)) {
            return "PASS";
        }
        if ("NOT_PASS".equals(latestCode)) {
            return "NOT PASS";
        }
        if ("IN_TRAINING".equals(latestCode)) {
            return "ĐANG ĐÀO TẠO";
        }
        return null;
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FORMAT);
    }

    private static final class SkillSnapshot {
        private final Integer skillId;
        private String skillName;
        private boolean certified;
        private LocalDate certifiedDate;
        private String latestCode;
        private LocalDate latestDate;
        private LocalDateTime latestActivity;

        private SkillSnapshot(Integer skillId, String skillName) {
            this.skillId = skillId;
            this.skillName = skillName;
        }
    }
}
