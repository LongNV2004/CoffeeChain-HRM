package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingType;
import com.example.coffee_hrm.common.enums.TrainingResult;
import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AddTrainingClassStudentsRequest;
import com.example.coffee_hrm.dto.request.CreateCentralizedTrainingRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.EvaluateTrainingStudentRequest;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import com.example.coffee_hrm.entity.TrainingSkill;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingServiceImplTest {

    @Mock
    private TrainingSkillRepository trainingSkillRepository;
    @Mock
    private TrainingClassRepository trainingClassRepository;
    @Mock
    private TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    @Mock
    private EmployeeRepository employeeRepository;
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
                .skillIds(List.of(9))
                .className("Lớp Barista T9")
                .startDate(VietnamTime.today().plusDays(1))
                .endDate(VietnamTime.today().plusDays(2))
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
                .skills(Set.of(TrainingSkill.builder().id(1).skillName("Espresso").build()))
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
                .skills(Set.of(TrainingSkill.builder().id(1).skillName("Espresso").build()))
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
    void createClassRejectsStartThatAlreadyPassedInVietnam() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));

        LocalDateTime startAt = VietnamTime.now().minusMinutes(90).withSecond(0).withNano(0);
        LocalTime endTime = startAt.toLocalTime().plusMinutes(30);
        CreateTrainingClassRequest request = endTime.isAfter(startAt.toLocalTime())
                ? classRequest(startAt.toLocalDate(), startAt.toLocalDate(), startAt.toLocalTime(), endTime)
                : classRequest(
                        VietnamTime.today().minusDays(1),
                        VietnamTime.today().minusDays(1),
                        LocalTime.of(10, 0),
                        LocalTime.of(12, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createClass(request, manager));
        assertEquals(
                "Thời gian bắt đầu đã qua. Vui lòng chọn thời điểm từ hiện tại trở đi theo giờ Việt Nam.",
                ex.getMessage());
        verify(trainingClassRepository, never()).save(any());
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
        LocalDate overlapStart = VietnamTime.today().plusDays(10);
        LocalDate overlapEnd = overlapStart.plusDays(1);
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED))
                .thenReturn(List.of(existingClass(
                        overlapStart,
                        overlapEnd,
                        LocalTime.of(7, 30),
                        LocalTime.of(11, 30))));

        CreateTrainingClassRequest request = classRequest(
                overlapStart,
                overlapEnd,
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
        LocalDate overlapStart = VietnamTime.today().plusDays(10);
        LocalDate overlapEnd = overlapStart.plusDays(1);
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED))
                .thenReturn(List.of(existingClass(
                        overlapStart,
                        overlapEnd,
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
                .skills(Set.of(skill))
                .store(store)
                .className("Lớp Barista T9")
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build()));
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of(user(1, RoleName.ADMIN, null)));

        CreateTrainingClassRequest request = classRequest(
                overlapStart,
                overlapEnd,
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
                    .skills(Set.of(skill))
                    .store(store)
                    .className("Lớp Barista T9")
                    .startDate(VietnamTime.today().plusDays(1))
                    .endDate(VietnamTime.today().plusDays(1))
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
        assertEquals(TrainingType.STORE_TRAINING, captor.getValue().getTrainingType());
        assertEquals(2, captor.getValue().getTrainer().getId());
        assertEquals(2, captor.getValue().getCreatedBy().getId());
        assertEquals(1, captor.getValue().getSkills().size());
        assertNull(captor.getValue().getLocation());
    }

    @Test
    void createClassRejectsDuplicateClassNameWhileExistingClassIsStillOpen() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.existsByClassNameIgnoreCaseAndStatusNotAndEndDateGreaterThanEqual(
                "Lớp Barista T9", TrainingClassStatus.REJECTED, VietnamTime.today())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createClass(validClassRequest(), manager));
        assertEquals("Tên lớp đã tồn tại.", ex.getMessage());
        verify(trainingClassRepository, never()).save(any());
    }

    @Test
    void createClassAllowsReuseOfNameWhenNoOpenClassUsesIt() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingSkill skill = TrainingSkill.builder()
                .id(9)
                .skillName("Barista")
                .status(TrainingSkillStatus.ACTIVE)
                .build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.existsByClassNameIgnoreCaseAndStatusNotAndEndDateGreaterThanEqual(
                "Lớp Barista T9", TrainingClassStatus.REJECTED, VietnamTime.today())).thenReturn(false);
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED)).thenReturn(List.of());
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        when(trainingClassRepository.save(any(TrainingClass.class))).thenAnswer(invocation -> {
            TrainingClass saved = invocation.getArgument(0);
            saved.setId(30);
            return saved;
        });
        when(trainingClassRepository.findByIdWithDetails(30)).thenReturn(Optional.of(TrainingClass.builder()
                .id(30)
                .skills(Set.of(skill))
                .store(store)
                .className("Lớp Barista T9")
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build()));
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of());

        trainingService.createClass(validClassRequest(), manager);

        ArgumentCaptor<TrainingClass> captor = ArgumentCaptor.forClass(TrainingClass.class);
        verify(trainingClassRepository).save(captor.capture());
        assertEquals("Lớp Barista T9", captor.getValue().getClassName());
        assertEquals(TrainingClassStatus.PENDING_APPROVAL, captor.getValue().getStatus());
    }

    @Test
    void listSubmittedClassesForManagerShowsOpenRequestStatuses() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        TrainingClass pending = TrainingClass.builder()
                .id(7)
                .className("Phục vụ 1")
                .skills(Set.of(TrainingSkill.builder().id(9).skillName("Phục vụ").build()))
                .store(store)
                .startDate(VietnamTime.today())
                .endDate(VietnamTime.today().plusDays(2))
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build();
        TrainingClass rejected = TrainingClass.builder()
                .id(8)
                .className("Pha chế")
                .skills(Set.of(TrainingSkill.builder().id(9).skillName("Pha chế").build()))
                .store(store)
                .startDate(VietnamTime.today())
                .endDate(VietnamTime.today().plusDays(1))
                .status(TrainingClassStatus.REJECTED)
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build();
        when(trainingClassRepository.findOpenRequestsForManager(
                5, 2, TrainingType.STORE_TRAINING, VietnamTime.today()))
                .thenReturn(List.of(pending, rejected));

        var requests = trainingService.listSubmittedClassesForManager(manager);

        assertEquals(2, requests.size());
        assertEquals(TrainingClassStatus.PENDING_APPROVAL, requests.get(0).getStatus());
        assertEquals("Chờ duyệt", requests.get(0).getStatus().getLabel());
        assertEquals(TrainingClassStatus.REJECTED, requests.get(1).getStatus());
        assertEquals("Từ chối", requests.get(1).getStatus().getLabel());
    }

    @Test
    void createSkillRejectsNameContainingDigits() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createSkill(CreateTrainingSkillRequest.builder()
                        .skillName("Barista 1")
                        .build(), manager));
        assertEquals("Tên kỹ năng không được chứa số.", ex.getMessage());
        verify(trainingSkillRepository, never()).save(any());
    }

    @Test
    void listClassesForManagerUsesBackendPageAndSortsStartTime() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        TrainingClass barista = TrainingClass.builder()
                .id(10)
                .className("Lớp Barista sáng")
                .skills(Set.of(TrainingSkill.builder().id(9).skillName("Barista").build()))
                .store(store)
                .startDate(VietnamTime.today())
                .endDate(VietnamTime.today().plusDays(3))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .status(TrainingClassStatus.APPROVED)
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build();
        when(trainingClassRepository.searchApprovedActiveForManager(
                eq(5), eq(2), eq(TrainingType.STORE_TRAINING),
                eq(TrainingClassStatus.APPROVED), eq(VietnamTime.today()),
                eq(9), eq(VietnamTime.today()), eq("barista"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(barista)));

        var page = trainingService.listClassesForManager(
                manager, 9, VietnamTime.today(), "barista", "desc", 1);

        assertEquals(1, page.getContent().size());
        assertEquals(10, page.getContent().getFirst().getId());
        assertTrue(page.getContent().getFirst().getStudents().isEmpty());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(trainingClassRepository).searchApprovedActiveForManager(
                eq(5), eq(2), eq(TrainingType.STORE_TRAINING),
                eq(TrainingClassStatus.APPROVED), eq(VietnamTime.today()),
                eq(9), eq(VietnamTime.today()), eq("barista"), captor.capture());
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
                .skills(Set.of(TrainingSkill.builder().id(1).skillName("Espresso").build()))
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
                .endDate(VietnamTime.today().plusDays(2))
                .skills(Set.of(TrainingSkill.builder().id(1).skillName("Espresso").build()))
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
                .skills(Set.of(TrainingSkill.builder().id(1).skillName("Espresso").build()))
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.deleteClassForManager(7, manager));
        assertEquals("Không tìm thấy lớp đào tạo.", ex.getMessage());
        verify(trainingClassRepository, never()).delete(any());
    }

    @Test
    void addStudentsEnrollsActiveEmployeesOfTheSameStore() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass trainingClass = approvedClass(store, VietnamTime.today().plusDays(1));
        Employee first = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        Employee second = employee(31, "Lê Thị Thu", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(0L);
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(first));
        when(employeeRepository.findByIdWithStore(31)).thenReturn(Optional.of(second));
        when(trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(10, 30)).thenReturn(false);
        when(trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(10, 31)).thenReturn(false);
        User firstAccount = staffAccount(40, 30);
        User secondAccount = staffAccount(41, 31);
        when(userRepository.findByEmployee_Id(30)).thenReturn(Optional.of(firstAccount));
        when(userRepository.findByEmployee_Id(31)).thenReturn(Optional.of(secondAccount));

        int added = trainingService.addStudentsToClass(10, AddTrainingClassStudentsRequest.builder()
                .employeeIds(List.of(30, 31, 30))
                .build(), manager);

        assertEquals(2, added);
        assertEquals(CertificationStatus.NOTCERTIFIED, first.getCertificationStatus());
        assertEquals(CertificationStatus.NOTCERTIFIED, second.getCertificationStatus());
        verify(trainingClassEnrollmentRepository, times(2)).save(any(TrainingClassEnrollment.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User>> recipients = ArgumentCaptor.forClass(List.class);
        verify(notificationService).notifyUsers(
                recipients.capture(),
                eq("Bạn được thêm vào lớp đào tạo"),
                eq("Bạn đã được thêm vào lớp đào tạo Lớp Barista."),
                eq(NotificationType.TRAINING_CLASS_ENROLLED),
                eq("TRAINING_CLASS"),
                eq(10));
        assertEquals(List.of(40, 41), recipients.getValue().stream().map(User::getId).toList());
    }

    @Test
    void addStudentsRejectsDuplicateEnrollment() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee employee = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(1L);
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
        when(trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(10, 30)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> trainingService.addStudentsToClass(
                10, AddTrainingClassStudentsRequest.builder().employeeIds(List.of(30)).build(), manager));

        assertEquals("Nhân viên Trần Văn Tuấn đã được ghi danh vào lớp này.", ex.getMessage());
        verify(trainingClassEnrollmentRepository, never()).save(any());
        verify(notificationService, never()).notifyUsers(
                any(), any(), any(), eq(NotificationType.TRAINING_CLASS_ENROLLED), any(), any());
    }

    @Test
    void addStudentsRejectsEmployeeOutsideStoreAndMissingEmployee() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Store other = Store.builder().id(9).storeName("Store B").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));
        when(employeeRepository.findByIdWithStore(30))
                .thenReturn(Optional.of(employee(30, "Người khác", other, EmployeeStatus.ACTIVE)));

        BusinessException wrongStore = assertThrows(BusinessException.class, () -> trainingService.addStudentsToClass(
                10, AddTrainingClassStudentsRequest.builder().employeeIds(List.of(30)).build(), manager));
        assertEquals("Nhân viên không thuộc cửa hàng của bạn.", wrongStore.getMessage());

        when(employeeRepository.findByIdWithStore(99)).thenReturn(Optional.empty());
        BusinessException missing = assertThrows(BusinessException.class, () -> trainingService.addStudentsToClass(
                10, AddTrainingClassStudentsRequest.builder().employeeIds(List.of(99)).build(), manager));
        assertEquals("Không tìm thấy nhân viên.", missing.getMessage());
    }

    @Test
    void addStudentsRejectsWhenCapacityWouldBeExceeded() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass trainingClass = approvedClass(store, VietnamTime.today().plusDays(1));
        trainingClass.setMaxParticipants(1);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> trainingService.addStudentsToClass(
                10, AddTrainingClassStudentsRequest.builder().employeeIds(List.of(30)).build(), manager));

        assertEquals("Số học viên vượt quá sĩ số tối đa của lớp.", ex.getMessage());
        verify(employeeRepository, never()).findByIdWithStore(any());
    }

    @Test
    void endedClassDetailShowsEvaluationStatus() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass trainingClass = approvedClass(store, VietnamTime.today().minusDays(1));
        Employee employee = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.findByClassIdWithEmployee(10)).thenReturn(List.of(
                TrainingClassEnrollment.builder()
                        .employee(employee)
                        .result(TrainingResult.PASS)
                        .evaluationNote("Làm tốt")
                        .build()));

        var detail = trainingService.getApprovedClassDetailForManager(10, manager);

        assertTrue(detail.isEnded());
        assertEquals(TrainingResult.PASS, detail.getStudents().getFirst().getResult());
        assertEquals("Đạt", detail.getStudents().getFirst().getResultLabel());
        assertEquals("Làm tốt", detail.getStudents().getFirst().getEvaluationNote());
    }

    @Test
    void evaluateStudentSavesResultOnlyAfterClassEnds() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee employee = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));

        BusinessException tooEarly = assertThrows(BusinessException.class, () -> trainingService.evaluateStudent(
                10, 30, EvaluateTrainingStudentRequest.builder()
                        .result(TrainingResult.PASS)
                        .updating(false)
                        .build(), manager));
        assertEquals("Chỉ được đánh giá sau khi lớp đào tạo kết thúc.", tooEarly.getMessage());

        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        TrainingClassEnrollment enrollment = TrainingClassEnrollment.builder()
                .trainingClass(ended)
                .employee(employee)
                .build();
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(ended));
        when(trainingClassEnrollmentRepository.findByClassAndEmployee(10, 30)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));

        trainingService.evaluateStudent(10, 30, EvaluateTrainingStudentRequest.builder()
                .result(TrainingResult.NOT_PASS)
                .note("  Cần luyện thêm  ")
                .updating(false)
                .build(), manager);

        assertEquals(TrainingResult.NOT_PASS, enrollment.getResult());
        assertEquals(CertificationStatus.NOTCERTIFIED, employee.getCertificationStatus());
        assertEquals("Cần luyện thêm", enrollment.getEvaluationNote());
        assertNotNull(enrollment.getEvaluatedAt());
        assertNotNull(enrollment.getEvaluatedBy());
    }

    @Test
    void evaluateStudentBlocksRepeatUnlessUpdating() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee employee = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        TrainingClassEnrollment enrollment = TrainingClassEnrollment.builder()
                .trainingClass(ended)
                .employee(employee)
                .result(TrainingResult.PASS)
                .evaluationNote("Đạt")
                .build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(ended));
        when(trainingClassEnrollmentRepository.findByClassAndEmployee(10, 30)).thenReturn(Optional.of(enrollment));

        BusinessException repeat = assertThrows(BusinessException.class, () -> trainingService.evaluateStudent(
                10, 30, EvaluateTrainingStudentRequest.builder()
                        .result(TrainingResult.NOT_PASS)
                        .updating(false)
                        .build(), manager));
        assertEquals("Nhân viên này đã được đánh giá. Hãy dùng chức năng cập nhật kết quả.", repeat.getMessage());
        assertEquals(TrainingResult.PASS, enrollment.getResult());

        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        trainingService.evaluateStudent(10, 30, EvaluateTrainingStudentRequest.builder()
                .result(TrainingResult.NOT_PASS)
                .note("Cập nhật")
                .updating(true)
                .build(), manager);
        assertEquals(TrainingResult.NOT_PASS, enrollment.getResult());
        assertEquals("Cập nhật", enrollment.getEvaluationNote());
    }

    @Test
    void evaluateStudentRejectsEmployeeNotInClassOrAnotherStore() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Store other = Store.builder().id(9).storeName("Store B").build();
        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(ended));
        when(trainingClassEnrollmentRepository.findByClassAndEmployee(10, 30)).thenReturn(Optional.empty());

        BusinessException missing = assertThrows(BusinessException.class, () -> trainingService.evaluateStudent(
                10, 30, EvaluateTrainingStudentRequest.builder()
                        .result(TrainingResult.PASS)
                        .build(), manager));
        assertEquals("Nhân viên không thuộc lớp đào tạo này.", missing.getMessage());

        when(trainingClassEnrollmentRepository.findByClassAndEmployee(10, 31)).thenReturn(Optional.of(
                TrainingClassEnrollment.builder()
                        .employee(employee(31, "Người khác", other, EmployeeStatus.ACTIVE))
                        .build()));
        BusinessException wrongStore = assertThrows(BusinessException.class, () -> trainingService.evaluateStudent(
                10, 31, EvaluateTrainingStudentRequest.builder()
                        .result(TrainingResult.PASS)
                        .build(), manager));
        assertEquals("Nhân viên không thuộc cửa hàng của bạn.", wrongStore.getMessage());
    }

    @Test
    void onlyManagerCanAddOrEvaluateStudents() {
        BusinessException add = assertThrows(BusinessException.class, () -> trainingService.addStudentsToClass(
                10, AddTrainingClassStudentsRequest.builder().employeeIds(List.of(30)).build(), admin));
        BusinessException evaluate = assertThrows(BusinessException.class, () -> trainingService.evaluateStudent(
                10, 30, EvaluateTrainingStudentRequest.builder().result(TrainingResult.PASS).build(), admin));
        assertEquals("Chỉ Manager mới được thực hiện thao tác này.", add.getMessage());
        assertEquals("Chỉ Manager mới được thực hiện thao tác này.", evaluate.getMessage());
    }

    @Test
    void employeeSeesOwnClassesWithResultAndParticipationStatus() {
        AuthenticatedUser staff = AuthenticatedUser.from(user(8, RoleName.STAFF, 30));
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee learner = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        TrainingClass upcoming = approvedClass(store, VietnamTime.today().plusDays(2));
        upcoming.setClassName("Pha Chế 2");
        upcoming.setStartDate(VietnamTime.today().plusDays(1));
        upcoming.setStartTime(LocalTime.of(8, 0));
        User lan = user(4, RoleName.MANAGER, 21);
        lan.getEmployee().setFullName("Cô Lan");
        upcoming.setTrainer(lan);
        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        ended.setId(11);
        ended.setClassName("Lớp cũ");
        ended.setStartDate(VietnamTime.today().minusDays(3));
        when(trainingClassEnrollmentRepository.findByEmployeeAndStatusWithClass(30, TrainingClassStatus.APPROVED))
                .thenReturn(List.of(
                        TrainingClassEnrollment.builder()
                                .trainingClass(ended)
                                .employee(learner)
                                .result(TrainingResult.NOT_PASS)
                                .evaluationNote("Cần luyện")
                                .build(),
                        TrainingClassEnrollment.builder()
                                .trainingClass(upcoming)
                                .employee(learner)
                                .build()));

        var classes = trainingService.listClassesForEmployee(staff);

        assertEquals(2, classes.size());
        assertEquals("Pha Chế 2", classes.get(0).getClassName());
        assertEquals("Chưa bắt đầu", classes.get(0).getParticipationStatus());
        assertEquals("Chưa đánh giá", classes.get(0).getResultLabel());
        assertNull(classes.get(0).getResult());
        assertEquals("Cô Lan", classes.get(0).getTrainer());
        assertEquals("Lớp cũ", classes.get(1).getClassName());
        assertEquals("Đã kết thúc", classes.get(1).getParticipationStatus());
        assertEquals(TrainingResult.NOT_PASS, classes.get(1).getResult());
        assertEquals("Không đạt", classes.get(1).getResultLabel());
        assertEquals("Cần luyện", classes.get(1).getEvaluationNote());
    }

    @Test
    void employeeCannotOpenClassTheyWereNotAddedTo() {
        AuthenticatedUser staff = AuthenticatedUser.from(user(8, RoleName.STAFF, 30));
        when(trainingClassEnrollmentRepository.findOwnedByClassAndEmployee(99, 30)).thenReturn(Optional.empty());

        BusinessException missing = assertThrows(BusinessException.class,
                () -> trainingService.getClassForEmployee(99, staff));
        assertEquals("Không tìm thấy lớp đào tạo.", missing.getMessage());

        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass pending = approvedClass(store, VietnamTime.today().plusDays(1));
        pending.setStatus(TrainingClassStatus.PENDING_APPROVAL);
        when(trainingClassEnrollmentRepository.findOwnedByClassAndEmployee(10, 30)).thenReturn(Optional.of(
                TrainingClassEnrollment.builder().trainingClass(pending).build()));
        BusinessException hidden = assertThrows(BusinessException.class,
                () -> trainingService.getClassForEmployee(10, staff));
        assertEquals("Không tìm thấy lớp đào tạo.", hidden.getMessage());
    }

    @Test
    void employeeDetailShowsPassResultAndOtherRolesCannotOpenIt() {
        AuthenticatedUser staff = AuthenticatedUser.from(user(8, RoleName.STAFF, 30));
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        ended.setClassName("Pha Chế 2");
        User lan = user(4, RoleName.MANAGER, 21);
        lan.getEmployee().setFullName("Cô Lan");
        ended.setTrainer(lan);
        when(trainingClassEnrollmentRepository.findOwnedByClassAndEmployee(10, 30)).thenReturn(Optional.of(
                TrainingClassEnrollment.builder()
                        .trainingClass(ended)
                        .result(TrainingResult.PASS)
                        .evaluationNote("Làm tốt")
                        .build()));

        var detail = trainingService.getClassForEmployee(10, staff);

        assertEquals("Pha Chế 2", detail.getClassName());
        assertEquals("Espresso", detail.getSkillName());
        assertEquals(TrainingResult.PASS, detail.getResult());
        assertEquals("Đạt", detail.getResultLabel());
        assertEquals("Làm tốt", detail.getEvaluationNote());
        assertEquals("Đã kết thúc", detail.getParticipationStatus());

        BusinessException denied = assertThrows(BusinessException.class,
                () -> trainingService.listClassesForEmployee(manager));
        assertEquals("Chỉ nhân viên mới được xem lớp đào tạo của mình.", denied.getMessage());
        BusinessException detailDenied = assertThrows(BusinessException.class,
                () -> trainingService.getClassForEmployee(10, admin));
        assertEquals("Chỉ nhân viên mới được xem lớp đào tạo của mình.", detailDenied.getMessage());
    }

    @Test
    void ongoingClassCountIsOnlyForStaffWithEmployeeProfile() {
        AuthenticatedUser staff = AuthenticatedUser.from(user(8, RoleName.STAFF, 30));
        when(trainingClassEnrollmentRepository.findClassesEndingOnOrAfter(
                eq(30), eq(TrainingClassStatus.APPROVED), any(LocalDate.class)))
                .thenReturn(List.of(
                        TrainingClass.builder().id(1).endDate(VietnamTime.today().plusDays(1)).build(),
                        TrainingClass.builder().id(2).endDate(VietnamTime.today()).endTime(LocalTime.MIN).build()));

        assertEquals(1L, trainingService.countOngoingClassesForEmployee(staff));
        assertEquals(0L, trainingService.countOngoingClassesForEmployee(manager));
        assertEquals(0L, trainingService.countOngoingClassesForEmployee(null));
    }

    @Test
    void evaluatePassUpdatesCertificationAndNotPassClearsIt() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee learner = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        TrainingClassEnrollment enrollment = TrainingClassEnrollment.builder()
                .trainingClass(ended)
                .employee(learner)
                .build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(ended));
        when(trainingClassEnrollmentRepository.findByClassAndEmployee(10, 30)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));

        trainingService.evaluateStudent(10, 30, EvaluateTrainingStudentRequest.builder()
                .result(TrainingResult.PASS)
                .updating(false)
                .build(), manager);

        assertEquals(CertificationStatus.CERTIFIED, learner.getCertificationStatus());

        trainingService.evaluateStudent(10, 30, EvaluateTrainingStudentRequest.builder()
                .result(TrainingResult.NOT_PASS)
                .updating(true)
                .build(), manager);

        assertEquals(CertificationStatus.NOTCERTIFIED, learner.getCertificationStatus());
    }

    @Test
    void onlyAssignedTrainerCanEvaluate() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        ended.setTrainer(user(9, RoleName.MANAGER, 90));
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(ended));

        BusinessException ex = assertThrows(BusinessException.class, () -> trainingService.evaluateStudent(
                10, 30, EvaluateTrainingStudentRequest.builder()
                        .result(TrainingResult.PASS)
                        .build(), manager));

        assertEquals("Chỉ người đào tạo của lớp mới được đánh giá học viên.", ex.getMessage());
    }

    @Test
    void managerCannotCreateCentralizedClassAndAdminCannotCreateStoreClass() {
        BusinessException managerDenied = assertThrows(BusinessException.class,
                () -> trainingService.createCentralizedClass(CreateCentralizedTrainingRequest.builder()
                        .className("Tập trung")
                        .skillIds(List.of(9))
                        .storeIds(List.of(5, 6))
                        .employeeIds(List.of(30))
                        .trainerId(2)
                        .startDate(VietnamTime.today().plusDays(1))
                        .endDate(VietnamTime.today().plusDays(1))
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(10, 0))
                        .build(), manager));
        assertEquals("Chỉ Admin mới được tạo lớp đào tạo tập trung.", managerDenied.getMessage());

        BusinessException adminDenied = assertThrows(BusinessException.class,
                () -> trainingService.createClass(validClassRequest(), admin));
        assertEquals("Chỉ Manager mới được thực hiện thao tác này.", adminDenied.getMessage());
    }

    @Test
    void adminCreatesCentralizedClassWithChosenTrainerNotThemselves() {
        Store firstStore = Store.builder().id(5).storeName("Store A").build();
        Store secondStore = Store.builder().id(6).storeName("Store B").build();
        TrainingSkill skill = TrainingSkill.builder().id(9).skillName("Barista").status(TrainingSkillStatus.ACTIVE).build();
        Employee firstEmployee = employee(30, "An", firstStore, EmployeeStatus.ACTIVE);
        Employee secondEmployee = employee(31, "Binh", secondStore, EmployeeStatus.ACTIVE);
        User trainer = user(2, RoleName.MANAGER, 20);
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(storeRepository.findById(5)).thenReturn(Optional.of(firstStore));
        when(storeRepository.findById(6)).thenReturn(Optional.of(secondStore));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED)).thenReturn(List.of());
        when(trainingClassRepository.findActiveByStoreId(6, TrainingClassStatus.REJECTED)).thenReturn(List.of());
        when(userRepository.findByIdWithRole(2)).thenReturn(Optional.of(trainer));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(firstEmployee));
        when(employeeRepository.findByIdWithStore(31)).thenReturn(Optional.of(secondEmployee));
        when(trainingClassRepository.save(any(TrainingClass.class))).thenAnswer(invocation -> {
            TrainingClass saved = invocation.getArgument(0);
            saved.setId(40);
            return saved;
        });
        when(trainingClassRepository.findByIdWithDetails(40)).thenReturn(Optional.of(TrainingClass.builder()
                .id(40)
                .trainingType(TrainingType.CENTRALIZED_TRAINING)
                .className("Lớp tập trung")
                .skills(Set.of(skill))
                .participatingStores(Set.of(firstStore, secondStore))
                .trainer(trainer)
                .createdBy(user(1, RoleName.ADMIN, null))
                .status(TrainingClassStatus.APPROVED)
                .build()));

        trainingService.createCentralizedClass(CreateCentralizedTrainingRequest.builder()
                .className("Lớp tập trung")
                .skillIds(List.of(9))
                .storeIds(List.of(5, 6))
                .employeeIds(List.of(30, 31))
                .trainerId(2)
                .startDate(VietnamTime.today().plusDays(2))
                .endDate(VietnamTime.today().plusDays(2))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .build(), admin);

        ArgumentCaptor<TrainingClass> captor = ArgumentCaptor.forClass(TrainingClass.class);
        verify(trainingClassRepository).save(captor.capture());
        TrainingClass saved = captor.getValue();
        assertEquals(TrainingType.CENTRALIZED_TRAINING, saved.getTrainingType());
        assertNull(saved.getStore());
        assertEquals(2, saved.getParticipatingStores().size());
        assertEquals(2, saved.getTrainer().getId());
        assertEquals(1, saved.getCreatedBy().getId());
        assertEquals(TrainingClassStatus.APPROVED, saved.getStatus());
        assertEquals(1, saved.getApprovedBy().getId());
        assertNotNull(saved.getApprovedAt());
        assertEquals(CertificationStatus.NOTCERTIFIED, firstEmployee.getCertificationStatus());
        verify(notificationService, never()).notifyUsers(
                any(),
                eq("Lớp đào tạo chờ duyệt"),
                any(),
                any(),
                any(),
                any());
        verify(notificationService).notifyUsers(
                eq(List.of(trainer)),
                eq("Bạn được phân công đào tạo"),
                contains("Lớp tập trung"),
                eq(NotificationType.TRAINING_CLASS_CREATED),
                eq("TRAINING_CLASS"),
                eq(40));
    }

    @Test
    void centralizedClassRejectsEmployeeOutsideSelectedStores() {
        Store firstStore = Store.builder().id(5).storeName("Store A").build();
        Store secondStore = Store.builder().id(6).storeName("Store B").build();
        Store otherStore = Store.builder().id(7).storeName("Store C").build();
        TrainingSkill skill = TrainingSkill.builder().id(9).skillName("Barista").status(TrainingSkillStatus.ACTIVE).build();
        Employee outsider = employee(32, "Ngoai", otherStore, EmployeeStatus.ACTIVE);
        User trainer = user(2, RoleName.MANAGER, 20);
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(storeRepository.findById(5)).thenReturn(Optional.of(firstStore));
        when(storeRepository.findById(6)).thenReturn(Optional.of(secondStore));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED)).thenReturn(List.of());
        when(trainingClassRepository.findActiveByStoreId(6, TrainingClassStatus.REJECTED)).thenReturn(List.of());
        when(userRepository.findByIdWithRole(2)).thenReturn(Optional.of(trainer));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));
        when(employeeRepository.findByIdWithStore(32)).thenReturn(Optional.of(outsider));
        when(trainingClassRepository.save(any(TrainingClass.class))).thenAnswer(invocation -> {
            TrainingClass saved = invocation.getArgument(0);
            saved.setId(41);
            return saved;
        });

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createCentralizedClass(CreateCentralizedTrainingRequest.builder()
                        .className("Lớp tập trung")
                        .skillIds(List.of(9))
                        .storeIds(List.of(5, 6))
                        .employeeIds(List.of(32))
                        .trainerId(2)
                        .startDate(VietnamTime.today().plusDays(2))
                        .endDate(VietnamTime.today().plusDays(2))
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(11, 0))
                        .build(), admin));

        assertEquals("Nhân viên không thuộc các cửa hàng của lớp đào tạo.", ex.getMessage());
    }

    private User staffAccount(Integer userId, Integer employeeId) {
        User account = user(userId, RoleName.STAFF, employeeId);
        account.setUsername("staff" + employeeId);
        return account;
    }

    private TrainingClass approvedClass(Store store, LocalDate endDate) {
        return TrainingClass.builder()
                .id(10)
                .className("Lớp Barista")
                .status(TrainingClassStatus.APPROVED)
                .store(store)
                .endDate(endDate)
                .endTime(LocalTime.of(17, 0))
                .maxParticipants(20)
                .skills(Set.of(TrainingSkill.builder().id(1).skillName("Espresso").build()))
                .createdBy(user(2, RoleName.MANAGER, 20))
                .build();
    }

    private Employee employee(Integer id, String fullName, Store store, EmployeeStatus status) {
        return Employee.builder()
                .id(id)
                .fullName(fullName)
                .email(fullName + "@coffee.test")
                .phone("0900000000")
                .store(store)
                .status(status)
                .hireDate(VietnamTime.today().minusYears(1))
                .build();
    }

    private CreateTrainingClassRequest validClassRequest() {
        return classRequest(
                VietnamTime.today().plusDays(1),
                VietnamTime.today().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0));
    }

    private CreateTrainingClassRequest classRequest(LocalDate startDate,
                                                    LocalDate endDate,
                                                    LocalTime startTime,
                                                    LocalTime endTime) {
        return CreateTrainingClassRequest.builder()
                .skillIds(List.of(9))
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
