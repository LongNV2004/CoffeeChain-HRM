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
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.TrainingClassStudentResponse;
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
import com.example.coffee_hrm.repository.EmployeeOpenSkill;
import com.example.coffee_hrm.repository.EmployeePassedSkill;
import com.example.coffee_hrm.repository.EmployeeSkillTrainingFact;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(TrainingSkill.builder()
                .id(9)
                .skillName("Barista")
                .status(TrainingSkillStatus.INACTIVE)
                .build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createCentralizedClass(adminClassRequest(
                        VietnamTime.today().plusDays(1),
                        VietnamTime.today().plusDays(2),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0)), admin));
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
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));
        when(trainingSkillRepository.save(any(TrainingSkill.class))).thenAnswer(invocation -> {
            TrainingSkill skill = invocation.getArgument(0);
            skill.setId(11);
            return skill;
        });

        trainingService.createSkill(CreateTrainingSkillRequest.builder()
                .skillName("Latte Art")
                .description("Pour")
                .requirements("6 tháng kinh nghiệm")
                .build(), admin);

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

        var response = trainingService.activateSkill(11, admin);

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
                () -> trainingService.activateSkill(11, admin));
        assertEquals("Kỹ năng này đang được sử dụng.", ex.getMessage());
    }

    @Test
    void managerCannotUpdateDeactivateOrActivateSkill() {
        UpdateTrainingSkillRequest request = UpdateTrainingSkillRequest.builder()
                .skillName("Latte Art")
                .build();

        BusinessException updateDenied = assertThrows(BusinessException.class,
                () -> trainingService.updateSkill(11, request, manager));
        BusinessException deactivateDenied = assertThrows(BusinessException.class,
                () -> trainingService.deactivateSkill(11, manager));
        BusinessException activateDenied = assertThrows(BusinessException.class,
                () -> trainingService.activateSkill(11, manager));

        assertEquals("Chỉ Admin mới được sửa kỹ năng đào tạo.", updateDenied.getMessage());
        assertEquals("Chỉ Admin mới được ngừng kỹ năng đào tạo.", deactivateDenied.getMessage());
        assertEquals("Chỉ Admin mới được kích hoạt kỹ năng đào tạo.", activateDenied.getMessage());
        verify(trainingSkillRepository, never()).findById(any());
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
        LocalDateTime startAt = VietnamTime.now().minusMinutes(90).withSecond(0).withNano(0);
        LocalTime endTime = startAt.toLocalTime().plusMinutes(30);
        CreateCentralizedTrainingRequest request = endTime.isAfter(startAt.toLocalTime())
                ? adminClassRequest(startAt.toLocalDate(), startAt.toLocalDate(), startAt.toLocalTime(), endTime)
                : adminClassRequest(
                        VietnamTime.today().minusDays(1),
                        VietnamTime.today().minusDays(1),
                        LocalTime.of(10, 0),
                        LocalTime.of(12, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createCentralizedClass(request, admin));
        assertEquals(
                "Thời gian bắt đầu đã qua. Vui lòng chọn thời điểm từ hiện tại trở đi theo giờ Việt Nam.",
                ex.getMessage());
        verify(trainingClassRepository, never()).save(any());
    }

    @Test
    void createClassRejectsInvalidTimeRange() {
        CreateCentralizedTrainingRequest request = adminClassRequest(
                VietnamTime.today().plusDays(1),
                VietnamTime.today().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(7, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createCentralizedClass(request, admin));
        assertEquals("Giờ bắt đầu phải nhỏ hơn giờ kết thúc.", ex.getMessage());
        verify(trainingClassRepository, never()).save(any());
    }

    @Test
    void createClassRejectsOverlappingSchedule() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(TrainingSkill.builder()
                .id(9)
                .skillName("Barista")
                .status(TrainingSkillStatus.ACTIVE)
                .build()));
        when(storeRepository.findById(5)).thenReturn(Optional.of(store));
        when(userRepository.findByIdWithRole(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        LocalDate overlapStart = VietnamTime.today().plusDays(10);
        LocalDate overlapEnd = overlapStart.plusDays(1);
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED))
                .thenReturn(List.of(existingClass(
                        overlapStart,
                        overlapEnd,
                        LocalTime.of(7, 30),
                        LocalTime.of(11, 30))));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createCentralizedClass(adminClassRequest(
                        overlapStart,
                        overlapEnd,
                        LocalTime.of(11, 0),
                        LocalTime.of(12, 0)), admin));
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
        User trainer = user(2, RoleName.MANAGER, 20);
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(storeRepository.findById(5)).thenReturn(Optional.of(store));
        when(userRepository.findByIdWithRole(2)).thenReturn(Optional.of(trainer));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(
                employee(30, "An", store, EmployeeStatus.ACTIVE)));
        LocalDate overlapStart = VietnamTime.today().plusDays(10);
        LocalDate overlapEnd = overlapStart.plusDays(1);
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED))
                .thenReturn(List.of(existingClass(
                        overlapStart,
                        overlapEnd,
                        LocalTime.of(7, 30),
                        LocalTime.of(11, 30))));
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
                .status(TrainingClassStatus.APPROVED)
                .trainer(trainer)
                .createdBy(user(1, RoleName.ADMIN, null))
                .build()));

        trainingService.createCentralizedClass(adminClassRequest(
                overlapStart,
                overlapEnd,
                LocalTime.of(11, 31),
                LocalTime.of(12, 0)), admin);

        verify(trainingClassRepository).save(any(TrainingClass.class));
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
                contains("Lớp Barista T9"),
                eq(NotificationType.TRAINING_CLASS_CREATED),
                eq("TRAINING_CLASS"),
                eq(22));
    }

    @Test
    void adminCreatesOneStoreClassForEmployeesOfThatStore() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingSkill skill = TrainingSkill.builder()
                .id(9)
                .skillName("Barista")
                .status(TrainingSkillStatus.ACTIVE)
                .build();
        User trainer = user(2, RoleName.MANAGER, 20);
        Employee learner = employee(30, "An", store, EmployeeStatus.ACTIVE);
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(storeRepository.findById(5)).thenReturn(Optional.of(store));
        when(userRepository.findByIdWithRole(2)).thenReturn(Optional.of(trainer));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(learner));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED)).thenReturn(List.of());
        when(trainingClassRepository.save(any(TrainingClass.class))).thenAnswer(invocation -> {
            TrainingClass saved = invocation.getArgument(0);
            saved.setId(21);
            return saved;
        });
        when(trainingClassRepository.findByIdWithDetails(21)).thenReturn(Optional.of(TrainingClass.builder()
                .id(21)
                .trainingType(TrainingType.STORE_TRAINING)
                .skills(Set.of(skill))
                .store(store)
                .className("Lớp Barista T9")
                .status(TrainingClassStatus.APPROVED)
                .trainer(trainer)
                .createdBy(user(1, RoleName.ADMIN, null))
                .build()));

        trainingService.createCentralizedClass(adminClassRequest(
                VietnamTime.today().plusDays(1),
                VietnamTime.today().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0)), admin);

        ArgumentCaptor<TrainingClass> captor = ArgumentCaptor.forClass(TrainingClass.class);
        verify(trainingClassRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getStore().getId());
        assertEquals(1, captor.getValue().getParticipatingStores().size());
        assertEquals(TrainingType.STORE_TRAINING, captor.getValue().getTrainingType());
        assertEquals(2, captor.getValue().getTrainer().getId());
        assertEquals(1, captor.getValue().getCreatedBy().getId());
        assertEquals(TrainingClassStatus.APPROVED, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getSkills().size());
        assertEquals(CertificationStatus.NOTCERTIFIED, learner.getCertificationStatus());
        assertNull(captor.getValue().getLocation());
        verify(notificationService, never()).notifyUsers(
                any(),
                eq("Lớp đào tạo chờ duyệt"),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void createClassRejectsDuplicateClassNameWhileExistingClassIsStillOpen() {
        when(trainingClassRepository.existsByClassNameIgnoreCaseAndStatusNotAndEndDateGreaterThanEqual(
                "Lớp Barista T9", TrainingClassStatus.REJECTED, VietnamTime.today())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createCentralizedClass(adminClassRequest(
                        VietnamTime.today().plusDays(1),
                        VietnamTime.today().plusDays(1),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0)), admin));
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
        when(trainingClassRepository.existsByClassNameIgnoreCaseAndStatusNotAndEndDateGreaterThanEqual(
                "Lớp Barista T9", TrainingClassStatus.REJECTED, VietnamTime.today())).thenReturn(false);
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(storeRepository.findById(5)).thenReturn(Optional.of(store));
        when(userRepository.findByIdWithRole(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(
                employee(30, "An", store, EmployeeStatus.ACTIVE)));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED)).thenReturn(List.of());
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
                .status(TrainingClassStatus.APPROVED)
                .createdBy(user(1, RoleName.ADMIN, null))
                .build()));

        trainingService.createCentralizedClass(adminClassRequest(
                VietnamTime.today().plusDays(1),
                VietnamTime.today().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0)), admin);

        ArgumentCaptor<TrainingClass> captor = ArgumentCaptor.forClass(TrainingClass.class);
        verify(trainingClassRepository).save(captor.capture());
        assertEquals("Lớp Barista T9", captor.getValue().getClassName());
        assertEquals(TrainingClassStatus.APPROVED, captor.getValue().getStatus());
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
                eq(5), eq(2), eq(TrainingType.STORE_TRAINING), eq(VietnamTime.today()), any(LocalTime.class)))
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
                        .build(), admin));
        assertEquals("Tên kỹ năng không được chứa số.", ex.getMessage());
        verify(trainingSkillRepository, never()).save(any());
    }

    @Test
    void managerCannotCreateSkill() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createSkill(CreateTrainingSkillRequest.builder()
                        .skillName("Latte Art")
                        .build(), manager));
        assertEquals("Chỉ Admin mới được tạo kỹ năng đào tạo.", ex.getMessage());
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
                eq(TrainingClassStatus.APPROVED), eq(VietnamTime.today()), any(LocalTime.class),
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
                eq(TrainingClassStatus.APPROVED), eq(VietnamTime.today()), any(LocalTime.class),
                eq(9), eq(VietnamTime.today()), eq("barista"), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(6, pageable.getPageSize());
        assertEquals(0, pageable.getPageNumber());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("startTime").getDirection());
    }

    @Test
    void listActiveClassesForAdminUsesSamePageAndSortAsManagerList() {
        TrainingClass barista = TrainingClass.builder()
                .id(11)
                .className("Lớp Barista sáng")
                .skills(Set.of(TrainingSkill.builder().id(9).skillName("Barista").build()))
                .store(Store.builder().id(5).storeName("Store A").build())
                .startDate(VietnamTime.today())
                .endDate(VietnamTime.today().plusDays(3))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .status(TrainingClassStatus.APPROVED)
                .createdBy(user(1, RoleName.ADMIN, null))
                .build();
        when(trainingClassRepository.searchApprovedActiveForAdmin(
                eq(TrainingClassStatus.APPROVED), eq(VietnamTime.today()), any(LocalTime.class),
                eq(9), eq(VietnamTime.today()), eq("barista"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(barista)));

        var page = trainingService.listActiveClassesForAdmin(
                admin, 9, VietnamTime.today(), "barista", "desc", 1);

        assertEquals(1, page.getContent().size());
        assertEquals(11, page.getContent().getFirst().getId());
        assertFalse(page.getContent().getFirst().isEvaluable());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(trainingClassRepository).searchApprovedActiveForAdmin(
                eq(TrainingClassStatus.APPROVED), eq(VietnamTime.today()), any(LocalTime.class),
                eq(9), eq(VietnamTime.today()), eq("barista"), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(6, pageable.getPageSize());
        assertEquals(0, pageable.getPageNumber());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("startTime").getDirection());
    }

    @Test
    void adminClassDetailIsReadOnly() {
        when(trainingClassRepository.findByIdWithDetails(8)).thenReturn(Optional.of(TrainingClass.builder()
                .id(8)
                .className("Lớp đang chạy")
                .status(TrainingClassStatus.APPROVED)
                .skills(Set.of(TrainingSkill.builder().id(1).skillName("Espresso").build()))
                .createdBy(user(1, RoleName.ADMIN, null))
                .build()));
        when(trainingClassEnrollmentRepository.findByClassIdWithEmployee(8)).thenReturn(List.of());

        var detail = trainingService.getApprovedClassDetailForAdmin(8, admin);

        assertEquals(8, detail.getId());
        assertFalse(detail.isEvaluable());
    }

    @Test
    void adminClassDetailHidesClassesThatAreNotApproved() {
        when(trainingClassRepository.findByIdWithDetails(7)).thenReturn(Optional.of(TrainingClass.builder()
                .id(7)
                .className("Chờ duyệt")
                .status(TrainingClassStatus.PENDING_APPROVAL)
                .skills(Set.of(TrainingSkill.builder().id(1).skillName("Espresso").build()))
                .createdBy(user(1, RoleName.ADMIN, null))
                .build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.getApprovedClassDetailForAdmin(7, admin));
        assertEquals("Không tìm thấy lớp đào tạo.", ex.getMessage());
    }

    @Test
    void managerCannotOpenAdminActiveClassList() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.listActiveClassesForAdmin(manager, null, null, null, "asc", 1));
        assertEquals("Chỉ Admin mới được xem danh sách lớp đang hoạt động.", ex.getMessage());
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
                eq("Bạn đã được thêm vào lớp đào tạo Lớp Barista. Kết quả đánh giá sẽ có sau khi lớp kết thúc."),
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
    void employeeListCarriesPassedSkillIdsWithoutTreatingAnotherSkillAsCertified() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee barista = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        Employee otherSkill = employee(31, "Lê Văn C", store, EmployeeStatus.ACTIVE);
        Employee none = employee(32, "Trần Văn B", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(employeeRepository.findByStore_IdAndStatus(5, EmployeeStatus.ACTIVE))
                .thenReturn(List.of(none, barista, otherSkill));
        when(trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                any(), eq(TrainingResult.PASS), eq(TrainingClassStatus.APPROVED)))
                .thenReturn(List.of(
                        new EmployeePassedSkill(30, 1),
                        new EmployeePassedSkill(31, 2)));

        var employees = trainingService.listEmployeesForNewStoreClass(manager);

        var nguyen = employees.stream().filter(item -> item.getEmployeeId().equals(30)).findFirst().orElseThrow();
        var le = employees.stream().filter(item -> item.getEmployeeId().equals(31)).findFirst().orElseThrow();
        var tran = employees.stream().filter(item -> item.getEmployeeId().equals(32)).findFirst().orElseThrow();
        assertEquals(List.of(1), nguyen.getCertifiedSkillIds());
        assertEquals("1", nguyen.getCertifiedSkillIdsCsv());
        assertEquals("", nguyen.getStudyingSkillIdsCsv());
        assertNull(nguyen.getStudyingSameSkill());
        assertNull(nguyen.getHasCertificate());
        assertEquals(List.of(2), le.getCertifiedSkillIds());
        assertTrue(tran.getCertifiedSkillIds().isEmpty());
        assertEquals("", tran.getCertifiedSkillIdsCsv());
    }

    @Test
    void availableEmployeesAreCertifiedOnlyWhenTheyPassedEveryClassSkill() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass trainingClass = approvedClass(store, VietnamTime.today().plusDays(1));
        trainingClass.setSkills(Set.of(
                TrainingSkill.builder().id(1).skillName("Pha Chế").build(),
                TrainingSkill.builder().id(2).skillName("Phục Vụ").build()));
        Employee both = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        Employee brewOnly = employee(31, "Lê Văn C", store, EmployeeStatus.ACTIVE);
        Employee none = employee(32, "Trần Văn B", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.findEmployeeIdsByClassId(10)).thenReturn(List.of());
        when(employeeRepository.findByStore_IdAndStatus(5, EmployeeStatus.ACTIVE))
                .thenReturn(List.of(none, both, brewOnly));
        when(trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                any(), eq(TrainingResult.PASS), eq(TrainingClassStatus.APPROVED)))
                .thenReturn(List.of(
                        new EmployeePassedSkill(30, 1),
                        new EmployeePassedSkill(30, 2),
                        new EmployeePassedSkill(31, 1)));

        var available = trainingService.listAvailableEmployeesForClass(10, manager);

        assertTrue(employeeById(available, 30).getHasCertificate());
        assertFalse(employeeById(available, 30).getStudyingSameSkill());
        assertFalse(employeeById(available, 31).getHasCertificate());
        assertFalse(employeeById(available, 31).getStudyingSameSkill());
        assertFalse(employeeById(available, 32).getHasCertificate());
        assertFalse(employeeById(available, 32).getStudyingSameSkill());
        assertEquals(List.of(1), employeeById(available, 31).getCertifiedSkillIds());
    }

    @Test
    void employeePickersExcludeManagerAccounts() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee managerEmployee = employee(20, "Quản lý", store, EmployeeStatus.ACTIVE);
        Employee staff = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(userRepository.findEmployeeIdsByRoleName(RoleName.MANAGER)).thenReturn(List.of(20));
        when(employeeRepository.findByStore_IdAndStatus(5, EmployeeStatus.ACTIVE))
                .thenReturn(List.of(managerEmployee, staff));
        when(trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                any(), eq(TrainingResult.PASS), eq(TrainingClassStatus.APPROVED)))
                .thenReturn(List.of());

        var forNewClass = trainingService.listEmployeesForNewStoreClass(manager);

        assertEquals(List.of(30), forNewClass.stream().map(TrainingClassStudentResponse::getEmployeeId).toList());

        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));
        when(trainingClassEnrollmentRepository.findEmployeeIdsByClassId(10)).thenReturn(List.of());

        var available = trainingService.listAvailableEmployeesForClass(10, manager);

        assertEquals(List.of(30), available.stream().map(TrainingClassStudentResponse::getEmployeeId).toList());
    }

    @Test
    void centralizedEmployeePickerExcludesManagersWhileTrainerPickerKeepsThem() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee managerEmployee = employee(20, "Quản lý", store, EmployeeStatus.ACTIVE);
        Employee staff = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        User trainer = user(2, RoleName.MANAGER, 20);
        when(userRepository.findEmployeeIdsByRoleName(RoleName.MANAGER)).thenReturn(List.of(20));
        when(employeeRepository.findByStatusWithStore(EmployeeStatus.ACTIVE))
                .thenReturn(List.of(managerEmployee, staff));
        when(trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                any(), eq(TrainingResult.PASS), eq(TrainingClassStatus.APPROVED)))
                .thenReturn(List.of());
        when(userRepository.findActiveByRoleName(RoleName.MANAGER)).thenReturn(List.of(trainer));

        var employees = trainingService.listActiveEmployees(admin);
        var trainers = trainingService.listTrainerCandidates(admin);

        assertEquals(List.of(30), employees.stream().map(item -> item.getEmployeeId()).toList());
        assertEquals(List.of(2), trainers.stream().map(item -> item.getUserId()).toList());
    }

    @Test
    void addStudentsRejectsManagerAccount() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(0L);
        when(userRepository.findEmployeeIdsByRoleName(RoleName.MANAGER)).thenReturn(List.of(20));

        BusinessException ex = assertThrows(BusinessException.class, () -> trainingService.addStudentsToClass(
                10, AddTrainingClassStudentsRequest.builder().employeeIds(List.of(20)).build(), manager));

        assertEquals(
                "Không thể thêm tài khoản Manager vào lớp. Manager chỉ được chọn làm người đào tạo khi Admin tạo lớp.",
                ex.getMessage());
        verify(trainingClassEnrollmentRepository, never()).save(any());
        verify(employeeRepository, never()).findByIdWithStore(any());
    }

    @Test
    void addStudentsAllowsEmployeeAlreadyCertifiedForEveryClassSkill() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee employee = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(0L);
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
        when(trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(10, 30)).thenReturn(false);
        when(userRepository.findByEmployee_Id(30)).thenReturn(Optional.empty());

        int added = trainingService.addStudentsToClass(10, AddTrainingClassStudentsRequest.builder()
                .employeeIds(List.of(30))
                .build(), manager);

        assertEquals(1, added);
        verify(trainingClassEnrollmentRepository).save(any(TrainingClassEnrollment.class));
    }

    @Test
    void addStudentsAllowsEmployeeWhoStillMissesAClassSkill() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass trainingClass = approvedClass(store, VietnamTime.today().plusDays(1));
        trainingClass.setSkills(Set.of(
                TrainingSkill.builder().id(1).skillName("Pha Chế").build(),
                TrainingSkill.builder().id(2).skillName("Phục Vụ").build()));
        Employee employee = employee(31, "Lê Văn C", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(0L);
        when(employeeRepository.findByIdWithStore(31)).thenReturn(Optional.of(employee));
        when(trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(10, 31)).thenReturn(false);
        when(userRepository.findByEmployee_Id(31)).thenReturn(Optional.empty());

        int added = trainingService.addStudentsToClass(10, AddTrainingClassStudentsRequest.builder()
                .employeeIds(List.of(31))
                .build(), manager);

        assertEquals(1, added);
        verify(trainingClassEnrollmentRepository).save(any(TrainingClassEnrollment.class));
    }

    @Test
    void addStudentsRejectsEmployeeStudyingTheSameSkillInAnotherOpenClass() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee employee = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(0L);
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
        when(trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(10, 30)).thenReturn(false);
        when(trainingClassEnrollmentRepository.findOpenSkillEnrollments(
                any(), eq(TrainingClassStatus.REJECTED), eq(10), any(), any()))
                .thenReturn(List.of(new EmployeeOpenSkill(30, 1, "Espresso", "Lớp tập trung")));

        BusinessException ex = assertThrows(BusinessException.class, () -> trainingService.addStudentsToClass(
                10, AddTrainingClassStudentsRequest.builder().employeeIds(List.of(30)).build(), manager));

        assertEquals("Nhân viên Nguyễn Văn A đang học kỹ năng Espresso ở lớp \"Lớp tập trung\" còn hiệu lực, "
                + "nên không thể tham gia lớp khác cùng kỹ năng.", ex.getMessage());
        verify(trainingClassEnrollmentRepository, never()).save(any());
    }

    @Test
    void addStudentsRejectsWhenOnlyOneOfSeveralClassSkillsIsAlreadyBeingStudied() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass trainingClass = approvedClass(store, VietnamTime.today().plusDays(1));
        trainingClass.setSkills(Set.of(
                TrainingSkill.builder().id(1).skillName("Pha Chế").build(),
                TrainingSkill.builder().id(2).skillName("Phục Vụ").build()));
        Employee employee = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(0L);
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
        when(trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(10, 30)).thenReturn(false);
        when(trainingClassEnrollmentRepository.findOpenSkillEnrollments(
                any(), eq(TrainingClassStatus.REJECTED), eq(10), any(), any()))
                .thenReturn(List.of(new EmployeeOpenSkill(30, 2, "Phục Vụ", "Lớp tại cửa hàng")));

        BusinessException ex = assertThrows(BusinessException.class, () -> trainingService.addStudentsToClass(
                10, AddTrainingClassStudentsRequest.builder().employeeIds(List.of(30)).build(), manager));

        assertEquals("Nhân viên Nguyễn Văn A đang học kỹ năng Phục Vụ ở lớp \"Lớp tại cửa hàng\" còn hiệu lực, "
                + "nên không thể tham gia lớp khác cùng kỹ năng.", ex.getMessage());
        verify(trainingClassEnrollmentRepository, never()).save(any());
    }

    @Test
    void addStudentsAllowsEmployeeStudyingADifferentSkill() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee employee = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));
        when(trainingClassEnrollmentRepository.countByTrainingClass_Id(10)).thenReturn(0L);
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
        when(trainingClassEnrollmentRepository.existsByTrainingClass_IdAndEmployee_Id(10, 30)).thenReturn(false);
        when(trainingClassEnrollmentRepository.findOpenSkillEnrollments(
                any(), eq(TrainingClassStatus.REJECTED), eq(10), any(), any()))
                .thenReturn(List.of(new EmployeeOpenSkill(30, 9, "Pha chế", "Lớp khác")));
        when(userRepository.findByEmployee_Id(30)).thenReturn(Optional.empty());

        int added = trainingService.addStudentsToClass(10, AddTrainingClassStudentsRequest.builder()
                .employeeIds(List.of(30))
                .build(), manager);

        assertEquals(1, added);
        verify(trainingClassEnrollmentRepository).save(any(TrainingClassEnrollment.class));
    }

    @Test
    void availableEmployeesAreBlockedOnlyWhenAnOpenClassSharesASkill() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee sameSkill = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        Employee otherSkill = employee(31, "Lê Văn C", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10))
                .thenReturn(Optional.of(approvedClass(store, VietnamTime.today().plusDays(1))));
        when(trainingClassEnrollmentRepository.findEmployeeIdsByClassId(10)).thenReturn(List.of());
        when(employeeRepository.findByStore_IdAndStatus(5, EmployeeStatus.ACTIVE))
                .thenReturn(List.of(sameSkill, otherSkill));
        when(trainingClassEnrollmentRepository.findOpenSkillEnrollments(
                any(), eq(TrainingClassStatus.REJECTED), eq(10), any(), any()))
                .thenReturn(List.of(
                        new EmployeeOpenSkill(30, 1, "Espresso", "Lớp tập trung"),
                        new EmployeeOpenSkill(31, 4, "Pha chế", "Lớp khác")));

        var available = trainingService.listAvailableEmployeesForClass(10, manager);

        assertTrue(employeeById(available, 30).getStudyingSameSkill());
        assertEquals(List.of(1), employeeById(available, 30).getStudyingSkillIds());
        assertFalse(employeeById(available, 30).getHasCertificate());
        assertFalse(employeeById(available, 31).getStudyingSameSkill());
        assertEquals(List.of(4), employeeById(available, 31).getStudyingSkillIds());
    }

    @Test
    void newClassPickerShowsSkillsBeingStudiedBeforeAClassIsChosen() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee employee = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(employeeRepository.findByStore_IdAndStatus(5, EmployeeStatus.ACTIVE)).thenReturn(List.of(employee));
        when(trainingClassEnrollmentRepository.findOpenSkillEnrollments(
                any(), eq(TrainingClassStatus.REJECTED), eq(0), any(), any()))
                .thenReturn(List.of(new EmployeeOpenSkill(30, 1, "Espresso", "Lớp tập trung")));

        var employees = trainingService.listEmployeesForNewStoreClass(manager);

        var option = employees.get(0);
        assertEquals(List.of(1), option.getStudyingSkillIds());
        assertEquals("1", option.getStudyingSkillIdsCsv());
        assertNull(option.getStudyingSameSkill());
    }

    @Test
    void classIsEndedOnceCurrentTimeReachesEndTime() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass finishedToday = approvedClass(store, VietnamTime.today());
        finishedToday.setEndTime(LocalTime.MIN);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(finishedToday));
        when(trainingClassEnrollmentRepository.findByClassIdWithEmployee(10)).thenReturn(List.of());

        assertTrue(trainingService.getApprovedClassDetailForManager(10, manager).isEnded());

        TrainingClass stillRunning = approvedClass(store, VietnamTime.today().plusDays(1));
        stillRunning.setEndTime(LocalTime.NOON);
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(stillRunning));

        assertFalse(trainingService.getApprovedClassDetailForManager(10, manager).isEnded());
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
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any(), any());

        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        TrainingClassEnrollment enrollment = TrainingClassEnrollment.builder()
                .trainingClass(ended)
                .employee(employee)
                .build();
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(ended));
        when(trainingClassEnrollmentRepository.findByClassAndEmployee(10, 30)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        User staff = staffAccount(40, 30);
        when(userRepository.findByEmployee_Id(30)).thenReturn(Optional.of(staff));

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
        verify(notificationService).notifyUsers(
                eq(List.of(staff)),
                eq("Bạn đã được đánh giá lớp đào tạo"),
                eq("Bạn đã được đánh giá lớp \"Lớp Barista\". Kết quả: Không đạt. Ghi chú: Cần luyện thêm."),
                eq(NotificationType.TRAINING_CLASS_EVALUATED),
                eq("TRAINING_CLASS"),
                eq(10));
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
        User staff = staffAccount(40, 30);
        when(userRepository.findByEmployee_Id(30)).thenReturn(Optional.of(staff));
        trainingService.evaluateStudent(10, 30, EvaluateTrainingStudentRequest.builder()
                .result(TrainingResult.NOT_PASS)
                .note("Cập nhật")
                .updating(true)
                .build(), manager);
        assertEquals(TrainingResult.NOT_PASS, enrollment.getResult());
        assertEquals("Cập nhật", enrollment.getEvaluationNote());
        verify(notificationService).notifyUsers(
                eq(List.of(staff)),
                eq("Kết quả đánh giá đã được cập nhật"),
                eq("Kết quả đánh giá lớp \"Lớp Barista\" đã được cập nhật thành Không đạt. Ghi chú: Cập nhật."),
                eq(NotificationType.TRAINING_CLASS_EVALUATED),
                eq("TRAINING_CLASS"),
                eq(10));
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
        assertEquals("Sắp diễn ra", classes.get(0).getParticipationStatus());
        assertEquals("Đã ghi danh", classes.get(0).getEnrollmentStatus());
        assertEquals("Chưa hoàn thành", classes.get(0).getCompletionStatus());
        assertEquals("Chưa đánh giá", classes.get(0).getResultLabel());
        assertNull(classes.get(0).getResult());
        assertNull(classes.get(0).getResultCode());
        assertEquals("Trần Văn Tuấn", classes.get(0).getEmployeeName());
        assertEquals("Cô Lan", classes.get(0).getTrainer());
        assertEquals("Lớp cũ", classes.get(1).getClassName());
        assertEquals("Đã kết thúc", classes.get(1).getParticipationStatus());
        assertEquals("Đã hoàn thành", classes.get(1).getCompletionStatus());
        assertEquals(TrainingResult.NOT_PASS, classes.get(1).getResult());
        assertEquals("Không đạt", classes.get(1).getResultLabel());
        assertEquals("NOT PASS", classes.get(1).getResultCode());
        assertEquals(List.of("Espresso"), classes.get(1).getNotPassedSkillNames());
        assertEquals(List.of(), classes.get(1).getPassedSkillNames());
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
        var evaluatedAt = VietnamTime.now();
        Employee learner = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        ended.setLocation("Quầy bar");
        ended.setNotes("Luyện espresso cơ bản");
        when(trainingClassEnrollmentRepository.findOwnedByClassAndEmployee(10, 30)).thenReturn(Optional.of(
                TrainingClassEnrollment.builder()
                        .trainingClass(ended)
                        .employee(learner)
                        .result(TrainingResult.PASS)
                        .evaluationNote("Làm tốt")
                        .evaluatedAt(evaluatedAt)
                        .build()));

        var detail = trainingService.getClassForEmployee(10, staff);

        assertEquals("Pha Chế 2", detail.getClassName());
        assertEquals("Espresso", detail.getSkillName());
        assertEquals("Trần Văn Tuấn", detail.getEmployeeName());
        assertEquals("Đã ghi danh", detail.getEnrollmentStatus());
        assertEquals("Đã hoàn thành", detail.getCompletionStatus());
        assertEquals("Quầy bar", detail.getLocation());
        assertEquals("Store A", detail.getStoreName());
        assertEquals("Luyện espresso cơ bản", detail.getNotes());
        assertEquals(TrainingResult.PASS, detail.getResult());
        assertEquals("Đạt", detail.getResultLabel());
        assertEquals("PASS", detail.getResultCode());
        assertEquals(List.of("Espresso"), detail.getPassedSkillNames());
        assertEquals(List.of(), detail.getNotPassedSkillNames());
        assertEquals(evaluatedAt, detail.getEvaluatedAt());
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
    void notPassKeepsEmployeeCertifiedWhenAnotherSkillWasAlreadyPassed() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Employee learner = employee(30, "Trần Văn Tuấn", store, EmployeeStatus.ACTIVE);
        learner.setCertificationStatus(CertificationStatus.CERTIFIED);
        TrainingClass ended = approvedClass(store, VietnamTime.today().minusDays(1));
        TrainingClassEnrollment enrollment = TrainingClassEnrollment.builder()
                .trainingClass(ended)
                .employee(learner)
                .build();
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(ended));
        when(trainingClassEnrollmentRepository.findByClassAndEmployee(10, 30)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        when(trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                any(), eq(TrainingResult.PASS), eq(TrainingClassStatus.APPROVED)))
                .thenReturn(List.of(new EmployeePassedSkill(30, 2)));

        trainingService.evaluateStudent(10, 30, EvaluateTrainingStudentRequest.builder()
                .result(TrainingResult.NOT_PASS)
                .updating(false)
                .build(), manager);

        assertEquals(TrainingResult.NOT_PASS, enrollment.getResult());
        assertEquals(CertificationStatus.CERTIFIED, learner.getCertificationStatus());
    }

    @Test
    void certificationStaysOnPassedSkillWhenRetakeIsNotPassAndDoesNotSpread() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        TrainingClass trainingClass = approvedClass(store, VietnamTime.today().plusDays(1));
        trainingClass.setSkills(Set.of(
                TrainingSkill.builder().id(1).skillName("Pha chế").build(),
                TrainingSkill.builder().id(2).skillName("Phục vụ").build()));
        Employee barista = employee(30, "Nguyễn Văn A", store, EmployeeStatus.ACTIVE);
        when(storeRepository.findByManager_Id(20)).thenReturn(Optional.of(store));
        when(trainingClassRepository.findByIdWithDetails(10)).thenReturn(Optional.of(trainingClass));
        when(trainingClassEnrollmentRepository.findEmployeeIdsByClassId(10)).thenReturn(List.of());
        when(employeeRepository.findByStore_IdAndStatus(5, EmployeeStatus.ACTIVE)).thenReturn(List.of(barista));
        when(trainingClassEnrollmentRepository.findPassedSkillsByEmployees(
                any(), eq(TrainingResult.PASS), eq(TrainingClassStatus.APPROVED)))
                .thenReturn(List.of(new EmployeePassedSkill(30, 1)));
        when(trainingClassEnrollmentRepository.findSkillTrainingFacts(any(), eq(TrainingClassStatus.REJECTED)))
                .thenReturn(List.of(
                        fact(30, 1, "Pha chế", TrainingResult.PASS, LocalDateTime.of(2026, 10, 1, 9, 0)),
                        fact(30, 1, "Pha chế", TrainingResult.NOT_PASS, LocalDateTime.of(2026, 10, 5, 9, 0))));

        var available = trainingService.listAvailableEmployeesForClass(10, manager);
        var employee = available.getFirst();
        EmployeeSkillStatusLine brew = employee.getSkillStatuses().stream()
                .filter(line -> Integer.valueOf(1).equals(line.getSkillId()))
                .findFirst()
                .orElseThrow();
        EmployeeSkillStatusLine service = employee.getSkillStatuses().stream()
                .filter(line -> Integer.valueOf(2).equals(line.getSkillId()))
                .findFirst()
                .orElseThrow();

        assertEquals(CertificationStatus.CERTIFIED, brew.getCertificationStatus());
        assertEquals("CERTIFIED - Pha chế", brew.getCertificationLabel());
        assertEquals("01/10/2026", brew.getCertifiedDateLabel());
        assertEquals("NOT PASS", brew.getLatestResultLabel());
        assertEquals("05/10/2026", brew.getLatestDateLabel());
        assertEquals(CertificationStatus.NOTCERTIFIED, service.getCertificationStatus());
        assertEquals("NOTCERTIFIED - Phục vụ", service.getCertificationLabel());
        assertNull(service.getCertifiedDateLabel());
        assertEquals("Có thể đăng ký", employee.getEligibilityLabel());
        assertFalse(employee.getHasCertificate());
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
    void onlyAdminCanCreateTrainingClass() {
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
        assertEquals("Chỉ Admin mới được tạo lớp đào tạo.", managerDenied.getMessage());

        BusinessException managerLegacyDenied = assertThrows(BusinessException.class,
                () -> trainingService.createClass(validClassRequest(), manager));
        assertEquals("Chỉ Admin mới được tạo lớp đào tạo.", managerLegacyDenied.getMessage());

        AuthenticatedUser staff = AuthenticatedUser.from(user(8, RoleName.STAFF, 30));
        BusinessException staffDenied = assertThrows(BusinessException.class,
                () -> trainingService.createClass(validClassRequest(), staff));
        assertEquals("Chỉ Admin mới được tạo lớp đào tạo.", staffDenied.getMessage());
        verify(trainingClassRepository, never()).save(any());
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any(), any());
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

    @Test
    void oneStoreClassRejectsEmployeeOutsideThatStore() {
        Store store = Store.builder().id(5).storeName("Store A").build();
        Store otherStore = Store.builder().id(7).storeName("Store C").build();
        TrainingSkill skill = TrainingSkill.builder().id(9).skillName("Barista").status(TrainingSkillStatus.ACTIVE).build();
        Employee outsider = employee(32, "Ngoai", otherStore, EmployeeStatus.ACTIVE);
        when(trainingSkillRepository.findById(9)).thenReturn(Optional.of(skill));
        when(storeRepository.findById(5)).thenReturn(Optional.of(store));
        when(userRepository.findByIdWithRole(2)).thenReturn(Optional.of(user(2, RoleName.MANAGER, 20)));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, RoleName.ADMIN, null)));
        when(employeeRepository.findByIdWithStore(32)).thenReturn(Optional.of(outsider));
        when(trainingClassRepository.findActiveByStoreId(5, TrainingClassStatus.REJECTED)).thenReturn(List.of());
        when(trainingClassRepository.save(any(TrainingClass.class))).thenAnswer(invocation -> {
            TrainingClass saved = invocation.getArgument(0);
            saved.setId(42);
            return saved;
        });

        CreateCentralizedTrainingRequest request = adminClassRequest(
                VietnamTime.today().plusDays(2),
                VietnamTime.today().plusDays(2),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0));
        request.setEmployeeIds(List.of(32));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trainingService.createCentralizedClass(request, admin));

        assertEquals("Nhân viên không thuộc cửa hàng của bạn.", ex.getMessage());
    }

    private EmployeeSkillTrainingFact fact(Integer employeeId,
                                           Integer skillId,
                                           String skillName,
                                           TrainingResult result,
                                           LocalDateTime evaluatedAt) {
        return new EmployeeSkillTrainingFact(
                employeeId,
                skillId,
                skillName,
                result,
                evaluatedAt,
                evaluatedAt.toLocalDate().minusDays(1),
                evaluatedAt.toLocalDate(),
                LocalTime.of(17, 0),
                TrainingClassStatus.APPROVED);
    }

    private TrainingClassStudentResponse employeeById(List<TrainingClassStudentResponse> employees, Integer employeeId) {
        return employees.stream()
                .filter(item -> employeeId.equals(item.getEmployeeId()))
                .findFirst()
                .orElseThrow();
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

    private CreateCentralizedTrainingRequest adminClassRequest(LocalDate startDate,
                                                                LocalDate endDate,
                                                                LocalTime startTime,
                                                                LocalTime endTime) {
        return CreateCentralizedTrainingRequest.builder()
                .skillIds(List.of(9))
                .storeIds(List.of(5))
                .employeeIds(List.of(30))
                .trainerId(2)
                .className("Lớp Barista T9")
                .startDate(startDate)
                .endDate(endDate)
                .startTime(startTime)
                .endTime(endTime)
                .build();
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
