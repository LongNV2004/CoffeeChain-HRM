package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.ResignationItemStatus;
import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateResignationProposalRequest;
import com.example.coffee_hrm.dto.request.ResignationItemRequest;
import com.example.coffee_hrm.dto.response.ResignationDecisionResult;
import com.example.coffee_hrm.dto.response.ResignationProposalResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.ResignationProposal;
import com.example.coffee_hrm.entity.ResignationProposalItem;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ResignationProposalItemRepository;
import com.example.coffee_hrm.repository.ResignationProposalRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AccountMailService;
import com.example.coffee_hrm.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResignationProposalServiceImplTest {

    @Mock
    private ResignationProposalRepository proposalRepository;
    @Mock
    private ResignationProposalItemRepository itemRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AccountMailService accountMailService;

    private ResignationProposalServiceImpl service;
    private Store store;
    private Store otherStore;
    private Employee managerEmployee;
    private User managerUser;
    private User adminUser;
    private AuthenticatedUser manager;
    private AuthenticatedUser admin;
    private AuthenticatedUser staffActor;
    private Employee an;
    private Employee binh;

    @BeforeEach
    void setUp() {
        service = new ResignationProposalServiceImpl(
                proposalRepository,
                itemRepository,
                employeeRepository,
                userRepository,
                storeRepository,
                notificationService,
                accountMailService);

        store = Store.builder().id(4).storeName("Store A").address("12 Nguyễn Trãi").build();
        otherStore = Store.builder().id(8).storeName("Store B").address("20 Lê Lợi").build();
        managerEmployee = Employee.builder().id(9).fullName("Lan").email("lan@shop.vn")
                .store(store).status(EmployeeStatus.ACTIVE).hireDate(LocalDate.of(2020, 1, 1)).build();
        store.setManager(managerEmployee);
        Role managerRole = Role.builder().id(2).roleName(RoleName.MANAGER).build();
        Role adminRole = Role.builder().id(1).roleName(RoleName.ADMIN).build();
        Role staffRole = Role.builder().id(3).roleName(RoleName.STAFF).build();
        managerUser = User.builder().id(20).username("lan@shop.vn").passwordHash("x").role(managerRole)
                .employee(managerEmployee).isActive(true).build();
        managerEmployee.setUser(managerUser);
        Employee adminEmployee = Employee.builder().id(1).fullName("Admin").email("admin@shop.vn").build();
        adminUser = User.builder().id(1).username("admin@shop.vn").passwordHash("x").role(adminRole)
                .employee(adminEmployee).isActive(true).build();
        manager = AuthenticatedUser.from(managerUser);
        admin = AuthenticatedUser.from(adminUser);
        an = staff(11, "An Nguyễn", store, staffRole);
        binh = staff(12, "Bình Trần", store, staffRole);
        staffActor = AuthenticatedUser.from(an.getUser());
    }

    @Test
    void createOneEmployeeKeepsThemWorkingAndNotifiesAdmin() {
        stubCreate(List.of(an));
        when(accountMailService.sendResignationNotice(any(), any(), any(), any())).thenReturn(true);

        ResignationDecisionResult result = service.create(manager, proposal(line(an, VietnamTime.today().plusDays(5), "Hết nhu cầu ca tối", null)));

        ArgumentCaptor<ResignationProposal> captor = ArgumentCaptor.forClass(ResignationProposal.class);
        verify(proposalRepository).save(captor.capture());
        ResignationProposal saved = captor.getValue();
        assertEquals(1, saved.getItems().size());
        assertEquals(ResignationItemStatus.PENDING, saved.getItems().get(0).getStatus());
        assertEquals(EmployeeStatus.ACTIVE, an.getStatus());
        assertNull(an.getTerminationDate());
        assertEquals(store, saved.getStore());
        assertEquals(managerEmployee, saved.getRequestedBy());
        assertEquals(1, result.getProcessedCount());
        assertTrue(result.isEmailSent());
        verify(notificationService).notifyUsers(
                eq(List.of(adminUser)),
                eq("Đề xuất nghỉ việc mới"),
                org.mockito.ArgumentMatchers.contains("mã 7"),
                eq(NotificationType.RESIGNATION_PROPOSAL_CREATED),
                eq("RESIGNATION_PROPOSAL"),
                eq(7));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createManyEmployeesAllowsDifferentDates() {
        stubCreate(List.of(an, binh));

        service.create(manager, proposal(
                line(an, VietnamTime.today().plusDays(2), "Chuyển việc", "Ca sáng"),
                line(binh, VietnamTime.today().plusDays(9), "Giảm biên", null)));

        ArgumentCaptor<ResignationProposal> captor = ArgumentCaptor.forClass(ResignationProposal.class);
        verify(proposalRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getItems().size());
        assertEquals(an.getId(), captor.getValue().getItems().get(0).getEmployee().getId());
        assertEquals(binh.getId(), captor.getValue().getItems().get(1).getEmployee().getId());
        assertEquals("Nhân viên", captor.getValue().getItems().get(0).getPosition());
        assertEquals(VietnamTime.today(), captor.getValue().getItems().get(0).getProposedTerminationDate());
        assertEquals(VietnamTime.today(), captor.getValue().getItems().get(1).getProposedTerminationDate());
    }

    @Test
    void createRejectsDuplicateEmployeeInTheSameProposal() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByStoreIdWithAccount(4)).thenReturn(List.of(an));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                line(an, VietnamTime.today().plusDays(1), "Lý do", null),
                line(an, VietnamTime.today().plusDays(2), "Lý do khác", null))));

        assertEquals(ResignationProposalServiceImpl.DUPLICATE_EMPLOYEE, ex.getMessage());
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void createRejectsEmployeeOutsideTheManagedStore() {
        Employee outsider = staff(30, "Ngoài cửa hàng", otherStore, an.getUser().getRole());
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByStoreIdWithAccount(4)).thenReturn(List.of(an));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                line(outsider, VietnamTime.today().plusDays(1), "Lý do", null))));

        assertEquals(ResignationProposalServiceImpl.OUTSIDE_STORE, ex.getMessage());
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void createRejectsTerminatedEmployeeAndInvalidLineWithoutSaving() {
        an.setStatus(EmployeeStatus.TERMINATED);
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByStoreIdWithAccount(4)).thenReturn(List.of(an, binh));

        BusinessException terminated = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                line(an, VietnamTime.today().plusDays(1), "Lý do", null))));
        assertTrue(terminated.getMessage().contains(ResignationProposalServiceImpl.ALREADY_LEFT));

        an.setStatus(EmployeeStatus.ACTIVE);
        BusinessException missingReason = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                line(binh, VietnamTime.today().plusDays(1), "  ", null))));
        assertEquals(ResignationProposalServiceImpl.ITEM_REASON_REQUIRED, missingReason.getMessage());
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void createRejectsEmployeeAlreadyInAnOpenProposal() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByStoreIdWithAccount(4)).thenReturn(List.of(an));
        when(itemRepository.findOpenEmployeeIds(anyList(), eq(ResignationItemStatus.PENDING), eq(ResignationItemStatus.APPROVED)))
                .thenReturn(List.of(an.getId()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                line(an, VietnamTime.today().plusDays(1), "Lý do", null))));

        assertTrue(ex.getMessage().contains(ResignationProposalServiceImpl.OPEN_PROPOSAL));
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void adminSearchIsNotLimitedToOneStore() {
        when(proposalRepository.search(null, null, "ca", null)).thenReturn(List.of());

        service.listForAdmin(admin, null, null, " ca ");

        verify(proposalRepository).search(null, null, "ca", null);
    }

    @Test
    void approveOneEmployeeLeavesTheOtherPendingAndEndsEmploymentImmediately() {
        ProposalFixture fixture = pendingProposal();
        when(employeeRepository.findByIdWithStoreAccount(11)).thenReturn(Optional.of(an));
        when(employeeRepository.lockByIdForLeaveRequest(11)).thenReturn(Optional.of(an));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(accountMailService.sendResignationDecision(eq(an.getEmail()), eq("An Nguyễn"), eq("Store A"), any()))
                .thenReturn(true);

        ResignationDecisionResult result = service.approveItem(admin, 7, 101);

        assertEquals(ResignationItemStatus.APPROVED, fixture.first().getStatus());
        assertEquals(ResignationItemStatus.PENDING, fixture.second().getStatus());
        assertEquals(ResignationProposalStatus.PARTIALLY_PROCESSED, fixture.proposal().getStatus());
        assertEquals(EmployeeStatus.TERMINATED, an.getStatus());
        assertEquals(VietnamTime.today(), an.getTerminationDate());
        assertFalse(an.getUser().getIsActive());
        assertTrue(result.isTerminationApplied());
        assertTrue(result.isEmailSent());
        assertTrue(fixture.first().getDecisionNoticeSentAt() != null);
        verify(accountMailService).sendResignationDecision(eq(an.getEmail()), eq("An Nguyễn"), eq("Store A"), any());
        verify(notificationService).notifyUsers(
                eq(List.of(managerUser)),
                eq("Đề xuất nghỉ việc đã được duyệt"),
                org.mockito.ArgumentMatchers.contains("An Nguyễn"),
                eq(NotificationType.RESIGNATION_PROPOSAL_APPROVED),
                eq("RESIGNATION_PROPOSAL"),
                eq(7));
    }

    @Test
    void rejectRequiresReasonAndDoesNotChangeOtherEmployee() {
        ProposalFixture fixture = pendingProposal();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rejectItem(admin, 7, 101, "  "));

        assertEquals(ResignationProposalServiceImpl.REASON_REQUIRED, ex.getMessage());
        assertEquals(ResignationItemStatus.PENDING, fixture.first().getStatus());
        assertEquals(ResignationItemStatus.PENDING, fixture.second().getStatus());
    }

    @Test
    void mixedDecisionsCompleteTheProposalAndKeepEachResult() {
        ProposalFixture fixture = pendingProposal();
        when(employeeRepository.findByIdWithStoreAccount(11)).thenReturn(Optional.of(an));
        when(employeeRepository.lockByIdForLeaveRequest(11)).thenReturn(Optional.of(an));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(accountMailService.sendResignationDecision(any(), any(), any(), any())).thenReturn(false);
        when(proposalRepository.findDetailById(7)).thenReturn(Optional.of(fixture.proposal()));

        service.approveItem(admin, 7, 101);
        ResignationDecisionResult rejected = service.rejectItem(admin, 7, 102, "Chưa đủ căn cứ");

        assertEquals(ResignationItemStatus.APPROVED, fixture.first().getStatus());
        assertEquals(ResignationItemStatus.REJECTED, fixture.second().getStatus());
        assertEquals("Chưa đủ căn cứ", fixture.second().getRejectReason());
        assertEquals(ResignationProposalStatus.COMPLETED, fixture.proposal().getStatus());
        assertFalse(rejected.isEmailSent());
        assertEquals(EmployeeStatus.ACTIVE, binh.getStatus());
        assertTrue(binh.getUser().getIsActive());
        assertEquals(EmployeeStatus.TERMINATED, an.getStatus());
        assertFalse(an.getUser().getIsActive());
        verify(accountMailService, never()).sendResignationDecision(eq(binh.getEmail()), any(), any(), any());

        ResignationProposalResponse detail = service.getForAdmin(admin, 7);
        assertEquals("Xử lý hỗn hợp", detail.getOutcomeLabel());
        assertEquals(1, detail.getApprovedCount());
        assertEquals(1, detail.getRejectedCount());
        assertEquals(ResignationItemStatus.APPROVED, detail.getItems().get(0).getStatus());
        assertEquals(ResignationItemStatus.REJECTED, detail.getItems().get(1).getStatus());
    }

    @Test
    void processedEmployeeCannotBeDecidedAgain() {
        ProposalFixture fixture = pendingProposal();
        fixture.first().setStatus(ResignationItemStatus.APPROVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rejectItem(admin, 7, 101, "Đổi ý"));

        assertEquals(ResignationProposalServiceImpl.ALREADY_PROCESSED, ex.getMessage());
        assertEquals(ResignationItemStatus.APPROVED, fixture.first().getStatus());
        assertEquals(ResignationItemStatus.PENDING, fixture.second().getStatus());
    }

    @Test
    void managerSeesEachEmployeeStatus() {
        ProposalFixture fixture = openProposal();
        fixture.first().setStatus(ResignationItemStatus.APPROVED);
        fixture.first().setApprovedTerminationDate(VietnamTime.today().plusDays(2));
        fixture.second().setStatus(ResignationItemStatus.REJECTED);
        fixture.second().setRejectReason("Không đồng ý");
        fixture.proposal().setStatus(ResignationProposalStatus.COMPLETED);
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(proposalRepository.findDetailById(7)).thenReturn(Optional.of(fixture.proposal()));

        ResignationProposalResponse detail = service.getMine(manager, 7);

        assertEquals(ResignationItemStatus.APPROVED, detail.getItems().get(0).getStatus());
        assertEquals(ResignationItemStatus.REJECTED, detail.getItems().get(1).getStatus());
        assertEquals("Không đồng ý", detail.getItems().get(1).getRejectReason());
        assertEquals("Xử lý hỗn hợp", detail.getOutcomeLabel());
    }

    @Test
    void approvalTerminatesAndDisablesTheAccountWithoutDeletingHistory() {
        ProposalFixture fixture = pendingProposal();
        when(employeeRepository.findByIdWithStoreAccount(11)).thenReturn(Optional.of(an));
        when(employeeRepository.lockByIdForLeaveRequest(11)).thenReturn(Optional.of(an));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(accountMailService.sendResignationDecision(any(), any(), any(), any())).thenReturn(true);
        Integer userId = an.getUser().getId();

        ResignationDecisionResult result = service.approveItem(admin, 7, 101);

        assertTrue(result.isTerminationApplied());
        assertEquals(EmployeeStatus.TERMINATED, an.getStatus());
        assertEquals(VietnamTime.today(), an.getTerminationDate());
        assertFalse(an.getUser().getIsActive());
        assertEquals(userId, an.getUser().getId());
        assertEquals(ResignationItemStatus.PENDING, fixture.second().getStatus());
        verify(userRepository).save(an.getUser());
        verify(employeeRepository).save(an);
        verify(accountMailService, times(1)).sendResignationDecision(any(), any(), any(), any());
    }

    @Test
    void failedDecisionEmailDoesNotUndoTermination() {
        ProposalFixture fixture = pendingProposal();
        when(employeeRepository.findByIdWithStoreAccount(11)).thenReturn(Optional.of(an));
        when(employeeRepository.lockByIdForLeaveRequest(11)).thenReturn(Optional.of(an));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmployee_Id(9)).thenReturn(Optional.of(managerUser));
        when(accountMailService.sendResignationDecision(any(), any(), any(), any())).thenReturn(false);

        ResignationDecisionResult result = service.approveItem(admin, 7, 101);

        assertFalse(result.isEmailSent());
        assertEquals(EmployeeStatus.TERMINATED, an.getStatus());
        assertFalse(an.getUser().getIsActive());
        assertNull(fixture.first().getDecisionNoticeSentAt());
        assertEquals(ResignationItemStatus.APPROVED, fixture.first().getStatus());
    }

    @Test
    void approvingAgainDoesNotSendAnotherEmail() {
        ProposalFixture fixture = pendingProposal();
        fixture.first().setStatus(ResignationItemStatus.APPROVED);
        fixture.first().setAppliedAt(VietnamTime.now());
        fixture.first().setDecisionNoticeSentAt(VietnamTime.now());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approveItem(admin, 7, 101));

        assertEquals(ResignationProposalServiceImpl.ALREADY_PROCESSED, ex.getMessage());
        verify(accountMailService, never()).sendResignationDecision(any(), any(), any(), any());
    }

    @Test
    void dueDateSchedulerTerminatesOnlyTheReachedEmployee() {
        an.setStatus(EmployeeStatus.ACTIVE);
        ResignationProposalItem due = ResignationProposalItem.builder()
                .id(101)
                .employee(an)
                .status(ResignationItemStatus.APPROVED)
                .approvedTerminationDate(VietnamTime.today())
                .build();
        when(itemRepository.findDueIds(ResignationItemStatus.APPROVED, VietnamTime.today())).thenReturn(List.of(101));
        when(itemRepository.lockById(101)).thenReturn(Optional.of(due));
        when(employeeRepository.lockByIdForLeaveRequest(11)).thenReturn(Optional.of(an));

        int updated = service.applyDueTerminations(VietnamTime.today());

        assertEquals(1, updated);
        assertEquals(EmployeeStatus.TERMINATED, an.getStatus());
        assertEquals(VietnamTime.today(), an.getTerminationDate());
        assertFalse(an.getUser().getIsActive());
        assertTrue(due.getAppliedAt() != null);
        verify(userRepository).save(an.getUser());
    }

    @Test
    void managerAndStaffCannotDecide() {
        BusinessException managerDenied = assertThrows(BusinessException.class,
                () -> service.approveItem(manager, 7, 101));
        BusinessException staffDenied = assertThrows(BusinessException.class,
                () -> service.create(staffActor, proposal(line(binh, VietnamTime.today().plusDays(1), "Lý do", null))));

        assertEquals("Chỉ Admin mới được duyệt hoặc từ chối đề xuất nghỉ việc.", managerDenied.getMessage());
        assertEquals("Chỉ Manager mới được thao tác đề xuất nghỉ việc của cửa hàng mình.", staffDenied.getMessage());
        verify(proposalRepository, never()).lockById(any());
    }

    @Test
    void managerCannotProposeThemselves() {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByStoreIdWithAccount(4)).thenReturn(List.of(managerEmployee));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(manager, proposal(
                line(managerEmployee, VietnamTime.today().plusDays(1), "Tự nghỉ", null))));

        assertTrue(ex.getMessage().contains(ResignationProposalServiceImpl.SELF_PROPOSAL));
    }

    private void stubCreate(List<Employee> employees) {
        when(storeRepository.findByManager_Id(9)).thenReturn(Optional.of(store));
        when(employeeRepository.findById(9)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findByStoreIdWithAccount(4)).thenReturn(employees);
        when(itemRepository.findOpenEmployeeIds(anyList(), eq(ResignationItemStatus.PENDING), eq(ResignationItemStatus.APPROVED)))
                .thenReturn(List.of());
        when(userRepository.findActiveByRoleName(RoleName.ADMIN)).thenReturn(List.of(adminUser));
        when(proposalRepository.save(any())).thenAnswer(invocation -> {
            ResignationProposal proposal = invocation.getArgument(0);
            proposal.setId(7);
            return proposal;
        });
    }

    private ProposalFixture pendingProposal() {
        ProposalFixture fixture = openProposal();
        when(proposalRepository.lockById(7)).thenReturn(Optional.of(fixture.proposal()));
        when(itemRepository.lockByProposalId(7)).thenReturn(List.of(fixture.first(), fixture.second()));
        return fixture;
    }

    private ProposalFixture openProposal() {
        ResignationProposal proposal = ResignationProposal.builder()
                .id(7)
                .store(store)
                .requestedBy(managerEmployee)
                .title("Giảm biên")
                .status(ResignationProposalStatus.PENDING)
                .items(new ArrayList<>())
                .build();
        ResignationProposalItem first = item(101, an, proposal);
        ResignationProposalItem second = item(102, binh, proposal);
        proposal.getItems().add(first);
        proposal.getItems().add(second);
        return new ProposalFixture(proposal, first, second);
    }

    private ResignationProposalItem item(int id, Employee employee, ResignationProposal proposal) {
        ResignationProposalItem item = ResignationProposalItem.builder()
                .id(id)
                .employee(employee)
                .position("Nhân viên")
                .proposedTerminationDate(VietnamTime.today().plusDays(3))
                .reason("Lý do")
                .status(ResignationItemStatus.PENDING)
                .sortOrder(id)
                .build();
        item.setProposal(proposal);
        return item;
    }

    private Employee staff(int id, String name, Store workplace, Role role) {
        Employee employee = Employee.builder()
                .id(id)
                .fullName(name)
                .email(name.toLowerCase().replace(" ", "") + "@shop.vn")
                .store(workplace)
                .status(EmployeeStatus.ACTIVE)
                .hireDate(VietnamTime.today().minusYears(1))
                .build();
        User user = User.builder().id(100 + id).username(employee.getEmail()).passwordHash("x")
                .role(role).employee(employee).isActive(true).build();
        employee.setUser(user);
        return employee;
    }

    private CreateResignationProposalRequest proposal(ResignationItemRequest... lines) {
        return CreateResignationProposalRequest.builder()
                .title("Giảm biên chế")
                .note("Ghi chú chung")
                .items(List.of(lines))
                .build();
    }

    private ResignationItemRequest line(Employee employee, LocalDate date, String reason, String note) {
        return ResignationItemRequest.builder()
                .employeeId(employee.getId())
                .proposedTerminationDate(date)
                .reason(reason)
                .note(note)
                .build();
    }

    private record ProposalFixture(ResignationProposal proposal,
                                   ResignationProposalItem first,
                                   ResignationProposalItem second) {
    }
}
