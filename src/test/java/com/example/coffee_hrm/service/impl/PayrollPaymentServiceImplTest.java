package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.PaymentMethod;
import com.example.coffee_hrm.common.enums.PayrollStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.PayrollPaymentForm;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.PayrollPaymentViews;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.MonthlyAttendanceReport;
import com.example.coffee_hrm.entity.MonthlyPayroll;
import com.example.coffee_hrm.entity.PayrollEmployeeLine;
import com.example.coffee_hrm.entity.PayrollPayment;
import com.example.coffee_hrm.entity.PayrollShiftLine;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.MonthlyPayrollRepository;
import com.example.coffee_hrm.repository.PayrollPaymentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.PayrollPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Admin duyệt rồi ghi nhận thanh toán lương; không trả quá, không trả trùng, không trả báo cáo chưa duyệt. */
@ExtendWith(MockitoExtension.class)
class PayrollPaymentServiceImplTest {

    @Mock
    private MonthlyAttendanceReportRepository reportRepository;
    @Mock
    private MonthlyPayrollRepository payrollRepository;
    @Mock
    private PayrollPaymentRepository paymentRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PayrollProofStore proofStore;

    @InjectMocks
    private PayrollPaymentServiceImpl service;

    private Store store;
    private Employee an;
    private Employee binh;
    private MonthlyAttendanceReport report;
    private MonthlyPayroll payroll;
    private final List<PayrollPayment> payments = new ArrayList<>();
    private AuthenticatedUser admin;
    private AuthenticatedUser manager;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1).storeName("Cầu Giấy").build();
        an = Employee.builder().id(11).fullName("Nguyễn An").store(store).build();
        binh = Employee.builder().id(12).fullName("Trần Bình").store(store).build();
        report = MonthlyAttendanceReport.builder()
                .id(4).store(store).reportYear(2026).reportMonth(10).status(AttendanceReportStatus.APPROVED)
                .versionNumber(1).submittedAt(LocalDateTime.of(2026, 11, 1, 9, 0)).build();
        payroll = MonthlyPayroll.builder()
                .id(30).store(store).payrollYear(2026).payrollMonth(10).status(PayrollStatus.DA_CHOT)
                .employeeLines(new ArrayList<>()).build();
        // An: 2 ca × 5 giờ × 25.000; ca thứ hai đi muộn bị trừ 1 giờ → 225.000 thực nhận.
        payroll.getEmployeeLines().add(line(an, "225000",
                shift(5, 5, true, "125000"), shift(5, 4, true, "100000")));
        payroll.getEmployeeLines().add(line(binh, "100000", shift(4, 4, true, "100000")));
        admin = user(1, RoleName.ADMIN, null);
        manager = user(9, RoleName.MANAGER, 9);

        lenient().when(reportRepository.findByIdDetailed(4)).thenReturn(Optional.of(report));
        lenient().when(payrollRepository.findPeriod(1, 2026, 10)).thenReturn(Optional.of(payroll));
        lenient().when(paymentRepository.findByReportId(4)).thenAnswer(invocation -> new ArrayList<>(payments));
        lenient().when(paymentRepository.saveAndFlush(any())).thenAnswer(invocation -> remember(invocation.getArgument(0)));
        lenient().when(paymentRepository.save(any())).thenAnswer(invocation -> remember(invocation.getArgument(0)));
        lenient().when(userRepository.findByEmployee_Id(any())).thenReturn(Optional.of(User.builder().id(50).build()));
    }

    @Test
    void salaryIsSplitIntoBaseAndDeductionWithoutChangingTheNetPay() {
        PayrollPaymentViews.ReportPayroll view = service.reportPayroll(admin, 4);

        PayrollPaymentViews.EmployeeRow first = view.getEmployees().getFirst();
        assertEquals("Nguyễn An", first.getEmployeeName());
        assertEquals("250.000 đ", first.getBaseLabel());
        assertEquals("25.000 đ", first.getDeductionLabel());
        assertEquals("0 đ", first.getAllowanceLabel());
        assertEquals("225.000 đ", first.getNetLabel());
        assertEquals("325.000 đ", view.getTotalLabel());
        assertEquals("CHUA_THANH_TOAN", view.getPaymentStatusKey());
        assertEquals(2, view.getUnpaidCount());
        assertTrue(view.isCanPay());
    }

    @Test
    void payingOneEmployeeRecordsATransactionAndNotifiesThem() {
        PayrollPaymentForm form = form("100000");
        form.setTransactionCode("FT123");
        form.setNote("Trả đợt 1");

        String amount = service.payEmployee(admin, 4, 11, form, null);

        assertEquals("100.000 đ", amount);
        verify(reportRepository).findByIdForUpdate(4);
        PayrollPayment saved = payments.getFirst();
        assertEquals(an, saved.getEmployee());
        assertEquals(report, saved.getReport());
        assertEquals(payroll, saved.getPayroll());
        assertEquals(10, saved.getPayrollMonth());
        assertEquals(0, new BigDecimal("100000").compareTo(saved.getAmount()));
        assertEquals(PaymentMethod.CHUYEN_KHOAN, saved.getMethod());
        assertEquals("FT123", saved.getTransactionCode());
        assertEquals(admin.getDisplayName(), saved.getPaidByName());
        verify(notificationService).notifyUsers(anyList(), eq("Bạn đã được thanh toán lương"), any(),
                eq(NotificationType.PAYROLL_PAID), eq("PAYROLL_PAYMENT"), any());

        // Trả một phần: vẫn "Chưa thanh toán", còn 125.000.
        PayrollPaymentViews.EmployeeRow row = service.reportPayroll(admin, 4).getEmployees().getFirst();
        assertEquals("CHUA_THANH_TOAN", row.getPaymentStatusKey());
        assertEquals("125.000 đ", row.getRemainingLabel());

        PayrollPaymentForm rest = form("125000");
        rest.setRequestKey("key-2");
        service.payEmployee(admin, 4, 11, rest, null);
        row = service.reportPayroll(admin, 4).getEmployees().getFirst();
        assertEquals("DA_THANH_TOAN", row.getPaymentStatusKey());
        assertEquals("0 đ", row.getRemainingLabel());
    }

    @Test
    void cannotPayMoreThanWhatIsLeftOrPayAFullyPaidEmployee() {
        BusinessException over = assertThrows(BusinessException.class,
                () -> service.payEmployee(admin, 4, 11, form("225001"), null));
        assertTrue(over.getMessage().contains("vượt quá"));

        payments.add(PayrollPayment.builder().id(1).report(report).employee(an)
                .amount(new BigDecimal("225000")).paidDate(LocalDate.of(2026, 11, 2)).build());
        BusinessException done = assertThrows(BusinessException.class,
                () -> service.payEmployee(admin, 4, 11, form("1"), null));
        assertTrue(done.getMessage().contains("thanh toán đủ"));
        assertEquals(1, payments.size());
    }

    @Test
    void sameConfirmationIsNotRecordedTwice() {
        when(paymentRepository.existsByRequestKey("key-1")).thenReturn(true);

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.payEmployee(admin, 4, 11, form("1000"), null));

        assertTrue(duplicate.getMessage().contains("đã được ghi nhận"));
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateIsRejectedByTheDatabaseAndTheProofIsRemoved() {
        when(proofStore.save(any())).thenReturn(new PayrollProofStore.Stored("abc.png", "ck.png"));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("UX")).when(paymentRepository).saveAndFlush(any());

        assertThrows(BusinessException.class, () -> service.payEmployee(admin, 4, 11, form("1000"), null));

        verify(proofStore).delete("abc.png");
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reportMustBeApprovedBeforePaying() {
        report.setStatus(AttendanceReportStatus.SUBMITTED);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.payEmployee(admin, 4, 11, form("1000"), null));
        assertTrue(error.getMessage().contains("đã được Admin duyệt"));
        assertThrows(BusinessException.class, () -> service.payAll(admin, 4, form(null), null));
        assertTrue(payments.isEmpty());
        assertEquals(false, service.reportPayroll(admin, 4).isCanPay());
    }

    @Test
    void onlyAdminCanSeeOrConfirmPayments() {
        assertThrows(BusinessException.class, () -> service.payEmployee(manager, 4, 11, form("1000"), null));
        assertThrows(BusinessException.class, () -> service.payAll(manager, 4, form(null), null));
        assertThrows(BusinessException.class, () -> service.reportPayroll(manager, 4));
        assertThrows(BusinessException.class, () -> service.proof(manager, 4, 1));
        assertTrue(payments.isEmpty());
    }

    @Test
    void payingTheWholeReportPaysEveryoneWhoIsStillOwed() {
        payments.add(PayrollPayment.builder().id(1).report(report).employee(an)
                .amount(new BigDecimal("25000")).paidDate(LocalDate.of(2026, 11, 2)).build());
        PayrollPaymentForm form = form(null);
        form.setMethod("TIEN_MAT");
        form.setExpectedCount(2);
        form.setExpectedTotal("300000.00");

        PayrollPaymentService.PayAllResult result = service.payAll(admin, 4, form, null);

        assertEquals(2, result.employees());
        assertEquals("300.000 đ", result.totalLabel());
        assertEquals(3, payments.size());
        assertEquals(0, new BigDecimal("200000").compareTo(payments.get(1).getAmount()));
        assertEquals(0, new BigDecimal("100000").compareTo(payments.get(2).getAmount()));
        verify(notificationService, times(2)).notifyUsers(anyList(), any(), any(),
                eq(NotificationType.PAYROLL_PAID), any(), any());
        assertEquals("DA_THANH_TOAN", service.reportPayroll(admin, 4).getPaymentStatusKey());

        PayrollPaymentForm again = form(null);
        again.setRequestKey("key-3");
        BusinessException none = assertThrows(BusinessException.class, () -> service.payAll(admin, 4, again, null));
        assertTrue(none.getMessage().contains("Không còn nhân viên"));
    }

    @Test
    void payAllStopsWhenTheAmountChangedSinceTheAdminConfirmed() {
        PayrollPaymentForm form = form(null);
        form.setExpectedTotal("999000");

        BusinessException error = assertThrows(BusinessException.class, () -> service.payAll(admin, 4, form, null));

        assertTrue(error.getMessage().contains("vừa thay đổi"));
        assertTrue(payments.isEmpty());
    }

    @Test
    void paymentDateCannotBeInTheFutureAndMethodIsRequired() {
        PayrollPaymentForm future = form("1000");
        future.setPaidDate(VietnamTime.today().plusDays(1).toString());
        assertThrows(BusinessException.class, () -> service.payEmployee(admin, 4, 11, future, null));

        PayrollPaymentForm noMethod = form("1000");
        noMethod.setMethod("BITCOIN");
        assertThrows(BusinessException.class, () -> service.payEmployee(admin, 4, 11, noMethod, null));
        assertTrue(payments.isEmpty());
    }

    @Test
    void staffSeesOnlyApprovedPeriodsOfTheirOwn() {
        AuthenticatedUser staff = user(20, RoleName.STAFF, 11);
        PayrollEmployeeLine anLine = payroll.getEmployeeLines().getFirst();
        anLine.setPayroll(payroll);
        when(payrollRepository.findLinesByEmployeeId(11)).thenReturn(List.of(anLine));
        when(reportRepository.findCurrent(1, 2026, 10)).thenReturn(Optional.of(report));
        when(paymentRepository.findByEmployeeId(11)).thenReturn(List.of(PayrollPayment.builder().id(1).report(report)
                .employee(an).payrollYear(2026).payrollMonth(10).amount(new BigDecimal("225000"))
                .paidDate(LocalDate.of(2026, 11, 5)).method(PaymentMethod.TIEN_MAT).build()));

        List<PayrollPaymentViews.StaffPeriod> periods = service.myPeriods(staff);

        assertEquals(1, periods.size());
        PayrollPaymentViews.StaffPeriod period = periods.getFirst();
        assertEquals("Tháng 10/2026", period.getPeriodLabel());
        assertEquals("225.000 đ", period.getNetLabel());
        assertEquals("225.000 đ", period.getReceivedLabel());
        assertEquals("0 đ", period.getRemainingLabel());
        assertEquals("Đã thanh toán", period.getPaymentStatusLabel());
        assertEquals(LocalDate.of(2026, 11, 5), period.getLastPaidDate());

        report.setStatus(AttendanceReportStatus.SUBMITTED);
        assertTrue(service.myPeriods(staff).isEmpty());
    }

    @Test
    void adminListShowsMoneyPerStoreAndFiltersByPaymentStatusAndName() {
        when(payrollRepository.findByPeriod(2026, 10)).thenReturn(List.of(payroll));
        when(paymentRepository.findByReportIds(any())).thenReturn(List.of(PayrollPayment.builder().id(1).report(report)
                .employee(an).amount(new BigDecimal("25000")).paidDate(LocalDate.of(2026, 11, 2)).build()));
        AdminAttendanceReportPage page = AdminAttendanceReportPage.builder()
                .year(2026).month(10).periodLabel("10/2026").filterStatus("all").totalStores(2)
                .submittedStores(1).unsentStores(1).stores(List.of())
                .rows(List.of(
                        AdminAttendanceReportPage.Row.builder().reportId(4).storeId(1).storeName("Cầu Giấy")
                                .periodLabel("10/2026").statusCode("APPROVED").statusLabel("Đã duyệt")
                                .submitted(true).senderName("Quý").build(),
                        AdminAttendanceReportPage.Row.builder().storeId(2).storeName("Hoàn Kiếm")
                                .periodLabel("10/2026").statusCode("UNSENT").statusLabel("Chưa gửi").build()))
                .build();

        PayrollPaymentViews.ReportList list = service.adminReports(admin, page, null, null);

        assertEquals(2, list.getRows().size());
        PayrollPaymentViews.ReportRow cauGiay = list.getRows().getFirst();
        assertEquals(2, cauGiay.getEmployeeCount());
        assertEquals("325.000 đ", cauGiay.getTotalLabel());
        assertEquals("25.000 đ", cauGiay.getPaidLabel());
        assertEquals("300.000 đ", cauGiay.getRemainingLabel());
        assertEquals("Chưa thanh toán", cauGiay.getPaymentStatusLabel());
        assertEquals("—", list.getRows().get(1).getTotalLabel());
        assertEquals("300.000 đ", list.getTotalRemainingLabel());

        assertEquals(1, service.adminReports(admin, page, "CHUA_THANH_TOAN", null).getRows().size());
        assertEquals(0, service.adminReports(admin, page, "DA_THANH_TOAN", null).getRows().size());
        assertEquals("Hoàn Kiếm", service.adminReports(admin, page, null, "hoan kiem").getRows().getFirst().getStoreName());
        assertThrows(BusinessException.class, () -> service.adminReports(manager, page, null, null));
    }

    private PayrollPayment remember(PayrollPayment payment) {
        payment.setId(payments.size() + 1);
        payments.add(payment);
        return payment;
    }

    private PayrollPaymentForm form(String amount) {
        PayrollPaymentForm form = new PayrollPaymentForm();
        form.setAmount(amount);
        form.setMethod("CHUYEN_KHOAN");
        form.setPaidDate(VietnamTime.today().toString());
        form.setRequestKey("key-1");
        return form;
    }

    private PayrollEmployeeLine line(Employee employee, String net, PayrollShiftLine... shifts) {
        PayrollEmployeeLine line = PayrollEmployeeLine.builder()
                .employee(employee).assignedShiftCount(shifts.length).validShiftCount(shifts.length)
                .reviewShiftCount(0).payableHours(0).hourlyRate(new BigDecimal("25000"))
                .expectedSalary(new BigDecimal(net)).shiftLines(new ArrayList<>()).build();
        for (PayrollShiftLine shift : shifts) {
            line.addShift(shift);
        }
        return line;
    }

    private PayrollShiftLine shift(int scheduled, int payable, boolean paid, String salary) {
        return PayrollShiftLine.builder()
                .workDate(LocalDate.of(2026, 10, 6)).shiftName("Ca sáng")
                .scheduledStartTime(LocalTime.of(7, 0)).scheduledEndTime(LocalTime.of(7 + scheduled, 0))
                .checkInTime(LocalDate.of(2026, 10, 6).atTime(7, 0))
                .scheduledHours(scheduled).payableHours(payable).hourlyRate(new BigDecimal("25000"))
                .shiftSalary(new BigDecimal(salary)).payable(paid).build();
    }

    private AuthenticatedUser user(int id, RoleName role, Integer employeeId) {
        Employee employee = employeeId == null ? null : Employee.builder().id(employeeId).fullName("Người dùng").build();
        return AuthenticatedUser.from(User.builder()
                .id(id).username("u" + id).passwordHash("hash")
                .role(Role.builder().id(id).roleName(role).build())
                .employee(employee).isActive(true).build());
    }
}
