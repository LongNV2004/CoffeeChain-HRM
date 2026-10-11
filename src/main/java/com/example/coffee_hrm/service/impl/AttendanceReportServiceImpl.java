package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportDetail;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.entity.MonthlyAttendanceReport;
import com.example.coffee_hrm.entity.MonthlyAttendanceReportVersion;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.report.AttendanceReportCalculator;
import com.example.coffee_hrm.report.AttendanceReportWorkbook;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportVersionRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingAttendanceRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceReportServiceImpl implements AttendanceReportService {

    static final String STATUS_NOT_CREATED = "NOT_CREATED";
    static final String STATUS_CREATED = "CREATED";
    static final String STATUS_SUBMITTED = "SUBMITTED";
    static final String STATUS_STALE = "STALE";

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final TrainingAttendanceRepository trainingAttendanceRepository;
    private final MonthlyAttendanceReportRepository reportRepository;
    private final MonthlyAttendanceReportVersionRepository versionRepository;
    private final UserRepository userRepository;
    @Qualifier("attendanceReportClock")
    private final Clock clock;

    @Override
    public ManagerAttendanceReportPage managerPage(AuthenticatedUser user, Integer year, Integer month, Integer employeeId) {
        assertManager(user);
        Store store = resolveManagerStore(user);
        LocalDate today = LocalDate.now(clock);
        int reportYear = year == null ? today.getYear() : year;
        int reportMonth = month == null ? today.getMonthValue() : month;
        validatePeriod(reportYear, reportMonth);
        MonthlyAttendanceReport report = reportRepository
                .findCurrent(store.getId(), reportYear, reportMonth)
                .orElse(null);
        AttendanceReportWorkbook workbook = loadWorkbook(
                store, reportYear, reportMonth, user.getDisplayName(), today);
        LocalDateTime changedAt = changedAt(store.getId(), reportYear, reportMonth);
        return managerPage(store, user, report, workbook, changedAt, employeeId);
    }

    @Override
    @Transactional
    public void submit(AuthenticatedUser user, int year, int month) {
        assertManager(user);
        validatePeriod(year, month);
        Store store = resolveManagerStore(user);
        LocalDateTime now = LocalDateTime.now(clock);
        AttendanceReportWorkbook workbook = loadWorkbook(store, year, month, user.getDisplayName(), now.toLocalDate());
        MonthlyAttendanceReport report = reportRepository
                .findCurrent(store.getId(), year, month)
                .orElseGet(() -> newReport(store, year, month));
        int version = nextVersion(report);

        User sender = user.getUserId() == null ? null : userRepository.getReferenceById(user.getUserId());
        report.setStatus(AttendanceReportStatus.SUBMITTED);
        report.setVersionNumber(version);
        report.setDraftGeneratedAt(now);
        report.setSubmittedAt(now);
        report.setSubmittedBy(sender);
        report.setSubmittedByName(user.getDisplayName());
        report.setPreparedByName(user.getDisplayName());
        applySnapshot(report, workbook.getPreview());
        MonthlyAttendanceReport saved = saveReport(report);

        MonthlyAttendanceReportVersion versionRow = MonthlyAttendanceReportVersion.builder()
                .report(saved)
                .versionNumber(version)
                .submittedAt(now)
                .submittedBy(sender)
                .submittedByName(user.getDisplayName())
                .employeeCount(workbook.getPreview().getEmployeeCount())
                .shiftRecordCount(workbook.getPreview().getShiftRecordCount())
                .trainingRecordCount(workbook.getPreview().getTrainingRecordCount())
                .lateCount(workbook.getPreview().getLateCount())
                .absentCount(workbook.getPreview().getAbsentCount())
                .openCheckoutCount(workbook.getPreview().getOpenCheckoutCount())
                .reviewCount(workbook.getPreview().getReviewCount())
                .build();
        try {
            versionRepository.saveAndFlush(versionRow);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Báo cáo vừa được gửi. Hãy tải lại trang.");
        }
    }

    @Override
    public AdminAttendanceReportPage adminPage(AuthenticatedUser user,
                                               Integer year,
                                               Integer month,
                                               Integer storeId,
                                               String status) {
        assertAdmin(user);
        LocalDate today = LocalDate.now(clock);
        int reportYear = year == null ? today.getYear() : year;
        int reportMonth = month == null ? today.getMonthValue() : month;
        validatePeriod(reportYear, reportMonth);
        String filterStatus = normalizeStatus(status);
        List<Store> stores = storeRepository.findAllWithManager().stream()
                .sorted(Comparator.comparing(Store::getStoreName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Store::getId))
                .toList();
        if (storeId != null && stores.stream().noneMatch(store -> store.getId().equals(storeId))) {
            throw new BusinessException("Cửa hàng không tồn tại.");
        }
        Map<Integer, MonthlyAttendanceReport> reports = new HashMap<>();
        for (MonthlyAttendanceReport report : reportRepository.findByPeriod(reportYear, reportMonth)) {
            reports.put(report.getStore().getId(), report);
        }
        Map<Integer, LocalDateTime> changes = changeIndex(reportYear, reportMonth);
        LocalDate start = LocalDate.of(reportYear, reportMonth, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<AdminAttendanceReportPage.Row> rows = new ArrayList<>();
        int submitted = 0;
        int stale = 0;
        int order = 1;
        for (Store store : stores) {
            MonthlyAttendanceReport report = reports.get(store.getId());
            boolean sent = report != null && report.getSubmittedAt() != null;
            boolean outdated = sent && isAfter(changes.get(store.getId()), report.getSubmittedAt());
            if (sent) {
                submitted++;
            }
            if (outdated) {
                stale++;
            }
            if (storeId != null && !store.getId().equals(storeId)) {
                continue;
            }
            if ("submitted".equals(filterStatus) && !sent) {
                continue;
            }
            if ("unsent".equals(filterStatus) && sent) {
                continue;
            }
            rows.add(AdminAttendanceReportPage.Row.builder()
                    .order(order++)
                    .reportId(report == null ? null : report.getId())
                    .storeId(store.getId())
                    .storeName(store.getStoreName())
                    .managerName(store.getManager() == null ? "" : text(store.getManager().getFullName()))
                    .periodLabel(periodLabel(reportYear, reportMonth))
                    .periodStart(start)
                    .periodEnd(end)
                    .statusLabel(adminStatus(sent, outdated))
                    .submitted(sent)
                    .stale(outdated)
                    .senderName(sent ? senderName(report) : "")
                    .submittedAt(sent ? report.getSubmittedAt() : null)
                    .versionNumber(sent ? report.getVersionNumber() : null)
                    .build());
        }
        return AdminAttendanceReportPage.builder()
                .year(reportYear)
                .month(reportMonth)
                .periodLabel(periodLabel(reportYear, reportMonth))
                .filterStoreId(storeId)
                .filterStatus(filterStatus)
                .totalStores(stores.size())
                .submittedStores(submitted)
                .unsentStores(stores.size() - submitted)
                .staleStores(stale)
                .stores(stores.stream()
                        .map(store -> AdminAttendanceReportPage.StoreOption.builder()
                                .storeId(store.getId())
                                .storeName(store.getStoreName())
                                .build())
                        .toList())
                .rows(rows)
                .build();
    }

    @Override
    public AdminAttendanceReportDetail adminDetail(AuthenticatedUser user, int reportId, Integer employeeId) {
        assertAdmin(user);
        MonthlyAttendanceReport report = reportRepository.findByIdDetailed(reportId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy báo cáo."));
        Store store = report.getStore();
        LocalDate today = LocalDate.now(clock);
        AttendanceReportWorkbook live = loadWorkbook(
                store, report.getReportYear(), report.getReportMonth(), report.getPreparedByName(), today);
        boolean sent = report.getSubmittedAt() != null;
        boolean outdated = sent && isAfter(
                changedAt(store.getId(), report.getReportYear(), report.getReportMonth()),
                report.getSubmittedAt());
        List<AdminAttendanceReportDetail.VersionItem> versions = versionRepository
                .findByReport_IdOrderByVersionNumberDesc(report.getId())
                .stream()
                .map(version -> AdminAttendanceReportDetail.VersionItem.builder()
                        .versionNumber(version.getVersionNumber())
                        .submittedAt(version.getSubmittedAt())
                        .senderName(text(version.getSubmittedByName()))
                        .build())
                .toList();
        String selectedName = "";
        List<AttendanceReportWorkbook.ShiftRow> days = List.of();
        if (employeeId != null) {
            AttendanceReportWorkbook.SummaryRow selected = live.getEmployees().stream()
                    .filter(row -> row.getEmployeeId() == employeeId)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Nhân viên không thuộc cửa hàng của báo cáo này."));
            selectedName = selected.getFullName();
            days = live.getShifts().stream()
                    .filter(row -> row.getEmployeeId() == employeeId)
                    .toList();
        }
        return AdminAttendanceReportDetail.builder()
                .reportId(report.getId())
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .managerName(store.getManager() == null ? "" : text(store.getManager().getFullName()))
                .year(report.getReportYear())
                .month(report.getReportMonth())
                .periodLabel(periodLabel(report.getReportYear(), report.getReportMonth()))
                .submitted(sent)
                .stale(outdated)
                .senderName(sent ? senderName(report) : "")
                .authorName(text(report.getPreparedByName()))
                .submittedAt(report.getSubmittedAt())
                .versionNumber(sent ? report.getVersionNumber() : null)
                .periodStart(live.periodStart())
                .periodEnd(live.periodEnd())
                .snapshot(sent ? snapshot(report) : null)
                .live(preview(live.getPreview()))
                .employees(live.getEmployees())
                .selectedEmployeeId(employeeId)
                .selectedEmployeeName(selectedName)
                .days(days)
                .versions(versions)
                .build();
    }

    private ManagerAttendanceReportPage managerPage(Store store,
                                                    AuthenticatedUser user,
                                                    MonthlyAttendanceReport report,
                                                    AttendanceReportWorkbook workbook,
                                                    LocalDateTime changedAt,
                                                    Integer employeeId) {
        LocalDateTime draftAt = report == null ? null : report.getDraftGeneratedAt();
        LocalDateTime submittedAt = report == null ? null : report.getSubmittedAt();
        boolean newerDraft = draftAt != null && (submittedAt == null || draftAt.isAfter(submittedAt));
        boolean draftStale = draftAt != null && isAfter(changedAt, draftAt);
        boolean submittedStale = submittedAt != null && isAfter(changedAt, submittedAt);
        String statusCode;
        if (draftAt == null && submittedAt == null) {
            statusCode = STATUS_NOT_CREATED;
        } else if (newerDraft) {
            statusCode = draftStale ? STATUS_STALE : STATUS_CREATED;
        } else if (submittedStale) {
            statusCode = STATUS_STALE;
        } else if (submittedAt != null) {
            statusCode = STATUS_SUBMITTED;
        } else {
            statusCode = STATUS_CREATED;
        }
        LocalDateTime lastGenerated = latest(draftAt, submittedAt);
        String selectedName = "";
        List<AttendanceReportWorkbook.ShiftRow> days = List.of();
        if (employeeId != null) {
            AttendanceReportWorkbook.SummaryRow selected = workbook.getEmployees().stream()
                    .filter(row -> row.getEmployeeId() == employeeId)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý."));
            selectedName = selected.getFullName();
            days = workbook.getShifts().stream()
                    .filter(row -> row.getEmployeeId() == employeeId)
                    .toList();
        }
        return ManagerAttendanceReportPage.builder()
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .authorName(user.getDisplayName())
                .year(workbook.getYear())
                .month(workbook.getMonth())
                .periodLabel(workbook.periodLabel())
                .statusCode(statusCode)
                .statusLabel(managerStatusLabel(statusCode))
                .lastGeneratedAt(lastGenerated)
                .lastSubmittedAt(submittedAt)
                .versionNumber(report == null || report.getVersionNumber() == null || report.getVersionNumber() == 0
                        ? null
                        : report.getVersionNumber())
                .submitted(submittedAt != null)
                .preview(preview(workbook.getPreview()))
                .employees(workbook.getEmployees())
                .selectedEmployeeId(employeeId)
                .selectedEmployeeName(selectedName)
                .days(days)
                .build();
    }

    private AttendanceReportWorkbook loadWorkbook(Store store,
                                                  int year,
                                                  int month,
                                                  String authorName,
                                                  LocalDate createdDate) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return AttendanceReportCalculator.buildForPeriod(
                store,
                year,
                month,
                employeeRepository.findByStoreIdWithAccount(store.getId()),
                attendanceRepository.findForMonthlyReport(store.getId(), start, end),
                trainingAttendanceRepository.findForMonthlyReport(store.getId(), start, end),
                authorName,
                createdDate);
    }

    private LocalDateTime changedAt(Integer storeId, int year, int month) {
        return changeIndex(year, month).get(storeId);
    }

    private Map<Integer, LocalDateTime> changeIndex(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        Map<Integer, LocalDateTime> changes = new HashMap<>();
        for (AttendanceRepository.StoreReportChange change : attendanceRepository.findMonthlyReportChanges(start, end)) {
            putLater(changes, change.getStoreId(), change.getChangedAt());
        }
        for (TrainingAttendanceRepository.StoreReportChange change
                : trainingAttendanceRepository.findMonthlyReportChanges(start, end)) {
            putLater(changes, change.getStoreId(), change.getChangedAt());
        }
        return changes;
    }

    private void putLater(Map<Integer, LocalDateTime> changes, Integer storeId, LocalDateTime changedAt) {
        if (storeId == null || changedAt == null) {
            return;
        }
        LocalDateTime current = changes.get(storeId);
        if (current == null || changedAt.isAfter(current)) {
            changes.put(storeId, changedAt);
        }
    }

    private MonthlyAttendanceReport newReport(Store store, int year, int month) {
        return MonthlyAttendanceReport.builder()
                .store(store)
                .reportYear(year)
                .reportMonth(month)
                .status(AttendanceReportStatus.DRAFT)
                .versionNumber(0)
                .build();
    }

    private int nextVersion(MonthlyAttendanceReport report) {
        Integer current = report.getVersionNumber();
        return current == null || current < 1 ? 1 : current + 1;
    }

    private MonthlyAttendanceReport saveReport(MonthlyAttendanceReport report) {
        try {
            return reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Báo cáo của kỳ này vừa được cập nhật. Hãy tải lại trang.");
        }
    }

    private void applySnapshot(MonthlyAttendanceReport report, AttendanceReportWorkbook.Preview preview) {
        report.setEmployeeCount(preview.getEmployeeCount());
        report.setShiftRecordCount(preview.getShiftRecordCount());
        report.setTrainingRecordCount(preview.getTrainingRecordCount());
        report.setLateCount(preview.getLateCount());
        report.setAbsentCount(preview.getAbsentCount());
        report.setOpenCheckoutCount(preview.getOpenCheckoutCount());
        report.setReviewCount(preview.getReviewCount());
    }

    private Store resolveManagerStore(AuthenticatedUser user) {
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
    }

    private void assertManager(AuthenticatedUser user) {
        if (user == null || user.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Bạn không có quyền gửi báo cáo chấm công.");
        }
    }

    private void assertAdmin(AuthenticatedUser user) {
        if (user == null || user.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException("Bạn không có quyền xem báo cáo chấm công của cửa hàng.");
        }
    }

    private void validatePeriod(int year, int month) {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new BusinessException("Kỳ báo cáo không hợp lệ.");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return "all";
        }
        if ("submitted".equals(status) || "unsent".equals(status)) {
            return status;
        }
        throw new BusinessException("Trạng thái báo cáo không hợp lệ.");
    }

    private String adminStatus(boolean submitted, boolean stale) {
        if (!submitted) {
            return "Chưa gửi";
        }
        if (stale) {
            return "Đã gửi, có thay đổi cần gửi lại";
        }
        return "Đã gửi";
    }

    private String managerStatusLabel(String statusCode) {
        return switch (statusCode) {
            case STATUS_CREATED -> "Chưa gửi";
            case STATUS_SUBMITTED -> "Đã gửi";
            case STATUS_STALE -> "Có thay đổi cần gửi lại";
            default -> "Chưa gửi";
        };
    }

    private String senderName(MonthlyAttendanceReport report) {
        if (report.getSubmittedByName() != null && !report.getSubmittedByName().isBlank()) {
            return report.getSubmittedByName();
        }
        if (report.getSubmittedBy() != null && report.getSubmittedBy().getEmployee() != null) {
            return text(report.getSubmittedBy().getEmployee().getFullName());
        }
        if (report.getSubmittedBy() != null) {
            return text(report.getSubmittedBy().getUsername());
        }
        return "";
    }

    private ManagerAttendanceReportPage.AttendanceReportPreview snapshot(MonthlyAttendanceReport report) {
        return ManagerAttendanceReportPage.AttendanceReportPreview.builder()
                .employeeCount(zero(report.getEmployeeCount()))
                .shiftRecordCount(zero(report.getShiftRecordCount()))
                .trainingRecordCount(zero(report.getTrainingRecordCount()))
                .lateCount(zero(report.getLateCount()))
                .shiftLateCount(0)
                .trainingLateCount(0)
                .absentCount(zero(report.getAbsentCount()))
                .openCheckoutCount(zero(report.getOpenCheckoutCount()))
                .reviewCount(zero(report.getReviewCount()))
                .build();
    }

    private ManagerAttendanceReportPage.AttendanceReportPreview preview(AttendanceReportWorkbook.Preview preview) {
        return ManagerAttendanceReportPage.AttendanceReportPreview.builder()
                .employeeCount(preview.getEmployeeCount())
                .shiftRecordCount(preview.getShiftRecordCount())
                .trainingRecordCount(preview.getTrainingRecordCount())
                .lateCount(preview.getLateCount())
                .shiftLateCount(preview.getShiftLateCount())
                .trainingLateCount(preview.getTrainingLateCount())
                .absentCount(preview.getAbsentCount())
                .openCheckoutCount(preview.getOpenCheckoutCount())
                .reviewCount(preview.getReviewCount())
                .workDayCount(preview.getWorkDayCount())
                .violationCount(preview.getViolationCount())
                .missingCount(preview.getMissingCount())
                .build();
    }

    private String periodLabel(int year, int month) {
        return String.format("%02d/%04d", month, year);
    }

    private boolean isAfter(LocalDateTime left, LocalDateTime right) {
        return left != null && right != null && left.isAfter(right);
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null || left.isAfter(right)) {
            return left;
        }
        return right;
    }

    private int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
