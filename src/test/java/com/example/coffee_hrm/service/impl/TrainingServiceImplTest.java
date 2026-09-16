package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingSkill;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassRepository;
import com.example.coffee_hrm.repository.TrainingSkillRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingServiceImplTest {

    @Mock
    private TrainingSkillRepository trainingSkillRepository;
    @Mock
    private TrainingClassRepository trainingClassRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TrainingServiceImpl trainingService;

    private AuthenticatedUser manager;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        manager = AuthenticatedUser.from(user(2, RoleName.MANAGER, 20));
        admin = AuthenticatedUser.from(user(1, RoleName.ADMIN, null));
    }

    @Test
    void createClassRejectsInactiveSkill() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(TrainingSkill.builder()
                .id(9)
                .skillName("Barista")
                .status(TrainingSkillStatus.INACTIVE)
                .build()));

        CreateTrainingClassRequest request = CreateTrainingClassRequest.builder()
                .skillId(9)
                .className("Lớp Barista T9")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createClass(request, manager));
        assertEquals("Không thể chọn kỹ năng đã ngừng sử dụng.", ex.getMessage());
        verify(trainingClassRepository, never()).save(any());
    }

    @Test
    void managerCannotApproveClass() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.approveClass(1, manager));
        assertEquals("Chỉ Admin mới được duyệt lớp đào tạo.", ex.getMessage());
    }

    @Test
    void createSkillPersistsActiveStatus() {
        when(trainingSkillRepository.existsBySkillNameIgnoreCase("Latte Art")).thenReturn(false);
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        when(trainingSkillRepository.save(any(TrainingSkill.class))).thenAnswer(invocation -> {
            TrainingSkill skill = invocation.getArgument(0);
            skill.setId(11);
            return skill;
        });

        trainingService.createSkill(CreateTrainingSkillRequest.builder()
                .skillName("Latte Art")
                .description("Pour")
                .requirements("6 tháng kinh nghiệm")
                .build(), manager);

        ArgumentCaptor<TrainingSkill> captor = ArgumentCaptor.forClass(TrainingSkill.class);
        verify(trainingSkillRepository).save(captor.capture());
        assertEquals(TrainingSkillStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals("Latte Art", captor.getValue().getSkillName());
    }

    @Test
    void approveSetsReviewerAndStatus() {
        TrainingClass pending = TrainingClass.builder()
                .id(3)
                .className("Ca sáng")
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .skill(TrainingSkill.builder().id(1).skillName("Espresso").build())
                .store(Store.builder().id(5).storeName("Store A").build())
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build();
        when(trainingClassRepository.findByIdWithDetails(3)).thenReturn(Optional.of(pending));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));

        var response = trainingService.approveClass(3, admin);

        assertEquals(TrainingClassStatus.APPROVED, response.getStatus());
        assertEquals("admin", response.getApprovedByName());
    }

    private User user(Integer id, RoleName roleName, Integer employeeId) {
        com.example.coffee_hrm.entity.Role role = com.example.coffee_hrm.entity.Role.builder()
                .id(roleName == RoleName.ADMIN ? 1 : 2)
                .roleName(roleName)
                .build();
        com.example.coffee_hrm.entity.Employee employee = null;
        if (employeeId != null) {
            employee = com.example.coffee_hrm.entity.Employee.builder()
                    .id(employeeId)
                    .fullName(roleName.name())
                    .store(Store.builder().id(5).storeName("Store A").build())
                    .build();
        }
        return User.builder()
                .id(id)
                .username(roleName == RoleName.ADMIN ? "admin" : "manager")
                .passwordHash("hash")
                .role(role)
                .employee(employee)
                .isActive(true)
                .build();
    }
}
