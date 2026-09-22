package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
import com.example.coffee_hrm.dto.response.TrainingClassResponse;
import com.example.coffee_hrm.dto.response.TrainingSkillResponse;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingSkill;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.StoreRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingServiceImpl implements TrainingService {

    private final TrainingSkillRepository trainingSkillRepository;
    private final TrainingClassRepository trainingClassRepository;
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
        if (store == null) {
            return Page.empty(pageable);
        }
        String normalizedKeyword = blankToNull(keyword);

        Page<TrainingClass> result = trainingClassRepository.searchApprovedActiveForStore(
                store.getId(),
                TrainingClassStatus.APPROVED,
                LocalDate.now(),
                skillId,
                date,
                normalizedKeyword,
                pageable);
        if (result.getTotalPages() > 0 && pageIndex >= result.getTotalPages()) {
            pageable = PageRequest.of(result.getTotalPages() - 1, CLASS_LIST_PAGE_SIZE, sort);
            result = trainingClassRepository.searchApprovedActiveForStore(
                    store.getId(),
                    TrainingClassStatus.APPROVED,
                    LocalDate.now(),
                    skillId,
                    date,
                    normalizedKeyword,
                    pageable);
        }
        return result.map(this::toClassResponse);
    }

    @Override
    public TrainingClassResponse getApprovedClassDetailForManager(Integer classId, AuthenticatedUser manager) {
        return toClassResponse(requireVisibleApprovedClass(classId, manager));
    }

    @Override
    @Transactional
    public void deleteClassForManager(Integer classId, AuthenticatedUser manager) {
        TrainingClass trainingClass = requireVisibleApprovedClass(classId, manager);
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
        validateClassSchedule(request);

        TrainingSkill skill = trainingSkillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy kỹ năng đào tạo."));
        if (skill.getStatus() != TrainingSkillStatus.ACTIVE) {
            throw new BusinessException("Không thể chọn kỹ năng đã ngừng sử dụng.");
        }

        ensureNoScheduleOverlap(store.getId(), request, null);

        TrainingClass trainingClass = TrainingClass.builder()
                .skill(skill)
                .store(store)
                .className(request.getClassName().trim())
                .trainer(blankToNull(request.getTrainer()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .maxParticipants(request.getMaxParticipants())
                .notes(blankToNull(request.getNotes()))
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .createdBy(loadUser(manager.getUserId()))
                .build();

        TrainingClass saved = trainingClassRepository.save(trainingClass);
        TrainingClass detailed = trainingClassRepository.findByIdWithDetails(saved.getId()).orElse(saved);
        notifyAdminsNewClass(detailed, manager);
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
        trainingClass.setApprovedAt(LocalDateTime.now());
        notifyManagerReviewResult(trainingClass, targetStatus);
        return toClassResponse(trainingClass);
    }

    private void validateClassSchedule(CreateTrainingClassRequest request) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException("Vui lòng chọn giờ bắt đầu và giờ kết thúc.");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("Giờ bắt đầu phải nhỏ hơn giờ kết thúc.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
    }

    private void ensureNoScheduleOverlap(Integer storeId, CreateTrainingClassRequest request, Integer excludeClassId) {
        boolean overlapped = trainingClassRepository.findActiveByStoreId(storeId, TrainingClassStatus.REJECTED).stream()
                .filter(existing -> excludeClassId == null || !excludeClassId.equals(existing.getId()))
                .anyMatch(existing -> overlaps(request, existing));
        if (overlapped) {
            throw new BusinessException("Lịch lớp bị trùng với lớp đào tạo khác của cửa hàng.");
        }
    }

    private boolean overlaps(CreateTrainingClassRequest request, TrainingClass existing) {
        boolean datesOverlap = !request.getStartDate().isAfter(existing.getEndDate())
                && !request.getEndDate().isBefore(existing.getStartDate());
        if (!datesOverlap) {
            return false;
        }

        LocalTime existingStartTime = existing.getStartTime() != null ? existing.getStartTime() : LocalTime.MIN;
        LocalTime existingEndTime = existing.getEndTime() != null ? existing.getEndTime() : LocalTime.MAX;
        return request.getStartTime().isBefore(existingEndTime)
                && request.getEndTime().isAfter(existingStartTime);
    }

    private Sort classListSort(LocalDate date, String sortDir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort startTimeSort = Sort.by(direction, "startTime").and(Sort.by(Sort.Direction.ASC, "id"));
        if (date != null) {
            return startTimeSort;
        }
        return Sort.by(Sort.Direction.ASC, "startDate").and(startTimeSort);
    }

    private void notifyAdminsNewClass(TrainingClass trainingClass, AuthenticatedUser manager) {
        List<User> admins = userRepository.findActiveByRoleName(RoleName.ADMIN);
        String managerName = manager.getDisplayName() != null ? manager.getDisplayName() : manager.getUsername();
        String schedule = formatClassSchedule(trainingClass);
        String message = "Manager " + managerName
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

    private void notifyManagerReviewResult(TrainingClass trainingClass, TrainingClassStatus targetStatus) {
        User creator = trainingClass.getCreatedBy();
        if (creator == null) {
            return;
        }
        boolean approved = targetStatus == TrainingClassStatus.APPROVED;
        String title = approved ? "Lớp đào tạo đã được duyệt" : "Lớp đào tạo bị từ chối";
        String message = approved
                ? "Lớp \"" + trainingClass.getClassName() + "\" đã được Admin duyệt."
                : "Lớp \"" + trainingClass.getClassName() + "\" đã bị từ chối.";
        notificationService.notifyUsers(
                List.of(creator),
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
        requireManager(manager);
        Store store = requireManagerStore(manager);
        TrainingClass trainingClass = trainingClassRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lớp đào tạo."));
        if (trainingClass.getStore() == null || !store.getId().equals(trainingClass.getStore().getId())) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        if (trainingClass.getStatus() != TrainingClassStatus.APPROVED) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        if (trainingClass.getEndDate() != null && trainingClass.getEndDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Không tìm thấy lớp đào tạo.");
        }
        return trainingClass;
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
        return skillName.trim();
    }

    private void requireManager(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Chỉ Manager mới được thực hiện thao tác này.");
        }
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException("Chỉ Admin mới được duyệt lớp đào tạo.");
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
        return TrainingClassResponse.builder()
                .id(trainingClass.getId())
                .skillId(trainingClass.getSkill() != null ? trainingClass.getSkill().getId() : null)
                .skillName(trainingClass.getSkill() != null ? trainingClass.getSkill().getSkillName() : null)
                .storeId(trainingClass.getStore() != null ? trainingClass.getStore().getId() : null)
                .storeName(trainingClass.getStore() != null ? trainingClass.getStore().getStoreName() : null)
                .className(trainingClass.getClassName())
                .trainer(trainingClass.getTrainer())
                .startDate(trainingClass.getStartDate())
                .endDate(trainingClass.getEndDate())
                .startTime(trainingClass.getStartTime())
                .endTime(trainingClass.getEndTime())
                .location(trainingClass.getLocation())
                .maxParticipants(trainingClass.getMaxParticipants())
                .notes(trainingClass.getNotes())
                .status(trainingClass.getStatus())
                .createdByName(userDisplayName(trainingClass.getCreatedBy()))
                .students(List.of())
                .approvedByName(userDisplayName(trainingClass.getApprovedBy()))
                .approvedAt(trainingClass.getApprovedAt())
                .createdAt(trainingClass.getCreatedAt())
                .build();
    }
}
