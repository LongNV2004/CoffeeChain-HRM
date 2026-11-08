package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.ResignationProposalItemRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ocr.ContractOcrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractRenewalServiceImplTest {

    @TempDir
    Path storage;

    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ResignationProposalItemRepository resignations = mock(ResignationProposalItemRepository.class);
    private final ContractOcrService ocr = mock(ContractOcrService.class);
    private final NotificationService notifications = mock(NotificationService.class);

    private ContractFileStore fileStore;
    private ContractRenewalServiceImpl service;
    private Employee employee;
    private EmploymentContract source;
    private AuthenticatedUser manager;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = VietnamTime.today();
        fileStore = new ContractFileStore(storage.toString());
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new ContractRenewalServiceImpl(
                contracts, stores, users, resignations, fileStore, ocr, notifications, employees, transactions);
        Store store = Store.builder().id(1).storeName("Cửa hàng A").build();
        Store other = Store.builder().id(2).storeName("Cửa hàng B").build();
        employee = Employee.builder().id(30).fullName("Nhân viên").status(EmployeeStatus.ACTIVE)
                .citizenId("079203000123").store(store).build();
        Employee managerEmployee = Employee.builder().id(10).fullName("Quản lý").status(EmployeeStatus.ACTIVE)
                .store(store).build();
        store.setManager(managerEmployee);
        manager = AuthenticatedUser.from(User.builder().id(2).username("manager").passwordHash("hash")
                .role(Role.builder().roleName(RoleName.MANAGER).build())
                .employee(managerEmployee).isActive(true).build());
        source = EmploymentContract.builder().id(8).contractCode("HD-CU")
                .employee(employee)
                .startDate(today.minusMonths(2))
                .endDate(today.plusDays(5))
                .salary(new BigDecimal("10000"))
                .weeklyShiftCount(5)
                .signedFilePath("c" + "ab".repeat(16) + ".pdf")
                .build();
        when(stores.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(contracts.lockById(8)).thenReturn(Optional.of(source));
        when(contracts.findByEmployeeId(30)).thenReturn(List.of(source));
        when(contracts.findCodesByPrefix(any())).thenReturn(List.of());
        when(contracts.existsByPreviousContract_Id(8)).thenReturn(false);
        when(resignations.countPendingByEmployeeId(30)).thenReturn(0L);
        when(ocr.requireAccepted(any(), any(), any())).thenReturn(new ContractOcrService.Acceptance("hash", "DAT"));
        when(users.findById(2)).thenReturn(Optional.empty());
        when(users.findByEmployee_Id(30)).thenReturn(Optional.of(User.builder().id(30).username("nv").passwordHash("hash")
                .role(Role.builder().roleName(RoleName.STAFF).build()).employee(employee).isActive(true).build()));
        when(users.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of());
        when(contracts.saveAndFlush(any(EmploymentContract.class))).thenAnswer(invocation -> {
            EmploymentContract saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(21);
            }
            return saved;
        });
        when(stores.findByManager_Id(99)).thenReturn(Optional.of(other));
    }

    @Test
    void renewalStartingTodayExpiresTheSourceAndKeepsItsFile() {
        EmploymentContractResponse response = service.renew(manager, 8, request(today, today.plusMonths(6)), pdf());

        assertEquals(21, response.getId());
        assertTrue(response.getContractCode().startsWith("HD"));
        assertNull(source.getSupersededDate());
        assertEquals(new BigDecimal("10000"), source.getSalary());
        assertEquals(today.plusDays(5), source.getEndDate());
        assertEquals("c" + "ab".repeat(16) + ".pdf", source.getSignedFilePath());
        assertEquals(ContractValidityStatus.DA_GIA_HAN, ContractValidityRules.resolve(source, today, true));
        verify(notifications).notifyUsers(any(), any(), any(), any(), any(), any());
        assertEquals(1, storedFiles());
    }

    @Test
    void futureRenewalDoesNotChangeTheSourceYet() {
        EmploymentContract created = captureSaved(today.plusDays(4));

        assertNull(source.getSupersededDate());
        assertEquals(ContractValidityStatus.DA_GIA_HAN, ContractValidityRules.resolve(source, today, true));
        assertEquals(new BigDecimal("10000"), source.getSalary());
        assertNull(created.getActivatedAt());
        assertEquals(source, created.getPreviousContract());
        assertEquals(new BigDecimal("25000"), created.getSalary());
        assertEquals(ContractValidityStatus.CHO_HIEU_LUC, ContractValidityRules.resolve(created, today));
        assertFalse(ContractValidityRules.coversWorkDay(created, today));
        assertTrue(ContractValidityRules.coversWorkDay(source, today));
        assertTrue(ContractValidityRules.coversWorkDay(source, today.plusDays(3)));
    }

    @Test
    void rejectedRenewalsDoNotStoreAFile() {
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(manager, 8, request(today.minusDays(1), today.plusMonths(1)), pdf()))
                .getMessage().contains("quá khứ"));
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(manager, 8, request(today, today), pdf()))
                .getMessage().contains("sau ngày bắt đầu"));
        source.setEndDate(today.minusDays(1));
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(manager, 8, request(today, today.plusMonths(1)), pdf()))
                .getMessage().contains("Đã hết hạn"));
        source.setEndDate(today.plusMonths(2));
        source.setTerminatedDate(today.minusDays(1));
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(manager, 8, request(today, today.plusMonths(1)), pdf()))
                .getMessage().contains("chấm dứt"));
        source.setTerminatedDate(null);
        source.setPreviousContract(EmploymentContract.builder().id(1).build());
        source.setStartDate(today.plusDays(2));
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(manager, 8, request(today.plusDays(3), today.plusMonths(2)), pdf()))
                .getMessage().contains("Chờ hiệu lực"));
        source.setPreviousContract(null);
        source.setStartDate(today.minusMonths(2));
        when(contracts.existsByPreviousContract_Id(8)).thenReturn(true);
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(manager, 8, request(today, today.plusMonths(1)), pdf()))
                .getMessage().contains("đã được gia hạn"));
        when(contracts.existsByPreviousContract_Id(8)).thenReturn(false);
        when(resignations.countPendingByEmployeeId(30)).thenReturn(1L);
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(manager, 8, request(today, today.plusMonths(1)), pdf()))
                .getMessage().contains("chờ Admin"));
        assertEquals(0, storedFiles());
    }

    @Test
    void managerOfAnotherStoreAndADuplicateSaveAreRejected() {
        AuthenticatedUser outsider = AuthenticatedUser.from(User.builder().id(9).username("other").passwordHash("hash")
                .role(Role.builder().roleName(RoleName.MANAGER).build())
                .employee(Employee.builder().id(99).fullName("Khác").build()).isActive(true).build());
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(outsider, 8, request(today, today.plusMonths(1)), pdf()))
                .getMessage().contains("không thuộc cửa hàng"));

        when(contracts.saveAndFlush(any(EmploymentContract.class))).thenThrow(new DataIntegrityViolationException("trùng"));
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.renew(manager, 8, request(today, today.plusMonths(1)), pdf()));
        assertTrue(duplicate.getMessage().contains("đã được gia hạn"));
        assertEquals(0, storedFiles());
        verify(notifications, never()).notifyUsers(any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonPdfIsRejected() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.renew(
                manager, 8, request(today, today.plusMonths(1)),
                new ContractFilePayload("anh.jpg", new byte[] {(byte) 0xff, (byte) 0xd8})));
        assertTrue(error.getMessage().contains("PDF"));
    }

    @Test
    void activationSwitchesTheContractsOnceAndSkipsATerminatedSource() {
        EmploymentContract renewal = EmploymentContract.builder().id(21).contractCode("HD-MOI")
                .employee(employee)
                .previousContract(source)
                .startDate(today)
                .endDate(today.plusMonths(6))
                .salary(new BigDecimal("25000"))
                .weeklyShiftCount(8)
                .build();
        when(contracts.findDueActivationIds(today)).thenReturn(List.of(21));
        when(contracts.lockById(21)).thenReturn(Optional.of(renewal));

        assertEquals(1, service.activateDue(today));
        assertNotNull(renewal.getActivatedAt());
        assertNull(source.getSupersededDate());
        assertEquals(new BigDecimal("10000"), source.getSalary());
        assertEquals(5, source.getWeeklyShiftCount());
        assertEquals(0, service.activateDue(today));

        renewal.setActivatedAt(null);
        source.setSupersededDate(null);
        source.setTerminatedDate(today.minusDays(1));
        assertEquals(0, service.activateDue(today));
        assertNull(renewal.getActivatedAt());
    }

    @Test
    void stopRenewalKeepsTheEmployeeWorkingUntilTheEndDate() {
        EmploymentContractResponse response = service.stopRenewal(manager, 8, "store", "Hết nhu cầu");

        assertEquals(8, response.getId());
        assertNotNull(source.getRenewalDeclinedAt());
        assertEquals(new BigDecimal("10000"), source.getSalary());
        assertEquals("c" + "ab".repeat(16) + ".pdf", source.getSignedFilePath());
        assertEquals(ContractValidityStatus.KHONG_GIA_HAN, ContractValidityRules.resolve(source, today));
        assertTrue(ContractValidityRules.coversWorkDay(source, today));
        source.setEndDate(today.minusDays(1));
        assertEquals(ContractValidityStatus.DA_HET_HAN, ContractValidityRules.resolve(source, today));
        assertTrue(assertThrows(BusinessException.class, () -> service.renew(manager, 8, request(today, today.plusMonths(1)), pdf()))
                .getMessage().contains("không gia hạn"));
    }

    @Test
    void inForceContractCanBeRenewedBeforeTheRenewalWindow() {
        source.setEndDate(today.plusMonths(3));
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC, ContractValidityRules.resolve(source, today));

        EmploymentContractResponse response = service.renew(manager, 8, request(today.plusDays(1), today.plusMonths(7)), pdf());

        assertEquals(21, response.getId());
        assertEquals(ContractValidityStatus.DA_GIA_HAN, ContractValidityRules.resolve(source, today, true));
    }

    @Test
    void managerCannotRenewOwnOrManagerContracts() {
        Employee managerEmployee = manager.getEmployeeId() == null ? null
                : Employee.builder().id(10).fullName("Quản lý").status(EmployeeStatus.ACTIVE)
                .store(source.getEmployee().getStore()).build();
        EmploymentContract managerContract = EmploymentContract.builder().id(9).contractCode("HD-QL")
                .employee(managerEmployee)
                .startDate(today.minusMonths(1))
                .endDate(today.plusDays(5))
                .salary(new BigDecimal("10000"))
                .build();
        when(contracts.lockById(9)).thenReturn(Optional.of(managerContract));
        when(users.findByEmployee_Id(10)).thenReturn(Optional.of(User.builder().id(2).username("manager")
                .passwordHash("hash").role(Role.builder().roleName(RoleName.MANAGER).build())
                .employee(managerEmployee).isActive(true).build()));

        CreateEmploymentContractRequest ownRequest = request(today, today.plusMonths(1));
        ownRequest.setEmployeeId(10);
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(manager, 9, ownRequest, pdf()))
                .getMessage().contains("chính bạn"));
        assertTrue(assertThrows(BusinessException.class,
                () -> service.stopRenewal(manager, 9, "store", null))
                .getMessage().contains("chính bạn"));
    }

    @Test
    void adminRenewsManagerContractAndManagerCannotRenewStaffOutsideStore() {
        Employee managerEmployee = Employee.builder().id(50).fullName("Manager B").status(EmployeeStatus.ACTIVE)
                .citizenId("079203000999").store(source.getEmployee().getStore()).build();
        EmploymentContract managerContract = EmploymentContract.builder().id(11).contractCode("HD-M")
                .employee(managerEmployee)
                .startDate(today.minusMonths(1))
                .endDate(today.plusDays(5))
                .salary(new BigDecimal("12000"))
                .build();
        AuthenticatedUser admin = AuthenticatedUser.from(User.builder().id(1).username("admin").passwordHash("hash")
                .role(Role.builder().roleName(RoleName.ADMIN).build()).isActive(true).build());
        when(contracts.lockById(11)).thenReturn(Optional.of(managerContract));
        when(contracts.findByEmployeeId(50)).thenReturn(List.of(managerContract));
        when(users.findByEmployee_Id(50)).thenReturn(Optional.of(User.builder().id(50).username("mb")
                .passwordHash("hash").role(Role.builder().roleName(RoleName.MANAGER).build())
                .employee(managerEmployee).isActive(true).build()));
        when(ocr.requireAccepted(any(), any(), eq(managerEmployee)))
                .thenReturn(new ContractOcrService.Acceptance("hash", "DAT"));

        CreateEmploymentContractRequest renewRequest = request(today.plusDays(2), today.plusMonths(8));
        renewRequest.setEmployeeId(50);
        EmploymentContractResponse renewed = service.renew(admin, 11, renewRequest, pdf());
        assertEquals(21, renewed.getId());

        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(admin, 8, request(today, today.plusMonths(1)), pdf()))
                .getMessage().contains("Manager"));

        AuthenticatedUser outsider = AuthenticatedUser.from(User.builder().id(99).username("other").passwordHash("hash")
                .role(Role.builder().roleName(RoleName.MANAGER).build())
                .employee(Employee.builder().id(99).fullName("QL khác").status(EmployeeStatus.ACTIVE)
                        .store(Store.builder().id(2).storeName("Cửa hàng B").build()).build())
                .isActive(true).build());
        assertTrue(assertThrows(BusinessException.class,
                () -> service.renew(outsider, 8, request(today, today.plusMonths(1)), pdf()))
                .getMessage().contains("cửa hàng"));
    }

    private EmploymentContract captureSaved(LocalDate start) {
        final EmploymentContract[] saved = new EmploymentContract[1];
        when(contracts.saveAndFlush(any(EmploymentContract.class))).thenAnswer(invocation -> {
            EmploymentContract contract = invocation.getArgument(0);
            if (contract.getId() == null) {
                contract.setId(21);
                saved[0] = contract;
            }
            return contract;
        });
        service.renew(manager, 8, request(start, start.plusMonths(6)), pdf());
        return saved[0];
    }

    private CreateEmploymentContractRequest request(LocalDate start, LocalDate end) {
        CreateEmploymentContractRequest request = new CreateEmploymentContractRequest();
        request.setEmployeeId(30);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setSalary(new BigDecimal("25000"));
        request.setWeeklyShiftCount(8);
        return request;
    }

    private static ContractFilePayload pdf() {
        return new ContractFilePayload("hop-dong.pdf", "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
    }

    private long storedFiles() {
        try (var files = Files.list(storage)) {
            return files.count();
        } catch (Exception ex) {
            return 0;
        }
    }
}
