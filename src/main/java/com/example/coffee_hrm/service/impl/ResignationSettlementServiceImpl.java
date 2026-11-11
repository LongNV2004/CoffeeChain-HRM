package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.PayrollStatus;
import com.example.coffee_hrm.common.enums.ResignationItemStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.PayrollPaymentForm;
import com.example.coffee_hrm.dto.response.ResignationSettlementView;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.MonthlyAttendanceReport;
import com.example.coffee_hrm.entity.MonthlyPayroll;
import com.example.coffee_hrm.entity.PayrollEmployeeLine;
import com.example.coffee_hrm.entity.PayrollPayment;
import com.example.coffee_hrm.entity.PayrollShiftLine;
import com.example.coffee_hrm.entity.ResignationProposalItem;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.MonthlyPayrollRepository;
import com.example.coffee_hrm.repository.PayrollPaymentRepository;
import com.example.coffee_hrm.repository.ResignationProposalItemRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.PayrollPaymentService;
import com.example.coffee_hrm.service.ResignationSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Đối soát lương trước khi chấm dứt hợp đồng.
 *
 * <p>Kỳ lương liên quan = mọi (cửa hàng, tháng) mà nhân sự có dòng trong bảng lương, hoặc có ca đã công bố tới hôm nay.
 * Một kỳ chỉ có số tiền chính thức khi bảng lương đã chốt và báo cáo tháng đã được Admin duyệt — đúng điều kiện
 * thanh toán của {@link PayrollPaymentServiceImpl}. Kỳ chưa tính, chưa chốt, đang chờ duyệt hoặc bảng lương thiếu ca
 * là "chưa hoàn tất đối soát" và chặn việc duyệt chấm dứt.
 */
@Service
@RequiredArgsConstructor
public class ResignationSettlementServiceImpl implements ResignationSettlementService {

    private final MonthlyPayrollRepository payrollRepository;
    private final MonthlyAttendanceReportRepository reportRepository;
    private final PayrollPaymentRepository paymentRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmploymentContractRepository contractRepository;
    private final ResignationProposalItemRepository itemRepository;
    private final PayrollPaymentService payrollPaymentService;

    @Override
    @Transactional(readOnly = true)
    public ResignationSettlementView settlement(AuthenticatedUser admin, Integer employeeId) {
        assertAdmin(admin);
        if (employeeId == null) {
            throw new BusinessException("Không tìm thấy hồ sơ nhân viên.");
        }
        return compute(employeeId, false, true);
    }

    @Override
    @Transactional
    public void assertSettled(Integer employeeId, String employeeName) {
        if (employeeId == null) {
            throw new BusinessException("Không tìm thấy hồ sơ nhân viên.");
        }
        ResignationSettlementView view = compute(employeeId, true, false);
        if (!view.isEligible()) {
            String who = employeeName == null || employeeName.isBlank() ? "nhân sự này" : employeeName.trim();
            throw new BusinessException("Chưa thể duyệt chấm dứt hợp đồng cho " + who + ". "
                    + String.join(" ", view.getReasons()));
        }
    }

    @Override
    @Transactional
    public String pay(AuthenticatedUser admin,
                      Integer proposalId,
                      Integer itemId,
                      int reportId,
                      PayrollPaymentForm form,
                      MultipartFile proof) {
        assertAdmin(admin);
        // Khoá dòng nhân sự (cùng thứ tự khoá với thao tác duyệt: dòng đề xuất rồi tới báo cáo tháng).
        ResignationProposalItem item = itemRepository.lockById(itemId)
                .filter(found -> found.getProposal() != null && Objects.equals(found.getProposal().getId(), proposalId))
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân sự trong đề xuất này."));
        if (item.getStatus() != ResignationItemStatus.PENDING) {
            throw new BusinessException("Đề xuất của nhân sự này đã được xử lý. Thanh toán lương tiếp tại màn hình Báo cáo lương.");
        }
        Integer employeeId = item.getEmployee() == null ? null : item.getEmployee().getId();
        if (employeeId == null) {
            throw new BusinessException("Không tìm thấy hồ sơ nhân viên.");
        }
        ResignationSettlementView view = compute(employeeId, false, false);
        boolean payable = view.getPeriods().stream()
                .anyMatch(period -> period.isPayable() && Objects.equals(period.getReportId(), reportId));
        if (!payable) {
            throw new BusinessException("Kỳ lương này không còn khoản cần thanh toán hoặc chưa được duyệt. Hãy tải lại trang.");
        }
        // Dùng lại nghiệp vụ thanh toán hiện có: khoá báo cáo, kiểm tra số còn phải trả, chống gửi trùng, ghi lịch sử.
        return payrollPaymentService.payEmployee(admin, reportId, employeeId, form, proof);
    }

    // ------------------------------------------------------------------ đối soát

    private ResignationSettlementView compute(Integer employeeId, boolean lockReports, boolean forDisplay) {
        LocalDate today = VietnamTime.today();
        Map<Key, PayrollEmployeeLine> lineByKey = new HashMap<>();
        Map<Key, List<ShiftAssignment>> workByKey = new HashMap<>();
        Map<Key, String> storeNames = new HashMap<>();
        TreeMap<Key, Boolean> keys = new TreeMap<>();

        for (PayrollEmployeeLine line : payrollRepository.findLinesByEmployeeId(employeeId)) {
            MonthlyPayroll payroll = line.getPayroll();
            if (payroll == null || payroll.getStore() == null) {
                continue;
            }
            Key key = new Key(payroll.getStore().getId(), payroll.getPayrollYear(), payroll.getPayrollMonth());
            lineByKey.put(key, line);
            storeNames.putIfAbsent(key, payroll.getStore().getStoreName());
            keys.put(key, true);
        }
        for (ShiftAssignment assignment : shiftAssignmentRepository.findPublishedAssignedForEmployeeUntil(
                employeeId, today, AssignmentStatus.ASSIGNED)) {
            if (assignment.getWorkDate() == null || assignment.getShift() == null
                    || assignment.getShift().getStore() == null) {
                continue;
            }
            Store store = assignment.getShift().getStore();
            Key key = new Key(store.getId(), assignment.getWorkDate().getYear(), assignment.getWorkDate().getMonthValue());
            workByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(assignment);
            storeNames.putIfAbsent(key, store.getStoreName());
            keys.put(key, true);
        }

        // Khoá báo cáo trước khi đọc giao dịch: thanh toán, duyệt hay trả lại báo cáo đồng thời phải chờ.
        Map<Key, MonthlyAttendanceReport> reports = new HashMap<>();
        for (Key key : keys.keySet()) {
            MonthlyAttendanceReport report = lockReports
                    ? reportRepository.lockCurrent(key.storeId(), key.year(), key.month()).orElse(null)
                    : reportRepository.findCurrent(key.storeId(), key.year(), key.month()).orElse(null);
            if (report != null) {
                reports.put(key, report);
            }
        }

        List<PayrollPayment> payments = paymentRepository.findByEmployeeId(employeeId);
        Map<Integer, BigDecimal> paidByReport = new HashMap<>();
        for (PayrollPayment payment : payments) {
            if (payment.getReport() != null && payment.getAmount() != null) {
                paidByReport.merge(payment.getReport().getId(), payment.getAmount(), BigDecimal::add);
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paidTotal = BigDecimal.ZERO;
        BigDecimal remainingTotal = BigDecimal.ZERO;
        int pending = 0;
        int owed = 0;
        List<String> pendingLabels = new ArrayList<>();
        List<ResignationSettlementView.Period> periods = new ArrayList<>();

        for (Key key : keys.descendingKeySet()) {
            PayrollEmployeeLine line = lineByKey.get(key);
            List<ShiftAssignment> work = workByKey.getOrDefault(key, List.of());
            MonthlyPayroll payroll = line != null
                    ? line.getPayroll()
                    : payrollRepository.findPeriod(key.storeId(), key.year(), key.month()).orElse(null);
            MonthlyAttendanceReport report = reports.get(key);
            boolean submitted = report != null && report.getSubmittedAt() != null;
            boolean approved = submitted && report.getStatus() == AttendanceReportStatus.APPROVED;
            boolean locked = payroll != null && payroll.getStatus() == PayrollStatus.DA_CHOT;
            boolean finalized = approved && locked;
            int missing = missingShifts(line, work);
            BigDecimal net = line == null || line.getExpectedSalary() == null ? BigDecimal.ZERO : line.getExpectedSalary();
            BigDecimal paid = report == null ? BigDecimal.ZERO : paidByReport.getOrDefault(report.getId(), BigDecimal.ZERO);
            BigDecimal remaining = net.subtract(paid).max(BigDecimal.ZERO);
            String periodLabel = "Tháng " + key.month() + "/" + key.year();
            String storeName = storeNames.getOrDefault(key, "");

            String statusKey;
            String statusLabel;
            String statusCss;
            String note = null;
            boolean payable = false;
            if (!finalized) {
                statusKey = "PENDING";
                statusLabel = "Chờ tính / đối soát";
                statusCss = "badge-wait";
                note = pendingNote(payroll, report, missing);
            } else if (missing > 0) {
                statusKey = "PENDING";
                statusLabel = "Bảng lương thiếu ca";
                statusCss = "badge-wait";
                note = "Có " + missing + " ca làm không nằm trong bảng lương đã duyệt (phát sinh sau khi chốt lương).";
                payable = remaining.signum() > 0;
            } else if (remaining.signum() > 0) {
                statusKey = paid.signum() > 0 ? "PARTIAL" : "UNPAID";
                statusLabel = paid.signum() > 0 ? "Đã trả một phần" : "Chưa thanh toán";
                statusCss = paid.signum() > 0 ? "badge-partial" : "badge-off";
                payable = true;
            } else if (net.signum() == 0) {
                statusKey = "NO_SALARY";
                statusLabel = "Không phát sinh lương";
                statusCss = "badge-ok";
            } else {
                statusKey = "PAID";
                statusLabel = "Đã thanh toán";
                statusCss = "badge-ok";
            }

            if ("PENDING".equals(statusKey)) {
                pending++;
                pendingLabels.add(periodLabel + (storeName.isBlank() ? "" : " (" + storeName + ")"));
            }
            if (finalized) {
                total = total.add(net);
                paidTotal = paidTotal.add(paid.min(net));
                remainingTotal = remainingTotal.add(remaining);
                if (remaining.signum() > 0) {
                    owed++;
                }
            }
            periods.add(ResignationSettlementView.Period.builder()
                    .reportId(report == null ? null : report.getId())
                    .storeName(storeName)
                    .periodLabel(periodLabel)
                    .payrollStatusLabel(payroll == null || payroll.getStatus() == null ? "Chưa tính" : payroll.getStatus().label())
                    .reportStatusLabel(reportLabel(report))
                    .finalized(finalized)
                    .totalLabel(line == null ? "—" : PayrollPaymentServiceImpl.money(net) + (finalized ? "" : " (tạm tính)"))
                    .paidLabel(PayrollPaymentServiceImpl.money(paid))
                    .remainingLabel(finalized ? PayrollPaymentServiceImpl.money(remaining) : "—")
                    .remainingValue(remaining.stripTrailingZeros().toPlainString())
                    .payable(payable)
                    .statusKey(statusKey)
                    .statusLabel(statusLabel)
                    .statusCss(statusCss)
                    .note(note)
                    .build());
        }

        List<String> reasons = new ArrayList<>();
        if (remainingTotal.signum() > 0) {
            reasons.add("Còn " + PayrollPaymentServiceImpl.money(remainingTotal) + " lương đã xác định chưa thanh toán ("
                    + owed + " kỳ).");
        }
        if (pending > 0) {
            reasons.add("Còn " + pending + " kỳ lương chưa hoàn tất tính toán hoặc đối soát: "
                    + String.join(", ", pendingLabels) + ".");
        }
        boolean eligible = reasons.isEmpty();
        String statusKey;
        String statusLabel;
        String statusCss;
        if (remainingTotal.signum() > 0) {
            statusKey = ResignationSettlementView.OWED;
            statusLabel = "Còn lương chưa thanh toán";
            statusCss = "badge-off";
        } else if (pending > 0) {
            statusKey = ResignationSettlementView.PENDING;
            statusLabel = "Chưa hoàn tất đối soát";
            statusCss = "badge-wait";
        } else {
            statusKey = ResignationSettlementView.READY;
            statusLabel = "Đủ điều kiện duyệt";
            statusCss = "badge-ok";
        }

        return ResignationSettlementView.builder()
                .employeeId(employeeId)
                .contract(forDisplay ? contractInfo(employeeId, today) : null)
                .total(total)
                .paid(paidTotal)
                .remaining(remainingTotal)
                .totalLabel(PayrollPaymentServiceImpl.money(total))
                .paidLabel(PayrollPaymentServiceImpl.money(paidTotal))
                .remainingLabel(PayrollPaymentServiceImpl.money(remainingTotal))
                .periodCount(periods.size())
                .pendingCount(pending)
                .owedCount(owed)
                .eligible(eligible)
                .statusKey(statusKey)
                .statusLabel(statusLabel)
                .statusCss(statusCss)
                .reasons(List.copyOf(reasons))
                .periods(List.copyOf(periods))
                .payments(forDisplay ? paymentRows(payments) : List.of())
                .requestKey(forDisplay ? UUID.randomUUID().toString() : null)
                .today(today)
                .build();
    }

    /** Ca đã công bố tới hôm nay nhưng không có trong dòng lương đã lưu. */
    private static int missingShifts(PayrollEmployeeLine line, List<ShiftAssignment> work) {
        if (work.isEmpty()) {
            return 0;
        }
        Set<Integer> covered = new HashSet<>();
        if (line != null && line.getShiftLines() != null) {
            for (PayrollShiftLine shift : line.getShiftLines()) {
                if (shift.getAssignmentId() != null) {
                    covered.add(shift.getAssignmentId());
                }
            }
        }
        int missing = 0;
        for (ShiftAssignment assignment : work) {
            if (assignment.getId() == null || !covered.contains(assignment.getId())) {
                missing++;
            }
        }
        return missing;
    }

    private static String pendingNote(MonthlyPayroll payroll, MonthlyAttendanceReport report, int missing) {
        String note;
        if (payroll == null) {
            note = "Manager chưa tính lương kỳ này. Cần tính lương, chốt và gửi báo cáo tháng.";
        } else if (report != null && report.getSubmittedAt() != null
                && report.getStatus() == AttendanceReportStatus.SUBMITTED) {
            note = "Báo cáo lương tháng đang chờ Admin duyệt.";
        } else if (report != null && report.getStatus() == AttendanceReportStatus.REVISION) {
            note = "Báo cáo đã bị trả lại. Chờ Manager tính lại và gửi lại.";
        } else if (payroll.getStatus() != PayrollStatus.DA_CHOT) {
            note = "Bảng lương mới ở trạng thái Đã tính. Manager cần chốt và gửi báo cáo tháng.";
        } else {
            note = "Bảng lương đã chốt nhưng báo cáo tháng chưa được gửi hoặc chưa được duyệt.";
        }
        if (missing > 0) {
            note += " Bảng lương hiện chưa gồm " + missing + " ca làm của nhân sự.";
        }
        return note;
    }

    private static String reportLabel(MonthlyAttendanceReport report) {
        if (report == null || report.getSubmittedAt() == null || report.getStatus() == null) {
            return "Chưa gửi";
        }
        return switch (report.getStatus()) {
            case DRAFT -> "Chưa gửi";
            case SUBMITTED -> "Chờ duyệt";
            case APPROVED -> "Đã duyệt";
            case REVISION -> "Đã trả lại";
        };
    }

    private ResignationSettlementView.ContractInfo contractInfo(Integer employeeId, LocalDate today) {
        List<EmploymentContract> contracts = contractRepository.findByEmployeeId(employeeId);
        if (contracts == null || contracts.isEmpty()) {
            return null;
        }
        EmploymentContract contract = ContractValidityRules.openContract(contracts, today);
        if (contract == null) {
            contract = contracts.stream()
                    .filter(item -> item.getStartDate() != null)
                    .max(Comparator.comparing(EmploymentContract::getStartDate))
                    .orElse(contracts.get(0));
        }
        return ResignationSettlementView.ContractInfo.builder()
                .contractCode(contract.getContractCode())
                .termLabel(contract.getContractTerm() == null ? "—" : contract.getContractTerm().getLabel())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .jobTitle(contract.getJobTitle())
                .rateLabel(contract.getSalary() == null ? "—" : PayrollPaymentServiceImpl.money(contract.getSalary()) + "/giờ")
                .build();
    }

    private static List<ResignationSettlementView.Payment> paymentRows(List<PayrollPayment> payments) {
        List<ResignationSettlementView.Payment> rows = new ArrayList<>();
        for (PayrollPayment payment : payments) {
            MonthlyAttendanceReport report = payment.getReport();
            rows.add(ResignationSettlementView.Payment.builder()
                    .paymentId(payment.getId())
                    .reportId(report == null ? null : report.getId())
                    .periodLabel("Tháng " + payment.getPayrollMonth() + "/" + payment.getPayrollYear())
                    .storeName(report == null || report.getStore() == null ? "" : report.getStore().getStoreName())
                    .amountLabel(PayrollPaymentServiceImpl.money(payment.getAmount()))
                    .paidDate(payment.getPaidDate())
                    .methodLabel(payment.getMethod() == null ? "—" : payment.getMethod().label())
                    .transactionCode(payment.getTransactionCode() == null ? "" : payment.getTransactionCode())
                    .hasProof(payment.getProofStorageKey() != null && !payment.getProofStorageKey().isBlank())
                    .paidByName(payment.getPaidByName() == null ? "" : payment.getPaidByName())
                    .note(payment.getNote() == null ? "" : payment.getNote())
                    .createdAt(payment.getCreatedAt())
                    .build());
        }
        return rows;
    }

    private static void assertAdmin(AuthenticatedUser user) {
        if (user == null || user.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException("Chỉ Admin được đối soát và thanh toán lương khi chấm dứt hợp đồng.");
        }
    }

    private record Key(Integer storeId, int year, int month) implements Comparable<Key> {
        @Override
        public int compareTo(Key other) {
            int byYear = Integer.compare(year, other.year);
            if (byYear != 0) {
                return byYear;
            }
            int byMonth = Integer.compare(month, other.month);
            if (byMonth != 0) {
                return byMonth;
            }
            return Integer.compare(storeId == null ? 0 : storeId, other.storeId == null ? 0 : other.storeId);
        }
    }
}
