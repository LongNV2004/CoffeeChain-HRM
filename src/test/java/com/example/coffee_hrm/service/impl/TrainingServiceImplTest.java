package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.NotificationType;
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
import com.example.coffee_hrm.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private NotificationService notificationService;

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
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
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
    void activateSkillRestoresInactiveSkill() {
        when(trainingSkillRepository.findById(11)).thenReturn(Optional.of(TrainingSkill.builder()
                .id(11)
                .skillName("Latte Art")
                .status(TrainingSkillStatus.INACTIVE)
                .build()));

        var response = trainingService.activateSkill(11, manager);

        assertEquals(TrainingSkillStatus.ACTIVE, response.getStatus());
    }

    @Test
    void activateSkillRejectsAlreadyActiveSkill() {
        when(trainingSkillRepository.findById(11)).thenReturn(Optional.of(TrainingSkill.builder()
                .id(11)
                .skillName("Latte Art")
                .status(TrainingSkillStatus.ACTIVE)
                .build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.activateSkill(11, manager));
        assertEquals("Kỹ năng này đang được sử dụng.", ex.getMessage());
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
        verify(notificationService).notifyUsers(
                any(),
                eq("Lớp đào tạo đã được duyệt"),
                contains("đã được Admin duyệt"),
                eq(NotificationType.TRAINING_CLASS_APPROVED),
                eq("TRAINING_CLASS"),
                eq(3));
    }

    @Test
    void rejectNotifiesCreator() {
        TrainingClass pending = TrainingClass.builder()
                .id(4)
                .className("Ca chiều")
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .skill(TrainingSkill.builder().id(1).skillName("Espresso").build())
                .store(Store.builder().id(5).storeName("Store A").build())
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build();
        when(trainingClassRepository.findByIdWithDetails(4)).thenReturn(Optional.of(pending));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));

        var response = trainingService.rejectClass(4, admin);

        assertEquals(TrainingClassStatus.REJECTED, response.getStatus());
        verify(notificationService).notifyUsers(
                any(),
                eq("Lớp đào tạo bị từ chối"),
                contains("đã bị từ chối"),
                eq(NotificationType.TRAINING_CLASS_REJECTED),
                eq("TRAINING_CLASS"),
                eq(4));
    }

    @Test
    void createClassRejectsInvalidTimeRange() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));

        CreateTrainingClassRequest request = validClassRequest();
        request.setEndTime(LocalTime.of(7, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createClass(request, manager));
        assertEquals("Giờ bắt đầu phải nhỏ hơn giờ kết thúc.", ex.getMessage());
        verify(trainingClassRepository, never()).save(any());
    }

    @Test
    void createClassRejectsOverlappingSchedule() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(TrainingSkill.builder()
                .id(9)
                .skillName("Barista")
                .status(TrainingSkillStatus.ACTIVE)
                .build()));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED))
                .thenReturn(List.of(existingClass(
                        LocalDate.of(2026, 9, 21),
                        LocalDate.of(2026, 9, 22),
                        LocalTime.of(7, 30),
                        LocalTime.of(11, 30))));

        CreateTrainingClassRequest request = classRequest(
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 22),
                LocalTime.of(11, 0),
                LocalTime.of(12, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createClass(request, manager));
        assertEquals("Lịch lớp bị trùng với lớp đào tạo khác của cửa hàng.", ex.getMessage());
        verify(trainingClassRepository, never()).save(any());
    }

    @Test
    void createClassAllowsSameDatesWithDifferentTimeSlots() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingSkill skill = TrainingSkill.builder()
                .id(9)
                .skillName("Barista")
                .status(TrainingSkillStatus.ACTIVE)
                .build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED))
                .thenReturn(List.of(existingClass(
                        LocalDate.of(2026, 9, 21),
                        LocalDate.of(2026, 9, 22),
                        LocalTime.of(7, 30),
                        LocalTime.of(11, 30))));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        when(trainingClassRepository.save(any(TrainingClass.class))).thenAnswer(invocation -> {
            TrainingClass saved = invocation.getArgument(0);
            saved.setId(22);
            return saved;
        });
        when(trainingClassRepository.findByIdWithDetails(22)).thenReturn(Optional.of(TrainingClass.builder()
                .id(22)
                .skill(skill)
                .store(store)
                .className("Lớp Barista T9")
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build()));
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of(user(1, RoleName.ADMIN, null)));

        CreateTrainingClassRequest request = classRequest(
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 22),
                LocalTime.of(11, 31),
                LocalTime.of(12, 0));

        trainingService.createClass(request, manager);

        verify(trainingClassRepository).save(any(TrainingClass.class));
        verify(notificationService).notifyUsers(
                any(),
                eq("Lớp đào tạo chờ duyệt"),
                contains("Lớp Barista T9"),
                eq(NotificationType.TRAINING_CLASS_CREATED),
                eq("TRAINING_CLASS"),
                eq(22));
    }

    @Test
    void createClassUsesManagerStoreFromAuthentication() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingSkill skill = TrainingSkill.builder()
                .id(9)
                .skillName("Barista")
                .status(TrainingSkillStatus.ACTIVE)
                .build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED)).thenReturn(List.of());
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        when(trainingClassRepository.save(any(TrainingClass.class))).thenAnswer(invocation -> {
            TrainingClass saved = invocation.getArgument(0);
            saved.setId(21);
            return saved;
        });
        when(trainingClassRepository.findByIdWithDetails(21)).thenAnswer(invocation -> {
            TrainingClass saved = TrainingClass.builder()
                    .id(21)
                    .skill(skill)
                    .store(store)
                    .className("Lớp Barista T9")
                    .startDate(LocalDate.now().plusDays(1))
                    .endDate(LocalDate.now().plusDays(1))
                    .startTime(LocalTime.of(8, 0))
                    .endTime(LocalTime.of(10, 0))
                    .status(TrainingClassStatus.PENDING_APPROVAL)
                    .createdBy(user(2, RoleName.MANAGER, 20))
                    .build();
            return Optional.of(saved);
        });
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of());

        trainingService.createClass(validClassRequest(), manager);

        ArgumentCaptor<TrainingClass> captor = ArgumentCaptor.forClass(TrainingClass.class);
        verify(trainingClassRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getStore().getId());
        assertNull(captor.getValue().getLocation());
    }

    @Test
    void listClassesForManagerUsesBackendPageAndSortsStartTime() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        TrainingClass barista = TrainingClass.builder()
                .id(10)
                .className("Lớp Barista sáng")
                .skill(TrainingSkill.builder().id(9).skillName("Barista").build())
                .store(store)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .status(TrainingClassStatus.APPROVED)
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build();
        when(trainingClassRepository.searchApprovedActiveForStore(
                eq(5), eq(TrainingClassStatus.APPROVED), eq(LocalDate.now()),
                eq(9), eq(LocalDate.now()), eq("barista"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(barista)));

        var page = trainingService.listClassesForManager(
                manager, 9, LocalDate.now(), "barista", "desc", 1);

        assertEquals(1, page.getContent().size());
        assertEquals(10, page.getContent().getFirst().getId());
        assertTrue(page.getContent().getFirst().getStudents().isEmpty());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(trainingClassRepository).searchApprovedActiveForStore(
                eq(5), eq(TrainingClassStatus.APPROVED), eq(LocalDate.now()),
                eq(9), eq(LocalDate.now()), eq("barista"), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(6, pageable.getPageSize());
        assertEquals(0, pageable.getPageNumber());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("startTime").getDirection());
    }

    @Test
    void classDetailHidesPendingClassesFromManager() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(7)).thenReturn(Optional.of(TrainingClass.builder()
                .id(7)
                .className("Chờ duyệt")
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .store(store)
                .skill(TrainingSkill.builder().id(1).skillName("Espresso").build())
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.getApprovedClassDetailForManager(7, manager));
        assertEquals("Không tìm thấy lớp đào tạo.", ex.getMessage());
    }

    @Test
    void managerCanDeleteApprovedActiveClassInOwnStore() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        TrainingClass approved = TrainingClass.builder()
                .id(10)
                .className("Lớp Barista")
                .status(TrainingClassStatus.APPROVED)
                .store(store)
                .endDate(LocalDate.now().plusDays(2))
                .skill(TrainingSkill.builder().id(1).skillName("Espresso").build())
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build();
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(approved));

        trainingService.deleteClassForManager(10, manager);

        verify(trainingClassRepository).delete(approved);
    }

    @Test
    void managerCannotDeletePendingClassFromList() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(7)).thenReturn(Optional.of(TrainingClass.builder()
                .id(7)
                .className("Chờ duyệt")
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .store(store)
                .skill(TrainingSkill.builder().id(1).skillName("Espresso").build())
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.deleteClassForManager(7, manager));
        assertEquals("Không tìm thấy lớp đào tạo.", ex.getMessage());
        verify(trainingClassRepository, never()).delete(any());
    }

    private CreateTrainingClassRequest validClassRequest() {
        return classRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0));
    }

    private CreateTrainingClassRequest classRequest(LocalDate startDate,
                                                    LocalDate endDate,
                                                    LocalTime startTime,
                                                    LocalTime endTime) {
        return CreateTrainingClassRequest.builder()
                .skillId(9)
                .className("Lớp Barista T9")
                .startDate(startDate)
                .endDate(endDate)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    private TrainingClass existingClass(LocalDate startDate,
                                        LocalDate endDate,
                                        LocalTime startTime,
                                        LocalTime endTime) {
        return TrainingClass.builder()
                .id(1)
                .className("Lớp cũ")
                .startDate(startDate)
                .endDate(endDate)
                .startTime(startTime)
                .endTime(endTime)
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .build();
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
