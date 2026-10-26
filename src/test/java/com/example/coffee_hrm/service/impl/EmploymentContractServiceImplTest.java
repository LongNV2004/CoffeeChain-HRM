package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.CitizenIds;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.EmploymentContractListView;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmploymentContractServiceImplTest {

    @TempDir
    Path storage;

    private final EmploymentContractRepository contractRepository = mock(EmploymentContractRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private ContractFileStore fileStore;
    private EmploymentContractServiceImpl service;

    private Store store;
    private Store otherStore;
    private AuthenticatedUser manager;
    private AuthenticatedUser admin;
    private AuthenticatedUser staff;
    private Employee employee;

    @BeforeEach
    void setUp() {
        fileStore = new ContractFileStore(storage.toString());
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new EmploymentContractServiceImpl(
                contractRepository, employeeRepository, storeRepository, userRepository, fileStore, transactionManager, "");
        Employee managerEmployee = Employee.builder().id(10).fullName("Quản lý").status(EmployeeStatus.ACTIVE).build();
        store = Store.builder().id(1).storeName("Cửa hàng A").manager(managerEmployee).build();
        otherStore = Store.builder().id(2).storeName("Cửa hàng B").build();
        managerEmployee.setStore(store);
        employee = Employee.builder().id(30).fullName("Nhân viên").store(store).status(EmployeeStatus.ACTIVE)
                .citizenId("079203000123").build();
        manager = user(2, RoleName.MANAGER, managerEmployee);
        admin = user(1, RoleName.ADMIN, null);
        staff = user(3, RoleName.STAFF, employee);
        when(contractRepository.findByEmployeeId(30)).thenReturn(List.of());
        when(contractRepository.findCodesByPrefix(any())).thenReturn(List.of());
        when(userRepository.findById(2)).thenReturn(Optional.empty());
        when(contractRepository.saveAndFlush(any(EmploymentContract.class))).thenAnswer(invocation -> {
            EmploymentContract saved = invocation.getArgument(0);
            saved.setId(7);
            return saved;
        });
    }

    @Test
    void managerCreatesPdfContractWithGeneratedCode() throws Exception {
        allowEmployee();
        EmploymentContractResponse response = service.create(manager, request(null, new BigDecimal("12000000")), pdf("hop-dong.pdf"));

        assertEquals(7, response.getId());
        assertTrue(response.getContractCode().matches("HD\\d{6}-0001"));
        assertEquals(ContractSigningStatus.DA_TAI_BAN_KY, response.getSigningStatus());
        assertNull(response.getSignedDate());
        assertEquals("hop-dong.pdf", response.getOriginalFileName());
        assertEquals(7, response.getWeeklyShiftCount());
        assertTrue(response.isHasFile());
        assertNull(response.getTerminatedDate());
        assertEquals(1, storedFiles());
    }

    @Test
    void managerCreatesDocxContract() throws Exception {
        allowEmployee();
        EmploymentContractResponse response = service.create(manager, request(null, new BigDecimal("8500000")), docx("hop-dong.docx"));

        assertTrue(response.isHasFile());
        assertEquals("hop-dong.docx", response.getOriginalFileName());
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC, response.getValidityStatus());
    }

    @Test
    void futureStartDateIsNotYetEffective() {
        allowEmployee();
        CreateEmploymentContractRequest upcoming = request(VietnamTime.today().plusMonths(1), new BigDecimal("9000000"));
        upcoming.setStartDate(VietnamTime.today().plusDays(10));

        EmploymentContractResponse response = service.create(manager, upcoming, pdf("sap-hieu-luc.pdf"));

        assertEquals(ContractValidityStatus.CHUA_HIEU_LUC, response.getValidityStatus());
    }

    @Test
    void missingFileIsRejectedBeforeAnythingIsStored() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(manager, request(null, new BigDecimal("1000")), null));
        assertEquals("Vui lòng tải lên file hợp đồng đã ký.", error.getMessage());
        verify(contractRepository, never()).saveAndFlush(any());
        verify(employeeRepository, never()).findByIdWithStore(any());
    }

    @Test
    void invalidContentAndOversizedFileAreRejected() {
        allowEmployee();
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.create(manager, request(null, new BigDecimal("1000")),
                        new ContractFilePayload("hop-dong.pdf", "khong-phai-pdf".getBytes(StandardCharsets.UTF_8))));
        assertEquals("Nội dung file không phải PDF hợp lệ.", invalid.getMessage());

        byte[] oversized = new byte[ContractFileStore.MAX_BYTES + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, oversized, 0, 5);
        BusinessException tooLarge = assertThrows(BusinessException.class,
                () -> service.create(manager, request(null, new BigDecimal("1000")),
                        new ContractFilePayload("lon.pdf", oversized)));
        assertEquals("File hợp đồng vượt quá dung lượng cho phép (2 MB).", tooLarge.getMessage());
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void endDateBeforeStartAndOpenEndedContract() {
        allowEmployee();
        CreateEmploymentContractRequest reversed = request(LocalDate.of(2026, 1, 1), new BigDecimal("1000"));
        reversed.setStartDate(LocalDate.of(2026, 2, 1));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(manager, reversed, pdf("sai-ngay.pdf")));
        assertEquals("Ngày kết thúc không được trước ngày bắt đầu hiệu lực.", error.getMessage());

        CreateEmploymentContractRequest missingEnd = request(LocalDate.of(2026, 12, 31), new BigDecimal("1000.50"));
        missingEnd.setEndDate(null);
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.create(manager, missingEnd, pdf("thieu-ngay.pdf")));
        assertEquals("Vui lòng nhập ngày kết thúc.", missing.getMessage());
        verify(contractRepository, never()).saveAndFlush(any(EmploymentContract.class));
    }

    @Test
    void collidingContractCodeRetriesAndKeepsAUniqueCode() throws Exception {
        allowEmployee();
        java.util.List<String> taken = new ArrayList<>();
        when(contractRepository.findCodesByPrefix(any())).thenAnswer(invocation -> java.util.List.copyOf(taken));
        when(contractRepository.saveAndFlush(any(EmploymentContract.class))).thenAnswer(invocation -> {
            EmploymentContract saving = invocation.getArgument(0);
            if (taken.isEmpty()) {
                taken.add(saving.getContractCode());
                throw new DataIntegrityViolationException("duplicate");
            }
            saving.setId(8);
            return saving;
        });

        EmploymentContractResponse response = service.create(manager, request(null, new BigDecimal("1000")), pdf("song-song.pdf"));

        assertTrue(response.getContractCode().endsWith("-0002"));
        assertEquals(1, storedFiles());
    }

    @Test
    void duplicateContractNumberDeletesStoredFile() throws Exception {
        allowEmployee();
        when(contractRepository.saveAndFlush(any(EmploymentContract.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(manager, request(null, new BigDecimal("1000")), pdf("trung.pdf")));
        assertEquals("Không tạo được hợp đồng vì số hợp đồng bị trùng. Hãy thử lại.", error.getMessage());
        assertEquals(0, storedFiles());
    }

    @Test
    void databaseFailureDeletesStoredFile() throws Exception {
        allowEmployee();
        when(contractRepository.saveAndFlush(any(EmploymentContract.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class,
                () -> service.create(manager, request(null, new BigDecimal("1000")), pdf("loi-db.pdf")));
        assertEquals(0, storedFiles());
    }

    @Test
    void weeklyShiftCountMustBeAPositiveInteger() throws Exception {
        allowEmployee();
        CreateEmploymentContractRequest blank = request(null, new BigDecimal("1000"));
        blank.setWeeklyShiftCount(null);
        CreateEmploymentContractRequest zero = request(null, new BigDecimal("1000"));
        zero.setWeeklyShiftCount(0);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.create(manager, blank, pdf("thieu-ca.pdf")));
        BusinessException notPositive = assertThrows(BusinessException.class,
                () -> service.create(manager, zero, pdf("khong-ca.pdf")));

        assertEquals("Vui lòng nhập số ca làm việc quy định.", missing.getMessage());
        assertEquals("Số ca làm việc quy định phải lớn hơn 0.", notPositive.getMessage());
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void managerCannotCreateContractForSelf() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        Employee self = store.getManager();
        when(employeeRepository.findByIdWithStore(10)).thenReturn(Optional.of(self));
        CreateEmploymentContractRequest own = request(null, new BigDecimal("1000"));
        own.setEmployeeId(10);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(manager, own, pdf("tu-tao.pdf")));
        assertEquals("Không thể tạo hợp đồng cho chính bạn.", error.getMessage());
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void managerCannotCreateForAnotherStore() {
        Employee outsider = Employee.builder().id(40).fullName("Người khác").store(otherStore).status(EmployeeStatus.ACTIVE).build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(40)).thenReturn(Optional.of(outsider));
        CreateEmploymentContractRequest outsiderRequest = request(null, new BigDecimal("1000"));
        outsiderRequest.setEmployeeId(40);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(manager, outsiderRequest, pdf("ngoai.pdf")));
        assertEquals("Nhân viên không thuộc cửa hàng bạn quản lý.", error.getMessage());
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void terminatedEmployeeCannotReceiveANewContract() {
        employee.setStatus(EmployeeStatus.TERMINATED);
        allowEmployee();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(manager, request(null, new BigDecimal("1000")), pdf("nghi.pdf")));
        assertEquals("Nhân viên đã nghỉ việc, không thể tạo hợp đồng mới.", error.getMessage());
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void unresolvedContractBlocksOverlapButExpiredContractDoesNot() {
        allowEmployee();
        EmploymentContract active = contract(employee, VietnamTime.today().minusDays(5), null, null);
        when(contractRepository.findByEmployeeId(30)).thenReturn(List.of(active));
        BusinessException overlap = assertThrows(BusinessException.class,
                () -> service.create(manager, request(null, new BigDecimal("1000")), pdf("trung-thoi-gian.pdf")));
        assertEquals("Nhân viên đang có hợp đồng hiệu lực trùng thời gian. Hãy xử lý hợp đồng hiện tại trước khi tạo hợp đồng mới.",
                overlap.getMessage());

        EmploymentContract expired = contract(employee, VietnamTime.today().minusMonths(2), VietnamTime.today().minusDays(1), null);
        when(contractRepository.findByEmployeeId(30)).thenReturn(List.of(expired));
        EmploymentContractResponse created = service.create(
                manager, request(VietnamTime.today().plusMonths(1), new BigDecimal("1000")), pdf("sau-het-han.pdf"));
        assertEquals(7, created.getId());
    }

    @Test
    void staffAndAdminCannotCreate() {
        BusinessException staffError = assertThrows(BusinessException.class,
                () -> service.create(staff, request(null, new BigDecimal("1000")), pdf("staff.pdf")));
        assertEquals("Bạn không có quyền tạo hợp đồng.", staffError.getMessage());

        BusinessException adminError = assertThrows(BusinessException.class,
                () -> service.create(admin, request(null, new BigDecimal("1000")), pdf("admin.pdf")));
        assertEquals("Bạn không có quyền tạo hợp đồng.", adminError.getMessage());
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void staffCannotOpenAnotherEmployeesContract() {
        Employee outsider = Employee.builder().id(40).fullName("Người khác").store(store).build();
        EmploymentContract foreign = contract(outsider, LocalDate.of(2026, 1, 1), null, "c" + "ab".repeat(16) + ".pdf");
        foreign.setId(8);
        when(contractRepository.findDetailById(8)).thenReturn(Optional.of(foreign));

        BusinessException error = assertThrows(BusinessException.class, () -> service.detail(staff, 8));
        assertEquals("Bạn không có quyền xem hợp đồng này.", error.getMessage());
        assertThrows(BusinessException.class, () -> service.download(staff, 8));
    }

    @Test
    void adminSeesContractsFromEveryStoreAndSearchCanBeEmpty() {
        EmploymentContract first = contract(employee, LocalDate.of(2026, 1, 1), null, null);
        first.setId(1);
        first.setContractCode("HD202601-0001");
        Employee otherEmployee = Employee.builder().id(40).fullName("Nguyễn Văn B").store(otherStore).build();
        EmploymentContract second = contract(otherEmployee, LocalDate.of(2026, 2, 1), null, null);
        second.setId(2);
        second.setContractCode("HD202602-0001");
        when(contractRepository.findAllWithEmployee()).thenReturn(List.of(first, second));
        when(storeRepository.findAll()).thenReturn(List.of(store, otherStore));

        EmploymentContractListView all = service.search(admin, null, null, null, null, null, 1);
        assertEquals(2, all.getMatchCount());
        assertFalse(all.isFiltered());

        EmploymentContractListView none = service.search(admin, "khong-ton-tai", null, null, null, null, 1);
        assertEquals(0, none.getMatchCount());
        assertTrue(none.isFiltered());
        assertTrue(none.getContracts().isEmpty());
    }

    @Test
    void emptyListAndSalaryMustBePositive() {
        when(contractRepository.findAllWithEmployee()).thenReturn(List.of());
        when(storeRepository.findAll()).thenReturn(List.of());
        EmploymentContractListView empty = service.search(admin, null, null, null, null, null, 1);
        assertEquals(0, empty.getMatchCount());
        assertEquals(0, empty.getTotalPages());

        allowEmployee();
        BusinessException salary = assertThrows(BusinessException.class,
                () -> service.create(manager, request(null, BigDecimal.ZERO), pdf("luong.pdf")));
        assertEquals("Mức lương phải lớn hơn 0.", salary.getMessage());
    }

    @Test
    void missingCitizenIdBlocksTheContractAndLeavesTheSignedFileUntouched() throws Exception {
        allowEmployee();
        employee.setCitizenId(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(manager, request(null, new BigDecimal("1000")), pdf("thieu-cccd.pdf")));

        assertEquals(CitizenIds.MISSING_FOR_CONTRACT, error.getMessage());
        verify(contractRepository, never()).saveAndFlush(any());
        try (Stream<Path> files = Files.walk(storage)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void changingTheProfileCitizenIdDoesNotRewriteTheSignedFile() throws Exception {
        allowEmployee();
        EmploymentContractResponse created = service.create(
                manager, request(null, new BigDecimal("1000")), pdf("giu-file.pdf"));
        assertEquals("079203000123", created.getCitizenId());
        Path file;
        try (Stream<Path> files = Files.walk(storage)) {
            file = files.filter(Files::isRegularFile).findFirst().orElseThrow();
        }
        byte[] before = Files.readAllBytes(file);

        employee.setCitizenId("079203000999");

        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals("079203000123", created.getCitizenId());
    }

    private void allowEmployee() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
    }

    private CreateEmploymentContractRequest request(LocalDate endDate, BigDecimal salary) {
        CreateEmploymentContractRequest request = new CreateEmploymentContractRequest();
        request.setEmployeeId(30);
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(endDate == null ? LocalDate.of(2026, 12, 31) : endDate);
        request.setSalary(salary);
        request.setWeeklyShiftCount(7);
        return request;
    }

    private static EmploymentContract contract(Employee owner, LocalDate start, LocalDate end, String file) {
        return EmploymentContract.builder()
                .employee(owner)
                .startDate(start)
                .endDate(end)
                .salary(new BigDecimal("1000"))
                .signingStatus(ContractSigningStatus.DA_TAI_BAN_KY)
                .signedFilePath(file)
                .build();
    }

    private static ContractFilePayload pdf(String name) {
        return new ContractFilePayload(name, "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static ContractFilePayload docx(String name) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new ContractFilePayload(name, out.toByteArray());
    }

    private long storedFiles() throws Exception {
        if (!Files.isDirectory(storage)) {
            return 0;
        }
        try (var files = Files.list(storage)) {
            return files.count();
        }
    }

    private AuthenticatedUser user(int id, RoleName roleName, Employee owner) {
        Role role = Role.builder().id(id).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder()
                .id(id)
                .username(roleName.name().toLowerCase())
                .passwordHash("hash")
                .role(role)
                .employee(owner)
                .isActive(true)
                .build());
    }
}
