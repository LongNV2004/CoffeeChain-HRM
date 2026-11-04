package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.NotificationRepository;
import com.example.coffee_hrm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractRenewalReminderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    private final EmploymentContractRepository contractRepository = mock(EmploymentContractRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private ContractRenewalReminder reminder;

    private EmploymentContract contract;
    private User manager;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        reminder = new ContractRenewalReminder(
                contractRepository, userRepository, notificationRepository, notificationService, transactionManager);
        Employee managerEmployee = Employee.builder().id(10).fullName("Quản lý").build();
        Store store = Store.builder().id(1).storeName("Cầu Giấy").manager(managerEmployee).build();
        Employee employee = Employee.builder().id(4).fullName("Nghiêm Tiến Quý").store(store).build();
        contract = EmploymentContract.builder()
                .id(8)
                .contractCode("HD202611-0001")
                .employee(employee)
                .startDate(TODAY.minusMonths(1))
                .endDate(TODAY.plusDays(7))
                .build();
        manager = User.builder().id(2).role(Role.builder().roleName(RoleName.MANAGER).build()).build();
        when(contractRepository.findRenewalWindowIds(TODAY, TODAY.plusDays(7))).thenReturn(List.of(8));
        when(contractRepository.findReminderTarget(8)).thenReturn(Optional.of(contract));
        when(userRepository.findByEmployee_Id(10)).thenReturn(Optional.of(manager));
    }

    @Test
    void contractEnteringTheRenewalWindowNotifiesItsStoreManagerOnce() {
        assertEquals(1, reminder.remindDue(TODAY));
        verify(notificationService).notifyUsers(
                eq(List.of(manager)),
                eq("Hợp đồng chờ gia hạn"),
                eq("Hợp đồng HD202611-0001 của Nghiêm Tiến Quý (mã 4) đã chuyển sang trạng thái Chờ gia hạn. Ngày hết hạn 11/11/2026."),
                eq(NotificationType.CONTRACT_RENEWAL_DUE),
                eq(ContractRenewalReminder.REFERENCE_TYPE),
                eq(8));

        when(notificationRepository.existsByTypeAndReferenceTypeAndReferenceId(
                NotificationType.CONTRACT_RENEWAL_DUE, ContractRenewalReminder.REFERENCE_TYPE, 8)).thenReturn(true);

        assertEquals(0, reminder.remindDue(TODAY));
        verify(notificationService).notifyUsers(any(), any(), any(), any(), any(), any());
    }

    @Test
    void contractStillInForceIsNotReminded() {
        contract.setEndDate(TODAY.plusDays(8));

        assertEquals(0, reminder.remindDue(TODAY));
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any(), any());
    }

    @Test
    void storeWithoutAManagerAccountIsSkipped() {
        when(userRepository.findByEmployee_Id(10)).thenReturn(Optional.empty());

        assertEquals(0, reminder.remindDue(TODAY));
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any(), any());
    }
}
