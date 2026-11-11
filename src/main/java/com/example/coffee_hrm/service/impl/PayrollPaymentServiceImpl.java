package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.PaymentMethod;
import com.example.coffee_hrm.common.enums.PayrollPaymentStatus;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Thanh toán lương theo báo cáo cửa hàng.
 *
 * <p>Số phải trả của từng nhân viên là "Tổng lương thực nhận" trong bảng lương đã chốt khi Manager gửi báo cáo
 * (công thức tính lương không đổi). Lương cơ bản = giờ theo lịch × mức lương/giờ của các ca đủ điều kiện;
 * khấu trừ = phần giờ bị trừ do đi muộn/về sớm; hệ thống hiện chưa có phụ cấp nên phụ cấp = 0.
 *
 * <p>Mọi giao dịch đều khoá dòng báo cáo, kiểm tra lại số còn phải trả và lưu thành bản ghi mới; không sửa hay xoá
 * giao dịch cũ.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollPaymentServiceImpl implements PayrollPaymentService {

    static final String REFERENCE = "PAYROLL_PAYMENT";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MonthlyAttendanceReportRepository reportRepository;
    private final MonthlyPayrollRepository payrollRepository;
    private final PayrollPaymentRepository paymentRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PayrollProofStore proofStore;

    // ------------------------------------------------------------------ Admin: danh sách báo cáo

    @Override
    public PayrollPaymentViews.ReportList adminReports(AuthenticatedUser admin,
                                                       AdminAttendanceReportPage page,
                                                       String paymentStatus,
                                                       String query) {
        assertAdmin(admin);
        PayrollPaymentStatus paymentFilter = PayrollPaymentStatus.parse(paymentStatus);
        String search = query == null ? "" : query.trim();
        String needle = fold(search);
        Map<Integer, MonthlyPayroll> payrolls = new HashMap<>();
        for (MonthlyPayroll payroll : payrollRepository.findByPeriod(page.getYear(), page.getMonth())) {
            if (payroll.getStore() != null) {
                payrolls.put(payroll.getStore().getId(), payroll);
            }
        }
        List<Integer> reportIds = page.getRows().stream()
                .map(AdminAttendanceReportPage.Row::getReportId)
                .filter(Objects::nonNull)
                .toList();
        Map<Integer, List<PayrollPayment>> paymentsByReport = new HashMap<>();
        if (!reportIds.isEmpty()) {
            for (PayrollPayment payment : paymentRepository.findByReportIds(reportIds)) {
                paymentsByReport.computeIfAbsent(payment.getReport().getId(), ignored -> new ArrayList<>()).add(payment);
            }
        }
        List<PayrollPaymentViews.ReportRow> rows = new ArrayList<>();
        BigDecimal totalApproved = BigDecimal.ZERO;
        BigDecimal paidApproved = BigDecimal.ZERO;
        int waiting = 0;
        int approved = 0;
        for (AdminAttendanceReportPage.Row row : page.getRows()) {
            MonthlyPayroll payroll = row.isSubmitted() ? payrolls.get(row.getStoreId()) : null;
            Totals totals = payroll == null
                    ? Totals.EMPTY
                    : totals(payroll, paymentsByReport.getOrDefault(row.getReportId(), List.of()));
            PayrollPaymentStatus status = row.isSubmitted() ? totals.status() : null;
            if ("WAITING".equals(row.getStatusCode())) {
                waiting++;
            }
            if ("APPROVED".equals(row.getStatusCode())) {
                approved++;
                totalApproved = totalApproved.add(totals.total());
                paidApproved = paidApproved.add(totals.paid());
            }
            if (paymentFilter != null && paymentFilter != status) {
                continue;
            }
            if (!needle.isEmpty() && !fold(row.getStoreName()).contains(needle)) {
                continue;
            }
            rows.add(PayrollPaymentViews.ReportRow.builder()
                    .reportId(row.getReportId())
                    .storeId(row.getStoreId())
                    .storeName(row.getStoreName())
                    .periodLabel(row.getPeriodLabel())
                    .senderName(row.getSenderName())
                    .submittedAt(row.getSubmittedAt())
                    .submitted(row.isSubmitted())
                    .stale(row.isStale())
                    .hasPayroll(payroll != null)
                    .employeeCount(totals.employees())
                    .totalLabel(payroll == null ? "—" : money(totals.total()))
                    .paidLabel(payroll == null ? "—" : money(totals.paid()))
                    .remainingLabel(payroll == null ? "—" : money(totals.remaining()))
                    .statusCode(row.getStatusCode())
                    .statusLabel(row.getStatusLabel())
                    .statusCss(row.getStatusCss())
                    .paymentStatusKey(status == null ? null : status.name())
                    .paymentStatusLabel(status == null ? "—" : status.label())
                    .build());
        }
        return PayrollPaymentViews.ReportList.builder()
                .year(page.getYear())
                .month(page.getMonth())
                .periodLabel(page.getPeriodLabel())
                .filterStoreId(page.getFilterStoreId())
                .filterStatus(page.getFilterStatus())
                .filterPaymentStatus(paymentFilter == null ? "" : paymentFilter.name())
                .query(search)
                .totalStores(page.getTotalStores())
                .waitingReports(waiting)
                .approvedReports(approved)
                .unsentStores(page.getUnsentStores())
                .totalPayrollLabel(money(totalApproved))
                .totalPaidLabel(money(paidApproved))
                .totalRemainingLabel(money(totalApproved.subtract(paidApproved).max(BigDecimal.ZERO)))
                .stores(page.getStores().stream()
                        .map(store -> PayrollPaymentViews.StoreOption.builder()
                                .storeId(store.getStoreId())
                                .storeName(store.getStoreName())
                                .build())
                        .toList())
                .rows(rows)
                .build();
    }

    // ------------------------------------------------------------------ Admin: chi tiết báo cáo

    @Override
    public PayrollPaymentViews.ReportPayroll reportPayroll(AuthenticatedUser admin, int reportId) {
        assertAdmin(admin);
        MonthlyAttendanceReport report = requireSubmittedReport(reportId);
        return buildReport(report, payrollOf(report), true);
    }

    @Override
    public PayrollPaymentViews.EmployeePayroll employeePayroll(AuthenticatedUser admin, int reportId, int employeeId) {
        assertAdmin(admin);
        MonthlyAttendanceReport report = requireSubmittedReport(reportId);
        MonthlyPayroll payroll = payrollOf(report);
        if (payroll == null) {
            throw new BusinessException("Báo cáo này chưa có bảng lương đã chốt.");
        }
        PayrollEmployeeLine line = lineOf(payroll, employeeId);
        List<PayrollPayment> payments = paymentRepository.findByReportId(report.getId());
        PayrollPaymentViews.ReportPayroll summary = buildReport(report, payroll, true);
        PayrollPaymentViews.EmployeeRow row = summary.getEmployees().stream()
                .filter(item -> Objects.equals(item.getEmployeeId(), employeeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Nhân viên không có trong bảng lương của báo cáo này."));
        List<PayrollShiftLine> stored = line.getShiftLines() == null ? List.of() : new ArrayList<>(line.getShiftLines());
        stored.sort(Comparator.comparing(PayrollShiftLine::getWorkDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PayrollShiftLine::getScheduledStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        List<PayrollPaymentViews.ShiftRow> shifts = new ArrayList<>();
        for (PayrollShiftLine shift : stored) {
            int scheduled = zero(shift.getScheduledHours());
            int payable = zero(shift.getPayableHours());
            boolean paid = Boolean.TRUE.equals(shift.getPayable());
            shifts.add(PayrollPaymentViews.ShiftRow.builder()
                    .workDate(shift.getWorkDate())
                    .shiftName(shift.getShiftName())
                    .scheduleLabel(schedule(shift.getScheduledStartTime(), shift.getScheduledEndTime()))
                    .checkInLabel(shift.getCheckInTime() == null ? "—" : shift.getCheckInTime().format(CLOCK))
                    .checkOutLabel(shift.getCheckOutTime() == null ? "—" : shift.getCheckOutTime().format(CLOCK))
                    .attendanceStatus(shift.getAttendanceStatusLabel() == null ? "—" : shift.getAttendanceStatusLabel())
                    .scheduledHours(scheduled)
                    .deductedHours(paid ? Math.max(0, scheduled - payable) : 0)
                    .payableHours(payable)
                    .hourlyRateLabel(shift.getHourlyRate() == null ? "—" : money(shift.getHourlyRate()) + "/giờ")
                    .salaryLabel(money(shift.getShiftSalary()))
                    .payable(paid)
                    .unpaidReason(shift.getUnpaidReason() == null || shift.getUnpaidReason().isBlank()
                            ? "" : shift.getUnpaidReason())
                    .build());
        }
        return PayrollPaymentViews.EmployeePayroll.builder()
                .report(summary)
                .employee(row)
                .shifts(shifts)
                .payments(payments.stream()
                        .filter(payment -> Objects.equals(payment.getEmployee().getId(), employeeId))
                        .map(payment -> paymentRow(payment, report))
                        .toList())
                .build();
    }

    // ------------------------------------------------------------------ Manager

    @Override
    public PayrollPaymentViews.ReportPayroll managerReport(AuthenticatedUser manager, int year, int month) {
        if (manager == null || manager.getRoleName() != RoleName.MANAGER || manager.getEmployeeId() == null) {
            throw new BusinessException("Bạn không có quyền xem báo cáo lương của cửa hàng.");
        }
        Store store = storeRepository.findByManager_Id(manager.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
        MonthlyAttendanceReport report = reportRepository.findCurrent(store.getId(), year, month).orElse(null);
        if (report == null || report.getSubmittedAt() == null) {
            return null;
        }
        // Manager chỉ xem; không có khoá thanh toán.
        return buildReport(report, payrollOf(report), false);
    }

    // ------------------------------------------------------------------ Admin: thanh toán

    @Override
    @Transactional
    public String payEmployee(AuthenticatedUser admin,
                              int reportId,
                              int employeeId,
                              PayrollPaymentForm form,
                              MultipartFile proof) {
        assertAdmin(admin);
        Common common = common(form);
        MonthlyAttendanceReport report = lockApproved(reportId);
        MonthlyPayroll payroll = requireLockedPayroll(report);
        PayrollEmployeeLine line = lineOf(payroll, employeeId);
        Map<Integer, BigDecimal> paid = paidByEmployee(report.getId());
        BigDecimal remaining = owed(line).subtract(paid.getOrDefault(employeeId, BigDecimal.ZERO));
        if (remaining.signum() <= 0) {
            throw new BusinessException("Nhân viên này đã được thanh toán đủ.");
        }
        BigDecimal amount = parseAmount(form == null ? null : form.getAmount());
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessException("Số tiền thanh toán vượt quá số còn phải trả (" + money(remaining) + ").");
        }
        PayrollProofStore.Stored file = proofStore.save(proof);
        PayrollPayment saved;
        try {
            saved = paymentRepository.saveAndFlush(payment(report, payroll, line.getEmployee(), amount, common, file, admin));
        } catch (DataIntegrityViolationException ex) {
            proofStore.delete(file == null ? null : file.key());
            throw new BusinessException("Giao dịch này đã được ghi nhận. Hãy tải lại trang.");
        }
        notifyPaid(saved, report);
        return money(amount);
    }

    @Override
    @Transactional
    public PayAllResult payAll(AuthenticatedUser admin, int reportId, PayrollPaymentForm form, MultipartFile proof) {
        assertAdmin(admin);
        Common common = common(form);
        MonthlyAttendanceReport report = lockApproved(reportId);
        MonthlyPayroll payroll = requireLockedPayroll(report);
        Map<Integer, BigDecimal> paid = paidByEmployee(report.getId());
        List<PayrollEmployeeLine> owedLines = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollEmployeeLine line : sortedLines(payroll)) {
            BigDecimal remaining = owed(line).subtract(paid.getOrDefault(line.getEmployee().getId(), BigDecimal.ZERO));
            if (remaining.signum() > 0) {
                owedLines.add(line);
                amounts.add(remaining);
                total = total.add(remaining);
            }
        }
        if (owedLines.isEmpty()) {
            throw new BusinessException("Không còn nhân viên nào cần thanh toán trong báo cáo này.");
        }
        // Số tiền và số người phải đúng như Admin đã thấy khi xác nhận; nếu vừa có giao dịch khác thì dừng.
        if (form != null && form.getExpectedCount() != null && form.getExpectedCount() != owedLines.size()) {
            throw new BusinessException("Danh sách nhân viên cần thanh toán vừa thay đổi. Hãy tải lại trang và xác nhận lại.");
        }
        if (form != null && form.getExpectedTotal() != null && !form.getExpectedTotal().isBlank()) {
            BigDecimal expected = parseAmountLoose(form.getExpectedTotal());
            if (expected == null || expected.compareTo(total) != 0) {
                throw new BusinessException("Tổng số tiền cần thanh toán vừa thay đổi. Hãy tải lại trang và xác nhận lại.");
            }
        }
        PayrollProofStore.Stored file = proofStore.save(proof);
        List<PayrollPayment> saved = new ArrayList<>();
        try {
            for (int i = 0; i < owedLines.size(); i++) {
                saved.add(paymentRepository.save(payment(
                        report, payroll, owedLines.get(i).getEmployee(), amounts.get(i), common, file, admin)));
            }
            paymentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            proofStore.delete(file == null ? null : file.key());
            throw new BusinessException("Giao dịch này đã được ghi nhận. Hãy tải lại trang.");
        }
        for (PayrollPayment payment : saved) {
            notifyPaid(payment, report);
        }
        return new PayAllResult(saved.size(), money(total));
    }

    @Override
    public ProofFile proof(AuthenticatedUser admin, int reportId, int paymentId) {
        assertAdmin(admin);
        PayrollPayment payment = paymentRepository.findById(paymentId)
                .filter(item -> item.getReport() != null && Objects.equals(item.getReport().getId(), reportId))
                .orElseThrow(() -> new BusinessException("Không tìm thấy giao dịch thanh toán."));
        byte[] body = proofStore.read(payment.getProofStorageKey());
        if (body == null) {
            throw new BusinessException("Giao dịch này không có minh chứng thanh toán.");
        }
        String name = payment.getProofFileName() == null || payment.getProofFileName().isBlank()
                ? payment.getProofStorageKey() : payment.getProofFileName();
        return new ProofFile(name, PayrollProofStore.contentType(payment.getProofStorageKey()), body);
    }

    // ------------------------------------------------------------------ Staff

    @Override
    public List<PayrollPaymentViews.StaffPeriod> myPeriods(AuthenticatedUser staff) {
        if (staff == null || staff.getEmployeeId() == null) {
            return List.of();
        }
        Integer employeeId = staff.getEmployeeId();
        Map<Integer, List<PayrollPayment>> byReport = new HashMap<>();
        for (PayrollPayment payment : paymentRepository.findByEmployeeId(employeeId)) {
            byReport.computeIfAbsent(payment.getReport().getId(), ignored -> new ArrayList<>()).add(payment);
        }
        List<PayrollPaymentViews.StaffPeriod> periods = new ArrayList<>();
        for (PayrollEmployeeLine line : payrollRepository.findLinesByEmployeeId(employeeId)) {
            MonthlyPayroll payroll = line.getPayroll();
            if (payroll == null || payroll.getStore() == null) {
                continue;
            }
            MonthlyAttendanceReport report = reportRepository.findCurrent(
                    payroll.getStore().getId(), payroll.getPayrollYear(), payroll.getPayrollMonth()).orElse(null);
            // Chỉ hiện bảng lương chính thức: báo cáo đã được Admin duyệt.
            if (report == null || report.getStatus() != AttendanceReportStatus.APPROVED) {
                continue;
            }
            List<PayrollPayment> payments = byReport.getOrDefault(report.getId(), List.of());
            BigDecimal received = sum(payments);
            Money parts = parts(line);
            BigDecimal remaining = parts.net().subtract(received).max(BigDecimal.ZERO);
            PayrollPaymentStatus status = status(parts.net(), received);
            periods.add(PayrollPaymentViews.StaffPeriod.builder()
                    .year(payroll.getPayrollYear())
                    .month(payroll.getPayrollMonth())
                    .periodLabel("Tháng " + payroll.getPayrollMonth() + "/" + payroll.getPayrollYear())
                    .storeName(payroll.getStore().getStoreName())
                    .assignedShifts(zero(line.getAssignedShiftCount()))
                    .workedShifts(zero(line.getValidShiftCount()))
                    .baseLabel(money(parts.base()))
                    .allowanceLabel(money(parts.allowance()))
                    .deductionLabel(money(parts.deduction()))
                    .netLabel(money(parts.net()))
                    .receivedLabel(money(received))
                    .remainingLabel(money(remaining))
                    .paymentStatusKey(status.name())
                    .paymentStatusLabel(status.label())
                    .lastPaidDate(lastDate(payments))
                    .payments(payments.stream().map(payment -> paymentRow(payment, report)).toList())
                    .build());
        }
        return periods;
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    private PayrollPaymentViews.ReportPayroll buildReport(MonthlyAttendanceReport report,
                                                          MonthlyPayroll payroll,
                                                          boolean forAdmin) {
        boolean approved = report.getStatus() == AttendanceReportStatus.APPROVED;
        List<PayrollPayment> payments = report.getId() == null ? List.of() : paymentRepository.findByReportId(report.getId());
        Map<Integer, BigDecimal> paidBy = new HashMap<>();
        Map<Integer, LocalDate> lastPaid = new HashMap<>();
        for (PayrollPayment payment : payments) {
            Integer id = payment.getEmployee().getId();
            paidBy.merge(id, payment.getAmount(), BigDecimal::add);
            lastPaid.merge(id, payment.getPaidDate(), (left, right) -> left.isAfter(right) ? left : right);
        }
        List<PayrollPaymentViews.EmployeeRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paidTotal = BigDecimal.ZERO;
        int unpaid = 0;
        if (payroll != null) {
            for (PayrollEmployeeLine line : sortedLines(payroll)) {
                Employee employee = line.getEmployee();
                Money parts = parts(line);
                BigDecimal paid = paidBy.getOrDefault(employee.getId(), BigDecimal.ZERO);
                BigDecimal remaining = parts.net().subtract(paid).max(BigDecimal.ZERO);
                PayrollPaymentStatus status = status(parts.net(), paid);
                total = total.add(parts.net());
                paidTotal = paidTotal.add(paid);
                if (remaining.signum() > 0) {
                    unpaid++;
                }
                rows.add(PayrollPaymentViews.EmployeeRow.builder()
                        .employeeId(employee.getId())
                        .employeeCode(code(employee.getId()))
                        .employeeName(employee.getFullName() == null ? "—" : employee.getFullName())
                        .position(position(employee))
                        .assignedShifts(zero(line.getAssignedShiftCount()))
                        .attendedShifts(attended(line))
                        .validShifts(zero(line.getValidShiftCount()))
                        .payableHours(zero(line.getPayableHours()))
                        .hourlyRateLabel(line.getHourlyRate() == null ? "—" : money(line.getHourlyRate()) + "/giờ")
                        .baseLabel(money(parts.base()))
                        .allowanceLabel(money(parts.allowance()))
                        .deductionLabel(money(parts.deduction()))
                        .netLabel(money(parts.net()))
                        .paidLabel(money(paid))
                        .remainingLabel(money(remaining))
                        .remainingValue(plain(remaining))
                        .owed(remaining.signum() > 0)
                        .noSalary(parts.net().signum() == 0)
                        .paymentStatusKey(status.name())
                        .paymentStatusLabel(status.label())
                        .lastPaidDate(lastPaid.get(employee.getId()))
                        .build());
            }
        }
        BigDecimal remainingTotal = total.subtract(paidTotal).max(BigDecimal.ZERO);
        PayrollPaymentStatus status = status(total, paidTotal);
        String blocked = null;
        if (payroll == null || payroll.getStatus() != PayrollStatus.DA_CHOT) {
            blocked = "Báo cáo chưa kèm bảng lương đã chốt.";
        } else if (!approved) {
            blocked = "Chỉ thanh toán được báo cáo đã được duyệt.";
        } else if (unpaid == 0) {
            blocked = "Đã thanh toán đủ cho mọi nhân viên.";
        }
        Store store = report.getStore();
        return PayrollPaymentViews.ReportPayroll.builder()
                .reportId(report.getId())
                .storeId(store == null ? null : store.getId())
                .storeName(store == null ? "" : store.getStoreName())
                .year(report.getReportYear())
                .month(report.getReportMonth())
                .periodLabel("Tháng " + report.getReportMonth() + "/" + report.getReportYear())
                .statusCode(report.getStatus() == null ? "" : report.getStatus().name())
                .approved(approved)
                .hasPayroll(payroll != null)
                .payrollStatusLabel(payroll == null || payroll.getStatus() == null ? "Chưa tính" : payroll.getStatus().label())
                .employeeCount(rows.size())
                .total(total)
                .paid(paidTotal)
                .remaining(remainingTotal)
                .totalLabel(money(total))
                .paidLabel(money(paidTotal))
                .remainingLabel(money(remainingTotal))
                .paymentStatusKey(status.name())
                .paymentStatusLabel(status.label())
                .unpaidCount(unpaid)
                .canPay(forAdmin && blocked == null)
                .payBlockedReason(blocked)
                .requestKey(forAdmin ? UUID.randomUUID().toString() : null)
                .today(VietnamTime.today())
                .employees(rows)
                .payments(payments.stream().map(payment -> paymentRow(payment, report)).toList())
                .build();
    }

    private PayrollPaymentViews.PaymentRow paymentRow(PayrollPayment payment, MonthlyAttendanceReport report) {
        Employee employee = payment.getEmployee();
        MonthlyAttendanceReport owner = payment.getReport() == null ? report : payment.getReport();
        return PayrollPaymentViews.PaymentRow.builder()
                .paymentId(payment.getId())
                .employeeId(employee == null ? null : employee.getId())
                .employeeCode(employee == null ? "—" : code(employee.getId()))
                .employeeName(employee == null || employee.getFullName() == null ? "—" : employee.getFullName())
                .periodLabel("Tháng " + payment.getPayrollMonth() + "/" + payment.getPayrollYear())
                .storeName(owner == null || owner.getStore() == null ? "" : owner.getStore().getStoreName())
                .amountLabel(money(payment.getAmount()))
                .paidDate(payment.getPaidDate())
                .methodLabel(payment.getMethod() == null ? "—" : payment.getMethod().label())
                .transactionCode(blank(payment.getTransactionCode()))
                .hasProof(payment.getProofStorageKey() != null && !payment.getProofStorageKey().isBlank())
                .proofFileName(blank(payment.getProofFileName()))
                .paidByName(blank(payment.getPaidByName()))
                .note(blank(payment.getNote()))
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PayrollPayment payment(MonthlyAttendanceReport report,
                                   MonthlyPayroll payroll,
                                   Employee employee,
                                   BigDecimal amount,
                                   Common common,
                                   PayrollProofStore.Stored file,
                                   AuthenticatedUser admin) {
        return PayrollPayment.builder()
                .report(report)
                .payroll(payroll)
                .employee(employee)
                .payrollYear(report.getReportYear())
                .payrollMonth(report.getReportMonth())
                .amount(amount)
                .paidDate(common.paidDate())
                .method(common.method())
                .transactionCode(common.transactionCode())
                .proofFileName(file == null ? null : file.fileName())
                .proofStorageKey(file == null ? null : file.key())
                .note(common.note())
                .paidBy(admin.getUserId() == null ? null : userRepository.getReferenceById(admin.getUserId()))
                .paidByName(admin.getDisplayName())
                .requestKey(common.requestKey())
                .createdAt(VietnamTime.now())
                .build();
    }

    private void notifyPaid(PayrollPayment payment, MonthlyAttendanceReport report) {
        Employee employee = payment.getEmployee();
        if (employee == null || employee.getId() == null) {
            return;
        }
        Optional<User> recipient = userRepository.findByEmployee_Id(employee.getId());
        if (recipient == null || recipient.isEmpty()) {
            return;
        }
        notificationService.notifyUsers(
                List.of(recipient.get()),
                "Bạn đã được thanh toán lương",
                "Admin đã thanh toán " + money(payment.getAmount()) + " lương tháng " + report.getReportMonth() + "/"
                        + report.getReportYear() + " (" + payment.getMethod().label().toLowerCase(Locale.ROOT)
                        + ", ngày " + payment.getPaidDate().format(DAY) + ").",
                NotificationType.PAYROLL_PAID,
                REFERENCE,
                payment.getId());
    }

    // ------------------------------------------------------------------ kiểm tra

    private MonthlyAttendanceReport requireSubmittedReport(int reportId) {
        MonthlyAttendanceReport report = reportRepository.findByIdDetailed(reportId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy báo cáo."));
        if (report.getSubmittedAt() == null) {
            throw new BusinessException("Báo cáo chưa được gửi.");
        }
        return report;
    }

    /** Khoá dòng báo cáo: hai lần thanh toán đồng thời phải chờ nhau và thấy số đã trả mới nhất. */
    private MonthlyAttendanceReport lockApproved(int reportId) {
        reportRepository.findByIdForUpdate(reportId);
        MonthlyAttendanceReport report = requireSubmittedReport(reportId);
        if (report.getStatus() != AttendanceReportStatus.APPROVED) {
            throw new BusinessException("Chỉ thanh toán được báo cáo đã được Admin duyệt.");
        }
        return report;
    }

    private MonthlyPayroll requireLockedPayroll(MonthlyAttendanceReport report) {
        MonthlyPayroll payroll = payrollOf(report);
        if (payroll == null || payroll.getStatus() != PayrollStatus.DA_CHOT) {
            throw new BusinessException("Báo cáo chưa kèm bảng lương đã chốt, chưa thể thanh toán.");
        }
        return payroll;
    }

    private MonthlyPayroll payrollOf(MonthlyAttendanceReport report) {
        if (report.getStore() == null || report.getStore().getId() == null) {
            return null;
        }
        return payrollRepository.findPeriod(report.getStore().getId(), report.getReportYear(), report.getReportMonth())
                .orElse(null);
    }

    private PayrollEmployeeLine lineOf(MonthlyPayroll payroll, int employeeId) {
        return sortedLines(payroll).stream()
                .filter(line -> line.getEmployee().getId() == employeeId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Nhân viên không có trong bảng lương của báo cáo này."));
    }

    private List<PayrollEmployeeLine> sortedLines(MonthlyPayroll payroll) {
        List<PayrollEmployeeLine> lines = new ArrayList<>();
        if (payroll.getEmployeeLines() != null) {
            for (PayrollEmployeeLine line : payroll.getEmployeeLines()) {
                if (line.getEmployee() != null && line.getEmployee().getId() != null) {
                    lines.add(line);
                }
            }
        }
        lines.sort(Comparator.comparing((PayrollEmployeeLine line) ->
                        line.getEmployee().getFullName() == null ? "" : line.getEmployee().getFullName(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(line -> line.getEmployee().getId()));
        return lines;
    }

    private Common common(PayrollPaymentForm form) {
        if (form == null) {
            throw new BusinessException("Thiếu thông tin thanh toán.");
        }
        PaymentMethod method = PaymentMethod.parse(form.getMethod());
        if (method == null) {
            throw new BusinessException("Chọn phương thức thanh toán: Chuyển khoản hoặc Tiền mặt.");
        }
        LocalDate date;
        try {
            date = form.getPaidDate() == null || form.getPaidDate().isBlank()
                    ? VietnamTime.today()
                    : LocalDate.parse(form.getPaidDate().trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Ngày thanh toán không hợp lệ.");
        }
        if (date.isAfter(VietnamTime.today())) {
            throw new BusinessException("Ngày thanh toán không được sau hôm nay.");
        }
        String code = trim(form.getTransactionCode());
        if (code != null && code.length() > 100) {
            throw new BusinessException("Mã giao dịch tối đa 100 ký tự.");
        }
        String note = trim(form.getNote());
        if (note != null && note.length() > 500) {
            throw new BusinessException("Ghi chú tối đa 500 ký tự.");
        }
        String key = trim(form.getRequestKey());
        if (key == null || key.length() > 80) {
            throw new BusinessException("Phiên thanh toán không hợp lệ. Hãy tải lại trang.");
        }
        if (paymentRepository.existsByRequestKey(key)) {
            throw new BusinessException("Giao dịch này đã được ghi nhận. Hãy tải lại trang.");
        }
        return new Common(method, date, code, note, key);
    }

    private record Common(PaymentMethod method, LocalDate paidDate, String transactionCode, String note,
                          String requestKey) {
    }

    private BigDecimal parseAmount(String value) {
        BigDecimal amount = parseAmountLoose(value);
        if (amount == null) {
            throw new BusinessException("Số tiền thanh toán không hợp lệ.");
        }
        if (amount.signum() <= 0) {
            throw new BusinessException("Số tiền thanh toán phải lớn hơn 0.");
        }
        if (amount.scale() > 2) {
            throw new BusinessException("Số tiền thanh toán tối đa 2 chữ số thập phân.");
        }
        return amount;
    }

    /** Nhận "1250000", "1.250.000" hoặc "1250000.50". */
    private BigDecimal parseAmountLoose(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim().replace(" ", "").replace("đ", "");
        if (clean.matches("\\d{1,3}(\\.\\d{3})+")) {
            clean = clean.replace(".", "");
        } else {
            clean = clean.replace(",", ".");
        }
        try {
            return new BigDecimal(clean).stripTrailingZeros();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void assertAdmin(AuthenticatedUser user) {
        if (user == null || user.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException("Chỉ Admin được xem báo cáo lương và xác nhận thanh toán.");
        }
    }

    // ------------------------------------------------------------------ tiền

    private record Totals(int employees, BigDecimal total, BigDecimal paid) {
        static final Totals EMPTY = new Totals(0, BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal remaining() {
            return total.subtract(paid).max(BigDecimal.ZERO);
        }

        PayrollPaymentStatus status() {
            return PayrollPaymentServiceImpl.status(total, paid);
        }
    }

    private Totals totals(MonthlyPayroll payroll, List<PayrollPayment> payments) {
        BigDecimal total = BigDecimal.ZERO;
        int employees = 0;
        for (PayrollEmployeeLine line : sortedLines(payroll)) {
            total = total.add(owed(line));
            employees++;
        }
        return new Totals(employees, total, sum(payments));
    }

    /** Lương cơ bản − khấu trừ + phụ cấp = thực nhận (đúng số đã tính trong bảng lương). */
    private record Money(BigDecimal base, BigDecimal allowance, BigDecimal deduction, BigDecimal net) {
    }

    static Money parts(PayrollEmployeeLine line) {
        BigDecimal net = line.getExpectedSalary() == null ? BigDecimal.ZERO : line.getExpectedSalary();
        BigDecimal base = BigDecimal.ZERO;
        if (line.getShiftLines() != null) {
            for (PayrollShiftLine shift : line.getShiftLines()) {
                if (Boolean.TRUE.equals(shift.getPayable()) && shift.getHourlyRate() != null) {
                    base = base.add(shift.getHourlyRate()
                            .multiply(BigDecimal.valueOf(zero(shift.getScheduledHours())))
                            .setScale(0, RoundingMode.HALF_UP));
                }
            }
        }
        if (base.compareTo(net) < 0) {
            base = net;
        }
        return new Money(base, BigDecimal.ZERO, base.subtract(net), net);
    }

    private static BigDecimal owed(PayrollEmployeeLine line) {
        return line.getExpectedSalary() == null ? BigDecimal.ZERO : line.getExpectedSalary();
    }

    static PayrollPaymentStatus status(BigDecimal total, BigDecimal paid) {
        BigDecimal owed = total == null ? BigDecimal.ZERO : total;
        BigDecimal done = paid == null ? BigDecimal.ZERO : paid;
        return done.compareTo(owed) >= 0 && (owed.signum() == 0 || done.signum() > 0)
                ? PayrollPaymentStatus.DA_THANH_TOAN
                : PayrollPaymentStatus.CHUA_THANH_TOAN;
    }

    private Map<Integer, BigDecimal> paidByEmployee(Integer reportId) {
        Map<Integer, BigDecimal> paid = new HashMap<>();
        for (PayrollPayment payment : paymentRepository.findByReportId(reportId)) {
            paid.merge(payment.getEmployee().getId(), payment.getAmount(), BigDecimal::add);
        }
        return paid;
    }

    private static BigDecimal sum(List<PayrollPayment> payments) {
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollPayment payment : payments) {
            total = total.add(payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount());
        }
        return total;
    }

    private static LocalDate lastDate(List<PayrollPayment> payments) {
        return payments.stream()
                .map(PayrollPayment::getPaidDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static int attended(PayrollEmployeeLine line) {
        if (line.getShiftLines() == null) {
            return 0;
        }
        return (int) line.getShiftLines().stream()
                .filter(shift -> shift.getCheckInTime() != null || shift.getCheckOutTime() != null)
                .count();
    }

    private static String position(Employee employee) {
        if (employee.getUser() == null || employee.getUser().getRole() == null
                || employee.getUser().getRole().getRoleName() == null) {
            return "Nhân viên";
        }
        return switch (employee.getUser().getRole().getRoleName()) {
            case MANAGER -> "Quản lý cửa hàng";
            case ADMIN -> "Admin";
            case STAFF -> "Nhân viên";
        };
    }

    static String money(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN")));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value) + " đ";
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String schedule(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return "—";
        }
        return start.format(CLOCK) + " – " + end.format(CLOCK);
    }

    private static String code(Integer employeeId) {
        return employeeId == null ? "—" : String.format("NV%03d", employeeId);
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    /** So khớp tìm kiếm không phân biệt hoa thường và dấu tiếng Việt. */
    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return normalized.toLowerCase(Locale.ROOT);
    }
}
