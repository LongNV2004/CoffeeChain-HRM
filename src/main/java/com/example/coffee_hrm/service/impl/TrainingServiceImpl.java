package com.example.coffee_hrm.service.impl;

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
import com.example.coffee_hrm.service.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingServiceImpl implements TrainingService {

    private final TrainingSkillRepository trainingSkillRepository;
    private final TrainingClassRepository trainingClassRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

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
    public boolean managerHasAssignedStore(AuthenticatedUser manager) {
        requireManager(manager);
        return resolveManagerStore(manager) != null;
    }

    @Override
    public List<TrainingClassResponse> listClassesForManager(AuthenticatedUser manager) {
        requireManager(manager);
        Store store = resolveManagerStore(manager);
        if (store == null) {
            return List.of();
        }
        return trainingClassRepository.findByStoreIdWithDetails(store.getId()).stream()
                .map(this::toClassResponse)
                .toList();
    }

    @Override
    @Transactional
    public TrainingClassResponse createClass(CreateTrainingClassRequest request, AuthenticatedUser manager) {
        requireManager(manager);
        Store store = resolveManagerStore(manager);
        if (store == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gán cửa hàng.");
        }
        validateClassSchedule(request);

        TrainingSkill skill = trainingSkillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy kỹ năng đào tạo."));
        if (skill.getStatus() != TrainingSkillStatus.ACTIVE) {
            throw new BusinessException("Không thể chọn kỹ năng đã ngừng sử dụng.");
        }

        TrainingClass trainingClass = TrainingClass.builder()
                .skill(skill)
                .store(store)
                .className(request.getClassName().trim())
                .trainer(blankToNull(request.getTrainer()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .location(blankToNull(request.getLocation()))
                .maxParticipants(request.getMaxParticipants())
                .notes(blankToNull(request.getNotes()))
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .createdBy(loadUser(manager.getUserId()))
                .build();

        TrainingClass saved = trainingClassRepository.save(trainingClass);
        return toClassResponse(trainingClassRepository.findByIdWithDetails(saved.getId()).orElse(saved));
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
        return toClassResponse(trainingClass);
    }

    private void validateClassSchedule(CreateTrainingClassRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && !request.getEndTime().isAfter(request.getStartTime())
                && request.getEndDate().isEqual(request.getStartDate())) {
            throw new BusinessException("Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.");
        }
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
                .createdByName(trainingClass.getCreatedBy() != null ? trainingClass.getCreatedBy().getUsername() : null)
                .approvedByName(trainingClass.getApprovedBy() != null ? trainingClass.getApprovedBy().getUsername() : null)
                .approvedAt(trainingClass.getApprovedAt())
                .createdAt(trainingClass.getCreatedAt())
                .build();
    }
}
