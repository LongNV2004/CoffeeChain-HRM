package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.PayrollStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.MonthlyAttendanceReport;
import com.example.coffee_hrm.entity.MonthlyAttendanceReportVersion;
import com.example.coffee_hrm.entity.MonthlyPayroll;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportVersionRepository;
import com.example.coffee_hrm.repository.MonthlyPayrollRepository;
import com.example.coffee_hrm.repository.PayrollPaymentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingAttendanceRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.PayrollService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceReportServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private TrainingAttendanceRepository trainingAttendanceRepository;
    @Mock
    private MonthlyAttendanceReportRepository reportRepository;
    @Mock
    private MonthlyAttendanceReportVersionRepository versionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private Clock clock;
    @Mock
    private PayrollService payrollService;
    @Mock
    private MonthlyPayrollRepository payrollRepository;
    @Mock
    private PayrollPaymentRepository paymentRepository;

    private AttendanceReportFileStore reportFiles;
    private AttendanceReportServiceImpl service;
    private Store store;
    private Store otherStore;
    private AuthenticatedUser manager;
    private AuthenticatedUser staff;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() throws Exception {
        reportFiles = new AttendanceReportFileStore(Files.createTempDirectory("attendance-reports").toString());
        store = Store.builder().id(1).storeName("Cầu Giấy").build();
        otherStore = Store.builder().id(2).storeName("Hoàn Kiếm").build();
        manager = user(9, "manager1", "Nguyễn Văn Quản Lý", RoleName.MANAGER, store);
        staff = user(3, "staff1", "Lan", RoleName.STAFF, store);
        admin = user(1, "admin", "Admin", RoleName.ADMIN, null);
        service = new AttendanceReportServiceImpl(
                storeRepository,
                employeeRepository,
                attendanceRepository,
                trainingAttendanceRepository,
                reportRepository,
                versionRepository,
                userRepository,
                reportFiles,
                notificationService,
                clock,
                payrollService,
                payrollRepository,
                paymentRepository);
        lenient().when(clock.getZone()).thenReturn(VietnamTime.ZONE);
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-10-10T02:00:00Z"));
    }

    @Test
    void submitsReportForManagedStoreAndPeriodOnly() {
        stubStoreData();
        when(userRepository.getReferenceById(9)).thenReturn(User.builder().id(9).username("manager1").build());
        when(reportRepository.findCurrent(1, 2026, 10)).thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(manager, 2026, 10);

        verify(attendanceRepository).findForMonthlyReport(eq(1), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)));
        verify(trainingAttendanceRepository).findForMonthlyReport(eq(1), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)));
        verify(attendanceRepository, never()).findForMonthlyReport(eq(2), any(), any());
        verify(attendanceRepository, never()).save(any());
        verify(trainingAttendanceRepository, never()).save(any());
        ArgumentCaptor<MonthlyAttendanceReport> saved = ArgumentCaptor.forClass(MonthlyAttendanceReport.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        assertEquals(10, saved.getValue().getReportMonth());
        assertEquals(1, saved.getValue().getVersionNumber());
        byte[] excel = reportFiles.read(1, 2026, 10, 1);
        assertNotNull(excel);
        String sheet = new String(excel, StandardCharsets.UTF_8);
        assertTrue(sheet.contains("Cầu Giấy"));
        assertTrue(sheet.contains("10/2026"));
        assertTrue(sheet.contains("Nguyễn Văn Quản Lý"));
    }

    @Test
    void submitKeepsASnapshotOfTheSentVersion() {
        stubStoreData();
        when(userRepository.getReferenceById(9)).thenReturn(User.builder().id(9).username("manager1").build());
        AtomicReference<MonthlyAttendanceReport> stored = new AtomicReference<>();
        when(reportRepository.findCurrent(1, 2026, 10)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MonthlyAttendanceReport report = invocation.getArgument(0);
            report.setId(15);
            stored.set(report);
            return report;
        });
        when(versionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(manager, 2026, 10);

        // Bản lưu của lần gửi (dùng cho trang "Các lần gửi"); không còn cho tải xuống.
        byte[] snapshot = reportFiles.read(1, 2026, 10, 1);
        assertNotNull(snapshot);
        assertTrue(new String(snapshot, StandardCharsets.UTF_8).contains("Cầu Giấy"));
    }

    @Test
    void emptyStoreStillSavesSubmittedReport() {
        stubStoreData();
        when(userRepository.getReferenceById(9)).thenReturn(User.builder().id(9).username("manager1").build());
        when(reportRepository.findCurrent(1, 2026, 3)).thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(manager, 2026, 3);

        ArgumentCaptor<MonthlyAttendanceReport> saved = ArgumentCaptor.forClass(MonthlyAttendanceReport.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        assertEquals(3, saved.getValue().getReportMonth());
        assertEquals(0, saved.getValue().getEmployeeCount());
    }

    @Test
    void resubmitAfterAdminReturnsIncreasesVersionAndSendTime() {
        stubStoreData();
        when(userRepository.getReferenceById(9)).thenReturn(User.builder().id(9).username("manager1").build());
        AtomicReference<MonthlyAttendanceReport> stored = new AtomicReference<>();
        when(reportRepository.findCurrent(1, 2026, 10)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MonthlyAttendanceReport report = invocation.getArgument(0);
            report.setId(15);
            stored.set(report);
            return report;
        });
        when(clock.instant()).thenReturn(
                Instant.parse("2026-10-10T02:00:00Z"),
                Instant.parse("2026-10-10T03:00:00Z"));

        service.submit(manager, 2026, 10);
        // Admin trả lại báo cáo, Manager mới được gửi lại.
        stored.get().setStatus(AttendanceReportStatus.REVISION);
        service.submit(manager, 2026, 10);

        ArgumentCaptor<MonthlyAttendanceReportVersion> versions =
                ArgumentCaptor.forClass(MonthlyAttendanceReportVersion.class);
        verify(versionRepository, times(2)).saveAndFlush(versions.capture());
        assertEquals(1, versions.getAllValues().get(0).getVersionNumber());
        assertEquals(LocalDateTime.of(2026, 10, 10, 9, 0), versions.getAllValues().get(0).getSubmittedAt());
        assertEquals(2, versions.getAllValues().get(1).getVersionNumber());
        assertEquals(LocalDateTime.of(2026, 10, 10, 10, 0), versions.getAllValues().get(1).getSubmittedAt());
        assertEquals(2, stored.get().getVersionNumber());
        verify(attendanceRepository, never()).save(any());
        verify(versionRepository, never()).delete(any());
    }

    @Test
    void staffCannotCreateOrSubmit() {
        assertThrows(BusinessException.class, () -> service.submit(staff, 2026, 10));
        assertThrows(BusinessException.class, () -> service.adminDetail(staff, 99, null, null));
        verify(storeRepository, never()).findByManager_Id(any());
        verify(reportRepository, never()).findByIdDetailed(any());
    }

    @Test
    void managerCannotOpenAdminReport() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.adminDetail(manager, 99, null, null));

        assertTrue(error.getMessage().contains("quyền"));
        verify(reportRepository, never()).findByIdDetailed(any());
    }

    @Test
    void adminSeesSubmittedAndUnsentStores() {
        Employee managerEmployee = Employee.builder().id(9).fullName("Nguyễn Văn Quản Lý").store(store).build();
        store.setManager(managerEmployee);
        MonthlyAttendanceReport sent = MonthlyAttendanceReport.builder()
                .id(4)
                .store(store)
                .reportYear(2026)
                .reportMonth(10)
                .versionNumber(2)
                .submittedAt(LocalDateTime.of(2026, 10, 9, 8, 0))
                .submittedByName("Nguyễn Văn Quản Lý")
                .build();
        when(storeRepository.findAllWithManager()).thenReturn(List.of(otherStore, store));
        when(reportRepository.findByPeriod(2026, 10)).thenReturn(List.of(sent));
        when(attendanceRepository.findMonthlyReportChanges(any(), any())).thenReturn(List.of());
        when(trainingAttendanceRepository.findMonthlyReportChanges(any(), any())).thenReturn(List.of());

        AdminAttendanceReportPage page = service.adminPage(admin, 2026, 10, null, "all");

        assertEquals(2, page.getTotalStores());
        assertEquals(1, page.getSubmittedStores());
        assertEquals(1, page.getUnsentStores());
        AdminAttendanceReportPage.Row unsent = page.getRows().stream()
                .filter(row -> row.getStoreId() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals("Chưa gửi", unsent.getStatusLabel());
        assertEquals("", unsent.getSenderName());
        assertNull(unsent.getSubmittedAt());
        assertNull(unsent.getVersionNumber());
        AdminAttendanceReportPage.Row submitted = page.getRows().stream()
                .filter(row -> row.getStoreId() == 1)
                .findFirst()
                .orElseThrow();
        assertEquals("Chờ Admin duyệt", submitted.getStatusLabel());
        assertEquals("status-waiting", submitted.getStatusCss());
        assertEquals(2, submitted.getVersionNumber());
    }

    @Test
    void managerPreviewShowsOwnStoreAndRejectsOutsideEmployee() {
        stubStoreData();
        when(reportRepository.findCurrent(1, 2026, 10)).thenReturn(Optional.empty());
        when(attendanceRepository.findMonthlyReportChanges(any(), any())).thenReturn(List.of());
        when(trainingAttendanceRepository.findMonthlyReportChanges(any(), any())).thenReturn(List.of());

        ManagerAttendanceReportPage page = service.managerPage(manager, 2026, 10, null, null);

        assertEquals(1, page.getStoreId());
        assertTrue(page.getEmployees().isEmpty());
        assertTrue(page.getDays().isEmpty());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.managerPage(manager, 2026, 10, 99, null));
        assertTrue(error.getMessage().contains("cửa hàng"));
    }

    @Test
    void adminApprovalAndRevisionStayVisibleToTheManager() {
        MonthlyAttendanceReport report = MonthlyAttendanceReport.builder()
                .id(4)
                .store(store)
                .reportYear(2026)
                .reportMonth(10)
                .status(AttendanceReportStatus.SUBMITTED)
                .versionNumber(1)
                .submittedAt(LocalDateTime.of(2026, 10, 9, 8, 0))
                .build();
        when(reportRepository.findByIdDetailed(4)).thenReturn(Optional.of(report));
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubLockedPayroll();

        service.approve(admin, 4);
        assertEquals(AttendanceReportStatus.APPROVED, report.getStatus());

        service.requestRevision(admin, 4, "Thiếu ảnh check-out");
        assertEquals(AttendanceReportStatus.REVISION, report.getStatus());
        assertEquals("Thiếu ảnh check-out", report.getReviewNote());

        stubStoreData();
        when(reportRepository.findCurrent(1, 2026, 10)).thenReturn(Optional.of(report));
        when(attendanceRepository.findMonthlyReportChanges(any(), any())).thenReturn(List.of());
        when(trainingAttendanceRepository.findMonthlyReportChanges(any(), any())).thenReturn(List.of());

        ManagerAttendanceReportPage page = service.managerPage(manager, 2026, 10, null, null);
        assertEquals("Đã trả lại", page.getStatusLabel());
        assertEquals("status-revision", page.getStatusCss());
        assertEquals("Thiếu ảnh check-out", page.getReviewNote());
    }

    @Test
    void submitNotifiesAdminsAndReviewNotifiesTheManager() {
        stubStoreData();
        User sender = User.builder().id(9).username("manager1").build();
        User adminUser = User.builder().id(1).username("admin").build();
        when(userRepository.getReferenceById(9)).thenReturn(sender);
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of(adminUser));
        AtomicReference<MonthlyAttendanceReport> stored = new AtomicReference<>();
        when(reportRepository.findCurrent(1, 2026, 10)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MonthlyAttendanceReport report = invocation.getArgument(0);
            report.setId(15);
            stored.set(report);
            return report;
        });
        when(versionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findByIdDetailed(15)).thenAnswer(invocation -> Optional.of(stored.get()));
        stubLockedPayroll();

        service.submit(manager, 2026, 10);
        service.approve(admin, 15);
        service.requestRevision(admin, 15, "Thiếu ngày công");

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationType> types = ArgumentCaptor.forClass(NotificationType.class);
        verify(notificationService, times(3)).notifyUsers(
                any(), any(), messages.capture(), types.capture(), eq("ATTENDANCE_REPORT"), eq(15));
        assertEquals(NotificationType.ATTENDANCE_REPORT_SUBMITTED, types.getAllValues().get(0));
        assertTrue(messages.getAllValues().get(0).contains("Cầu Giấy"));
        assertEquals(NotificationType.ATTENDANCE_REPORT_APPROVED, types.getAllValues().get(1));
        assertEquals(NotificationType.ATTENDANCE_REPORT_REVISION, types.getAllValues().get(2));
        assertTrue(messages.getAllValues().get(2).contains("Thiếu ngày công"));
    }

    @Test
    void deletesOnlyAnOlderSubmission() throws Exception {
        MonthlyAttendanceReport report = MonthlyAttendanceReport.builder()
                .id(4)
                .store(store)
                .reportYear(2026)
                .reportMonth(10)
                .status(AttendanceReportStatus.SUBMITTED)
                .versionNumber(2)
                .submittedAt(LocalDateTime.of(2026, 10, 9, 8, 0))
                .build();
        MonthlyAttendanceReportVersion older = MonthlyAttendanceReportVersion.builder()
                .id(8)
                .report(report)
                .versionNumber(1)
                .build();
        MonthlyAttendanceReportVersion current = MonthlyAttendanceReportVersion.builder()
                .id(9)
                .report(report)
                .versionNumber(2)
                .build();
        when(reportRepository.findByIdDetailed(4)).thenReturn(Optional.of(report));
        when(versionRepository.findByReport_IdAndVersionNumber(4, 1)).thenReturn(Optional.of(older));
        when(versionRepository.findByReport_IdAndVersionNumber(4, 2)).thenReturn(Optional.of(current));
        reportFiles.save(1, 2026, 10, 1, "v1".getBytes(StandardCharsets.UTF_8));
        reportFiles.save(1, 2026, 10, 2, "v2".getBytes(StandardCharsets.UTF_8));

        service.deleteVersion(admin, 4, 1);

        verify(versionRepository).delete(older);
        assertNull(reportFiles.read(1, 2026, 10, 1));
        assertNotNull(reportFiles.read(1, 2026, 10, 2));

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteVersion(admin, 4, 2));
        assertTrue(error.getMessage().contains("mới hơn"));
        verify(versionRepository, never()).delete(current);
    }

    @Test
    void submitLocksThePayrollAndRejectsDuplicates() {
        stubStoreData();
        when(userRepository.getReferenceById(9)).thenReturn(User.builder().id(9).username("manager1").build());
        AtomicReference<MonthlyAttendanceReport> stored = new AtomicReference<>();
        when(reportRepository.findCurrent(1, 2026, 10)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MonthlyAttendanceReport report = invocation.getArgument(0);
            report.setId(15);
            stored.set(report);
            return report;
        });

        service.submit(manager, 2026, 10);

        verify(payrollService).lockForReport(manager, 2026, 10);
        assertEquals(AttendanceReportStatus.SUBMITTED, stored.get().getStatus());
        // Đang chờ duyệt: không gửi trùng.
        BusinessException waiting = assertThrows(BusinessException.class, () -> service.submit(manager, 2026, 10));
        assertEquals(AttendanceReportServiceImpl.WAITING_MESSAGE, waiting.getMessage());
        // Đã duyệt: không gửi lại.
        stored.get().setStatus(AttendanceReportStatus.APPROVED);
        BusinessException approved = assertThrows(BusinessException.class, () -> service.submit(manager, 2026, 10));
        assertEquals(AttendanceReportServiceImpl.APPROVED_MESSAGE, approved.getMessage());
        verify(versionRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void submitStopsWhenThePayrollIsNotReady() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(reportRepository.findCurrent(1, 2026, 10)).thenReturn(Optional.empty());
        doThrow(new BusinessException("Hãy tính lương trước khi gửi báo cáo."))
                .when(payrollService).lockForReport(manager, 2026, 10);

        BusinessException error = assertThrows(BusinessException.class, () -> service.submit(manager, 2026, 10));

        assertEquals("Hãy tính lương trước khi gửi báo cáo.", error.getMessage());
        verify(reportRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any(), any());
    }

    @Test
    void approvalNeedsALockedPayrollAndHappensOnce() {
        MonthlyAttendanceReport report = submittedReport();
        when(reportRepository.findByIdDetailed(4)).thenReturn(Optional.of(report));

        when(payrollRepository.findPeriod(1, 2026, 10)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.approve(admin, 4));
        assertEquals(AttendanceReportStatus.SUBMITTED, report.getStatus());

        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubLockedPayroll();
        service.approve(admin, 4);
        assertEquals(AttendanceReportStatus.APPROVED, report.getStatus());
        verify(reportRepository, times(2)).findByIdForUpdate(4);

        BusinessException again = assertThrows(BusinessException.class, () -> service.approve(admin, 4));
        assertTrue(again.getMessage().contains("đã được duyệt"));
        assertThrows(BusinessException.class, () -> service.approve(manager, 4));
    }

    @Test
    void returningReopensThePayrollUnlessSomeoneWasAlreadyPaid() {
        MonthlyAttendanceReport report = submittedReport();
        report.setStatus(AttendanceReportStatus.APPROVED);
        when(reportRepository.findByIdDetailed(4)).thenReturn(Optional.of(report));

        when(paymentRepository.existsByReport_Id(4)).thenReturn(true);
        BusinessException paid = assertThrows(BusinessException.class,
                () -> service.requestRevision(admin, 4, "Sai giờ"));
        assertTrue(paid.getMessage().contains("thanh toán"));
        verify(payrollService, never()).reopenForRevision(any(), anyIntValue(), anyIntValue());

        when(paymentRepository.existsByReport_Id(4)).thenReturn(false);
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.requestRevision(admin, 4, "Sai giờ");

        assertEquals(AttendanceReportStatus.REVISION, report.getStatus());
        verify(payrollService).reopenForRevision(1, 2026, 10);
    }

    private static int anyIntValue() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private MonthlyAttendanceReport submittedReport() {
        return MonthlyAttendanceReport.builder()
                .id(4)
                .store(store)
                .reportYear(2026)
                .reportMonth(10)
                .status(AttendanceReportStatus.SUBMITTED)
                .versionNumber(1)
                .submittedAt(LocalDateTime.of(2026, 10, 9, 8, 0))
                .build();
    }

    private void stubLockedPayroll() {
        when(payrollRepository.findPeriod(1, 2026, 10)).thenReturn(Optional.of(MonthlyPayroll.builder()
                .id(30).store(store).payrollYear(2026).payrollMonth(10).status(PayrollStatus.DA_CHOT).build()));
    }

    private void stubStoreData() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findByStoreIdWithAccount(1)).thenReturn(List.of());
        when(attendanceRepository.findForMonthlyReport(eq(1), any(), any())).thenReturn(List.of());
        when(trainingAttendanceRepository.findForMonthlyReport(eq(1), any(), any())).thenReturn(List.of());
    }

    private AuthenticatedUser user(int id, String username, String name, RoleName roleName, Store home) {
        Role role = Role.builder().roleName(roleName).build();
        Employee employee = home == null ? null : Employee.builder().id(id).fullName(name).store(home).build();
        User account = User.builder()
                .id(id)
                .username(username)
                .passwordHash("x")
                .role(role)
                .employee(employee)
                .isActive(true)
                .build();
        if (employee != null) {
            employee.setUser(account);
        }
        return AuthenticatedUser.from(account);
    }
}
