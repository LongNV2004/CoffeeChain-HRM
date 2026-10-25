package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmploymentContractServiceImplTest {

    private final EmploymentContractRepository contractRepository = mock(EmploymentContractRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final EmploymentContractServiceImpl service = new EmploymentContractServiceImpl(
            contractRepository, employeeRepository, storeRepository, "");

    private Store store;
    private Store otherStore;
    private AuthenticatedUser manager;
    private AuthenticatedUser admin;
    private AuthenticatedUser staff;
    private Employee employee;

    @BeforeEach
    void setUp() {
        Employee managerEmployee = Employee.builder().id(10).fullName("Quản lý").build();
        store = Store.builder().id(1).storeName("Cửa hàng A").manager(managerEmployee).build();
        otherStore = Store.builder().id(2).storeName("Cửa hàng B").build();
        managerEmployee.setStore(store);
        employee = Employee.builder().id(30).fullName("Nhân viên").store(store).build();
        manager = user(2, RoleName.MANAGER, managerEmployee);
        admin = user(1, RoleName.ADMIN, null);
        staff = user(3, RoleName.STAFF, employee);
    }

    @Test
    void createLeavesContractUnsigned() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
        when(contractRepository.countByContractCodeIgnoreCase("HD-01")).thenReturn(0L);
        when(contractRepository.save(any(EmploymentContract.class))).thenAnswer(invocation -> {
            EmploymentContract saved = invocation.getArgument(0);
            saved.setId(7);
            return saved;
        });

        EmploymentContractResponse response = service.create(manager, request("HD-01", null, new BigDecimal("12000000")));

        assertEquals(7, response.getId());
        assertEquals(ContractSigningStatus.CHUA_CO_BAN_KY, response.getSigningStatus());
        assertNull(response.getSignedDate());
        assertNull(response.getSignedFilePath());
        assertNull(response.getTerminatedDate());
        assertEquals(store.getId(), response.getStoreId());
    }

    @Test
    void createAllowsOpenEndedContractAndRejectsReversedDates() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
        when(contractRepository.countByContractCodeIgnoreCase("HD-02")).thenReturn(0L);
        when(contractRepository.save(any(EmploymentContract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmploymentContractResponse openEnded = service.create(manager, request("HD-02", null, new BigDecimal("1000.50")));
        assertNull(openEnded.getEndDate());

        CreateEmploymentContractRequest reversed = request("HD-03", LocalDate.of(2026, 1, 1), new BigDecimal("1000"));
        reversed.setStartDate(LocalDate.of(2026, 2, 1));
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(manager, reversed));
        assertEquals("Ngày kết thúc không được trước ngày bắt đầu.", error.getMessage());
        verify(contractRepository).save(any(EmploymentContract.class));
    }

    @Test
    void duplicateCodeAndInvalidSalaryAreRejected() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(employee));
        when(contractRepository.countByContractCodeIgnoreCase("HD-01")).thenReturn(1L);

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.create(manager, request("HD-01", null, new BigDecimal("1000"))));
        assertEquals("Mã hợp đồng đã tồn tại.", duplicate.getMessage());

        when(contractRepository.countByContractCodeIgnoreCase("HD-04")).thenReturn(0L);
        BusinessException salary = assertThrows(BusinessException.class,
                () -> service.create(manager, request("HD-04", null, BigDecimal.ZERO)));
        assertEquals("Mức lương phải lớn hơn 0.", salary.getMessage());
        verify(contractRepository, never()).save(any());
    }

    @Test
    void managerCannotUseEmployeeOrContractFromAnotherStore() {
        Employee outsider = Employee.builder().id(40).fullName("Người khác").store(otherStore).build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(40)).thenReturn(Optional.of(outsider));
        CreateEmploymentContractRequest outsiderRequest = request("HD-09", null, new BigDecimal("1000"));
        outsiderRequest.setEmployeeId(40);

        BusinessException createError = assertThrows(BusinessException.class,
                () -> service.create(manager, outsiderRequest));
        assertEquals("Nhân viên không thuộc cửa hàng bạn quản lý.", createError.getMessage());

        EmploymentContract foreign = EmploymentContract.builder()
                .id(8)
                .contractCode("HD-B")
                .employee(outsider)
                .startDate(LocalDate.of(2026, 1, 1))
                .salary(new BigDecimal("1000"))
                .signingStatus(ContractSigningStatus.CHUA_CO_BAN_KY)
                .build();
        when(contractRepository.findDetailById(8)).thenReturn(Optional.of(foreign));
        BusinessException detailError = assertThrows(BusinessException.class, () -> service.detail(manager, 8));
        assertEquals("Bạn không có quyền xem hợp đồng này.", detailError.getMessage());
    }

    @Test
    void missingEmployeeAndStaffAreRejected() {
        when(employeeRepository.findByIdWithStore(99)).thenReturn(Optional.empty());
        CreateEmploymentContractRequest missing = request("HD-10", null, new BigDecimal("1000"));
        missing.setEmployeeId(99);
        BusinessException notFound = assertThrows(BusinessException.class, () -> service.create(admin, missing));
        assertEquals("Không tìm thấy nhân viên.", notFound.getMessage());

        BusinessException denied = assertThrows(BusinessException.class,
                () -> service.create(staff, request("HD-11", null, new BigDecimal("1000"))));
        assertEquals("Bạn không có quyền tạo hợp đồng.", denied.getMessage());
        verify(storeRepository, never()).findByManager_Id(any());
    }

    private CreateEmploymentContractRequest request(String code, LocalDate endDate, BigDecimal salary) {
        CreateEmploymentContractRequest request = new CreateEmploymentContractRequest();
        request.setContractCode(code);
        request.setEmployeeId(30);
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(endDate);
        request.setSalary(salary);
        return request;
    }

    private AuthenticatedUser user(int id, RoleName roleName, Employee employee) {
        Role role = Role.builder().id(id).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder()
                .id(id)
                .username(roleName.name().toLowerCase())
                .passwordHash("hash")
                .role(role)
                .employee(employee)
                .isActive(true)
                .build());
    }
}
