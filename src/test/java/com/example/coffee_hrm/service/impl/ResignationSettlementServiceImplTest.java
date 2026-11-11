package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.PaymentMethod;
import com.example.coffee_hrm.common.enums.PayrollStatus;
import com.example.coffee_hrm.common.enums.ResignationItemStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.PayrollPaymentForm;
import com.example.coffee_hrm.dto.response.ResignationSettlementView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.MonthlyAttendanceReport;
import com.example.coffee_hrm.entity.MonthlyPayroll;
import com.example.coffee_hrm.entity.PayrollEmployeeLine;
import com.example.coffee_hrm.entity.PayrollPayment;
import com.example.coffee_hrm.entity.PayrollShiftLine;
import com.example.coffee_hrm.entity.ResignationProposal;
import com.example.coffee_hrm.entity.ResignationProposalItem;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.MonthlyPayrollRepository;
import com.example.coffee_hrm.repository.PayrollPaymentRepository;
import com.example.coffee_hrm.repository.ResignationProposalItemRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.PayrollPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Đối soát và thanh toán lương trước khi duyệt chấm dứt hợp đồng. */
@ExtendWith(MockitoExtension.class)
class ResignationSettlementServiceImplTest {

    @Mock
    private MonthlyPayrollRepository payrollRepository;
    @Mock
    private MonthlyAttendanceReportRepository reportRepository;
    @Mock
    private PayrollPaymentRepository paymentRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private EmploymentContractRepository contractRepository;
    @Mock
    private ResignationProposalItemRepository itemRepository;
    @Mock
    private PayrollPaymentService payrollPaymentService;

    @InjectMocks
    private ResignationSettlementServiceImpl service;

    private Store store;
    private Employee an;
    private AuthenticatedUser admin;
    private final List<PayrollEmployeeLine> lines = new ArrayList<>();
    private final List<ShiftAssignment> assignments = new ArrayList<>();
    private final List<PayrollPayment> payments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1).storeName("Cầu Giấy").build();
        an = Employee.builder().id(11).fullName("Nguyễn An").store(store).build();
        Role adminRole = Role.builder().id(1).roleName(RoleName.ADMIN).build();
        admin = AuthenticatedUser.from(User.builder().id(1).username("admin@shop.vn").passwordHash("x")
                .role(adminRole).isActive(true).build());
        lenient().when(payrollRepository.findLinesByEmployeeId(11)).thenAnswer(invocation -> new ArrayList<>(lines));
        lenient().when(shiftAssignmentRepository.findPublishedAssignedForEmployeeUntil(eq(11), any(), eq(AssignmentStatus.ASSIGNED)))
                .thenAnswer(invocation -> new ArrayList<>(assignments));
        lenient().when(paymentRepository.findByEmployeeId(11)).thenAnswer(invocation -> new ArrayList<>(payments));
    }

    @Test
    void employeeWithoutAnySalaryPeriodCanBeApproved() {
        ResignationSettlementView view = service.settlement(admin, 11);

        assertTrue(view.isEligible());
        assertEquals(ResignationSettlementView.READY, view.getStatusKey());
        assertEquals(0, view.getPeriodCount());
        assertEquals("0 đ", view.getRemainingLabel());
        assertDoesNotThrow(() -> service.assertSettled(11, "Nguyễn An"));
    }

    @Test
    void unpaidApprovedSalaryBlocksApprovalAndShowsTheMissingAmount() {
        MonthlyPayroll payroll = finalizedOctober();
        lines.add(line(payroll, "225000", 501));
        assignments.add(assignment(501, LocalDate.of(2026, 10, 3)));

        ResignationSettlementView view = service.settlement(admin, 11);

        assertFalse(view.isEligible());
        assertEquals(ResignationSettlementView.OWED, view.getStatusKey());
        assertEquals("225.000 đ", view.getTotalLabel());
        assertEquals("0 đ", view.getPaidLabel());
        assertEquals("225.000 đ", view.getRemainingLabel());
        ResignationSettlementView.Period period = view.getPeriods().getFirst();
        assertEquals("UNPAID", period.getStatusKey());
        assertTrue(period.isPayable());
        assertEquals(4, period.getReportId());

        BusinessException blocked = assertThrows(BusinessException.class, () -> service.assertSettled(11, "Nguyễn An"));
        assertTrue(blocked.getMessage().contains("225.000 đ"));
        assertTrue(blocked.getMessage().contains("Nguyễn An"));
    }

    @Test
    void partialPaymentStillBlocksApprovalWithTheRemainingAmount() {
        MonthlyPayroll payroll = finalizedOctober();
        lines.add(line(payroll, "225000", 501));
        assignments.add(assignment(501, LocalDate.of(2026, 10, 3)));
        payments.add(payment(1, "100000"));

        ResignationSettlementView view = service.settlement(admin, 11);

        assertFalse(view.isEligible());
        assertEquals("PARTIAL", view.getPeriods().getFirst().getStatusKey());
        assertEquals("100.000 đ", view.getPaidLabel());
        assertEquals("125.000 đ", view.getRemainingLabel());
        assertEquals(1, view.getPayments().size());
        assertThrows(BusinessException.class, () -> service.assertSettled(11, "Nguyễn An"));
    }

    @Test
    void fullyPaidSalaryAllowsApprovalAndLocksTheReportsWhileChecking() {
        MonthlyPayroll payroll = finalizedOctober();
        lines.add(line(payroll, "225000", 501));
        assignments.add(assignment(501, LocalDate.of(2026, 10, 3)));
        payments.add(payment(1, "100000"));
        payments.add(payment(2, "125000"));

        ResignationSettlementView view = service.settlement(admin, 11);
        assertTrue(view.isEligible());
        assertEquals("PAID", view.getPeriods().getFirst().getStatusKey());

        assertDoesNotThrow(() -> service.assertSettled(11, "Nguyễn An"));
        verify(reportRepository).lockCurrent(1, 2026, 10);
    }

    @Test
    void periodNotYetCalculatedOrNotLockedBlocksApproval() {
        // Tháng 10: bảng lương mới "Đã tính", báo cáo chưa gửi. Tháng 11: có ca nhưng chưa tính lương.
        MonthlyPayroll draft = MonthlyPayroll.builder().id(31).store(store).payrollYear(2026).payrollMonth(10)
                .status(PayrollStatus.DA_TINH).employeeLines(new ArrayList<>()).build();
        lines.add(line(draft, "90000", 501));
        assignments.add(assignment(501, LocalDate.of(2026, 10, 3)));
        assignments.add(assignment(601, LocalDate.of(2026, 11, 2)));

        ResignationSettlementView view = service.settlement(admin, 11);

        assertFalse(view.isEligible());
        assertEquals(ResignationSettlementView.PENDING, view.getStatusKey());
        assertEquals(2, view.getPendingCount());
        assertEquals("0 đ", view.getTotalLabel());
        assertTrue(view.getPeriods().stream().noneMatch(ResignationSettlementView.Period::isPayable));
        assertTrue(view.getReasons().getFirst().contains("Tháng 11/2026"));
        assertTrue(view.getPeriods().getFirst().getNote().contains("chưa tính lương"));
        assertThrows(BusinessException.class, () -> service.assertSettled(11, "Nguyễn An"));
    }

    @Test
    void shiftsWorkedAfterThePayrollWasLockedKeepThePeriodOpen() {
        MonthlyPayroll payroll = finalizedOctober();
        lines.add(line(payroll, "225000", 501));
        assignments.add(assignment(501, LocalDate.of(2026, 10, 3)));
        assignments.add(assignment(502, LocalDate.of(2026, 10, 28)));
        payments.add(payment(1, "225000"));

        ResignationSettlementView view = service.settlement(admin, 11);

        assertFalse(view.isEligible());
        assertEquals(1, view.getPendingCount());
        assertTrue(view.getPeriods().getFirst().getNote().contains("1 ca"));
    }

    @Test
    void payingFromTheProposalReusesTheExistingPayrollPayment() {
        MonthlyPayroll payroll = finalizedOctober();
        lines.add(line(payroll, "225000", 501));
        assignments.add(assignment(501, LocalDate.of(2026, 10, 3)));
        when(itemRepository.lockById(101)).thenReturn(Optional.of(item(ResignationItemStatus.PENDING)));
        PayrollPaymentForm form = new PayrollPaymentForm();
        when(payrollPaymentService.payEmployee(admin, 4, 11, form, null)).thenReturn("225.000 đ");

        assertEquals("225.000 đ", service.pay(admin, 7, 101, 4, form, null));
        verify(payrollPaymentService).payEmployee(admin, 4, 11, form, null);
    }

    @Test
    void failedPaymentIsNotRecordedAndTheErrorIsReturned() {
        MonthlyPayroll payroll = finalizedOctober();
        lines.add(line(payroll, "225000", 501));
        assignments.add(assignment(501, LocalDate.of(2026, 10, 3)));
        when(itemRepository.lockById(101)).thenReturn(Optional.of(item(ResignationItemStatus.PENDING)));
        PayrollPaymentForm form = new PayrollPaymentForm();
        when(payrollPaymentService.payEmployee(admin, 4, 11, form, null))
                .thenThrow(new BusinessException("Số tiền thanh toán vượt quá số còn phải trả (225.000 đ)."));

        BusinessException failed = assertThrows(BusinessException.class, () -> service.pay(admin, 7, 101, 4, form, null));

        assertTrue(failed.getMessage().contains("vượt quá"));
        // Không có giao dịch mới nào: vẫn còn nợ đủ 225.000.
        assertEquals("225.000 đ", service.settlement(admin, 11).getRemainingLabel());
    }

    @Test
    void cannotPayFromAProposalLineThatWasAlreadyRejectedOrForAnUnfinishedPeriod() {
        when(itemRepository.lockById(101)).thenReturn(Optional.of(item(ResignationItemStatus.REJECTED)));
        assertThrows(BusinessException.class, () -> service.pay(admin, 7, 101, 4, new PayrollPaymentForm(), null));

        MonthlyPayroll draft = MonthlyPayroll.builder().id(31).store(store).payrollYear(2026).payrollMonth(10)
                .status(PayrollStatus.DA_TINH).employeeLines(new ArrayList<>()).build();
        lines.add(line(draft, "90000", 501));
        when(itemRepository.lockById(102)).thenReturn(Optional.of(item(ResignationItemStatus.PENDING)));
        assertThrows(BusinessException.class, () -> service.pay(admin, 7, 102, 4, new PayrollPaymentForm(), null));

        verify(payrollPaymentService, never()).payEmployee(any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void onlyAdminCanSeeTheSettlement() {
        Role staffRole = Role.builder().id(3).roleName(RoleName.STAFF).build();
        AuthenticatedUser staff = AuthenticatedUser.from(User.builder().id(5).username("s@shop.vn").passwordHash("x")
                .role(staffRole).employee(an).isActive(true).build());
        assertThrows(BusinessException.class, () -> service.settlement(staff, 11));
    }

    // ------------------------------------------------------------------ dữ liệu mẫu

    private MonthlyAttendanceReport report;

    private MonthlyPayroll finalizedOctober() {
        report = MonthlyAttendanceReport.builder().id(4).store(store).reportYear(2026).reportMonth(10)
                .status(AttendanceReportStatus.APPROVED).versionNumber(1)
                .submittedAt(LocalDateTime.of(2026, 11, 1, 9, 0)).build();
        lenient().when(reportRepository.findCurrent(1, 2026, 10)).thenReturn(Optional.of(report));
        lenient().when(reportRepository.lockCurrent(1, 2026, 10)).thenReturn(Optional.of(report));
        return MonthlyPayroll.builder().id(30).store(store).payrollYear(2026).payrollMonth(10)
                .status(PayrollStatus.DA_CHOT).employeeLines(new ArrayList<>()).build();
    }

    private PayrollEmployeeLine line(MonthlyPayroll payroll, String salary, Integer... assignmentIds) {
        PayrollEmployeeLine line = PayrollEmployeeLine.builder().payroll(payroll).employee(an)
                .expectedSalary(new BigDecimal(salary)).shiftLines(new ArrayList<>()).build();
        for (Integer id : assignmentIds) {
            line.getShiftLines().add(PayrollShiftLine.builder().assignmentId(id).build());
        }
        payroll.getEmployeeLines().add(line);
        return line;
    }

    private ShiftAssignment assignment(int id, LocalDate day) {
        Shift shift = Shift.builder().id(1).store(store).build();
        return ShiftAssignment.builder().id(id).employee(an).shift(shift).workDate(day)
                .status(AssignmentStatus.ASSIGNED).isPublished(true).build();
    }

    private PayrollPayment payment(int id, String amount) {
        return PayrollPayment.builder().id(id).report(report).employee(an).payrollYear(2026).payrollMonth(10)
                .amount(new BigDecimal(amount)).paidDate(LocalDate.of(2026, 11, 2)).method(PaymentMethod.CHUYEN_KHOAN)
                .paidByName("Admin").requestKey("k" + id).build();
    }

    private ResignationProposalItem item(ResignationItemStatus status) {
        ResignationProposal proposal = ResignationProposal.builder().id(7).store(store).build();
        return ResignationProposalItem.builder().id(101).proposal(proposal).employee(an).status(status).build();
    }
}
