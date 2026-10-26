package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportDetail;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.AttendanceReportVersionView;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.MonthlyAttendanceReport;
import com.example.coffee_hrm.entity.MonthlyAttendanceReportVersion;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.report.AttendanceReportCalculator;
import com.example.coffee_hrm.report.AttendanceReportExcel;
import com.example.coffee_hrm.report.AttendanceReportSnapshot;
import com.example.coffee_hrm.report.AttendanceReportWorkbook;
import com.example.coffee_hrm.report.ExcelSpreadsheetReader;
import com.example.coffee_hrm.report.ReportExcelFile;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportVersionRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingAttendanceRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceReportService;
import com.example.coffee_hrm.service.NotificationService;
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

    static final String REPORT_REF = "ATTENDANCE_REPORT";

    static final String STATUS_NOT_CREATED = "NOT_CREATED";
    static final String STATUS_CREATED = "CREATED";
    static final String STATUS_SUBMITTED = "SUBMITTED";
    static final String STATUS_STALE = "STALE";
    private static final int VERSION_PAGE_SIZE = 5;

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final TrainingAttendanceRepository trainingAttendanceRepository;
    private final MonthlyAttendanceReportRepository reportRepository;
    private final MonthlyAttendanceReportVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final AttendanceReportFileStore reportFiles;
    private final NotificationService notificationService;
    @Qualifier("attendanceReportClock")
    private final Clock clock;

    @Override
    public ManagerAttendanceReportPage managerPage(AuthenticatedUser user,
                                                   Integer year,
                                                   Integer month,
                                                   Integer employeeId,
                                                   String historyKind) {
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
        return managerPage(store, user, report, workbook, changedAt, employeeId, historyKind(historyKind));
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
        report.setReviewedAt(null);
        report.setReviewedByName(null);
        report.setReviewNote(null);
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
        reportFiles.save(store.getId(), year, month, version, AttendanceReportExcel.write(workbook));
        notifyAdminsOfSubmit(saved);
    }

    @Override
    public ReportExcelFile managerReportExcel(AuthenticatedUser user, int year, int month) {
        assertManager(user);
        validatePeriod(year, month);
        Store store = resolveManagerStore(user);
        MonthlyAttendanceReport report = reportRepository.findCurrent(store.getId(), year, month)
                .orElseThrow(() -> new BusinessException("Báo cáo kỳ này chưa được gửi cho Admin."));
        return readSubmittedExcel(report);
    }

    @Override
    public ReportExcelFile adminReportExcel(AuthenticatedUser user, int reportId) {
        return readSubmittedExcel(requireSubmitted(user, reportId));
    }

    @Override
    public ReportExcelFile adminVersionExcel(AuthenticatedUser user, int reportId, int version) {
        MonthlyAttendanceReport report = requireSubmitted(user, reportId);
        requireVersion(report, version);
        return excelFile(report, version);
    }

    @Override
    @Transactional
    public void deleteVersion(AuthenticatedUser user, int reportId, int versionNumber) {
        MonthlyAttendanceReport report = requireSubmitted(user, reportId);
        MonthlyAttendanceReportVersion version = requireVersion(report, versionNumber);
        Integer current = report.getVersionNumber();
        if (current == null || versionNumber >= current) {
            throw new BusinessException("Chỉ xoá được lần gửi cũ khi đã có lần gửi mới hơn.");
        }
        versionRepository.delete(version);
        reportFiles.delete(report.getStore().getId(), report.getReportYear(), report.getReportMonth(), versionNumber);
    }

    @Override
    public AttendanceReportVersionView adminVersion(AuthenticatedUser user, int reportId, int versionNumber) {
        MonthlyAttendanceReport report = requireSubmitted(user, reportId);
        MonthlyAttendanceReportVersion version = requireVersion(report, versionNumber);
        byte[] body = reportFiles.read(
                report.getStore().getId(), report.getReportYear(), report.getReportMonth(), versionNumber);
        List<AttendanceReportVersionView.EmployeeCard> employees = List.of();
        boolean missing = body == null;
        if (!missing) {
            employees = AttendanceReportSnapshot.employees(ExcelSpreadsheetReader.read(body));
        }
        return AttendanceReportVersionView.builder()
                .reportId(report.getId())
                .storeName(report.getStore().getStoreName())
                .periodLabel(periodLabel(report.getReportYear(), report.getReportMonth()))
                .versionNumber(version.getVersionNumber())
                .submittedAt(version.getSubmittedAt())
                .senderName(text(version.getSubmittedByName()))
                .current(report.getVersionNumber() != null && report.getVersionNumber().equals(version.getVersionNumber()))
                .fileMissing(missing)
                .employeeCount(value(version.getEmployeeCount()))
                .shiftRecordCount(value(version.getShiftRecordCount()))
                .trainingRecordCount(value(version.getTrainingRecordCount()))
                .lateCount(value(version.getLateCount()))
                .absentCount(value(version.getAbsentCount()))
                .employees(employees)
                .build();
    }

    private ReportExcelFile readSubmittedExcel(MonthlyAttendanceReport report) {
        if (report.getSubmittedAt() == null || report.getStore() == null || report.getStore().getId() == null) {
            throw new BusinessException("Báo cáo kỳ này chưa được gửi cho Admin.");
        }
        int version = report.getVersionNumber() == null ? 1 : report.getVersionNumber();
        return excelFile(report, version);
    }

    private ReportExcelFile excelFile(MonthlyAttendanceReport report, int version) {
        byte[] body = reportFiles.read(report.getStore().getId(), report.getReportYear(), report.getReportMonth(), version);
        if (body == null) {
            throw new BusinessException("Không tìm thấy file Excel của lần gửi này.");
        }
        return new ReportExcelFile(
                AttendanceReportExcel.fileName(report.getStore().getId(), report.getReportYear(), report.getReportMonth(), version),
                body);
    }

    private MonthlyAttendanceReportVersion requireVersion(MonthlyAttendanceReport report, int version) {
        return versionRepository.findByReport_IdAndVersionNumber(report.getId(), version)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lần gửi này."));
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    @Override
    @Transactional
    public void approve(AuthenticatedUser user, int reportId) {
        MonthlyAttendanceReport report = requireSubmitted(user, reportId);
        report.setStatus(AttendanceReportStatus.APPROVED);
        report.setReviewedAt(LocalDateTime.now(clock));
        report.setReviewedByName(user.getDisplayName());
        report.setReviewNote(null);
        saveReport(report);
        notifyManagerOfReview(report, true);
    }

    @Override
    @Transactional
    public void requestRevision(AuthenticatedUser user, int reportId, String note) {
        MonthlyAttendanceReport report = requireSubmitted(user, reportId);
        String clean = note == null ? "" : note.trim();
        if (clean.length() > 500) {
            clean = clean.substring(0, 500);
        }
        report.setStatus(AttendanceReportStatus.REVISION);
        report.setReviewedAt(LocalDateTime.now(clock));
        report.setReviewedByName(user.getDisplayName());
        report.setReviewNote(clean.isEmpty() ? null : clean);
        saveReport(report);
        notifyManagerOfReview(report, false);
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
            ReportBadge badge = badge(report, sent);
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
            if ("waiting".equals(filterStatus) && !"WAITING".equals(badge.code())) {
                continue;
            }
            if ("approved".equals(filterStatus) && !"APPROVED".equals(badge.code())) {
                continue;
            }
            if ("revision".equals(filterStatus) && !"REVISION".equals(badge.code())) {
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
                    .statusCode(badge.code())
                    .statusLabel(badge.label())
                    .statusCss(badge.css())
                    .reviewNote(report == null ? "" : text(report.getReviewNote()))
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
    public AdminAttendanceReportDetail adminDetail(AuthenticatedUser user, int reportId, Integer employeeId, String historyKind, Integer versionPage) {
        assertAdmin(user);
        MonthlyAttendanceReport report = reportRepository.findByIdDetailed(reportId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy báo cáo."));
        Store store = report.getStore();
        LocalDate today = LocalDate.now(clock);
        AttendanceReportWorkbook live = loadWorkbook(
                store, report.getReportYear(), report.getReportMonth(), report.getPreparedByName(), today);
        boolean sent = report.getSubmittedAt() != null;
        ReportBadge badge = badge(report, sent);
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
        int versionTotal = versions.size();
        int versionPages = versionTotal == 0 ? 0 : (versionTotal + VERSION_PAGE_SIZE - 1) / VERSION_PAGE_SIZE;
        int page = versionPage == null || versionPage < 1 ? 1 : versionPage;
        if (versionPages > 0 && page > versionPages) {
            page = versionPages;
        }
        int from = versionPages == 0 ? 0 : (page - 1) * VERSION_PAGE_SIZE;
        versions = versions.subList(from, Math.min(from + VERSION_PAGE_SIZE, versionTotal));
        String selectedName = "";
        if (employeeId != null) {
            AttendanceReportWorkbook.SummaryRow selected = live.getEmployees().stream()
                    .filter(row -> row.getEmployeeId() == employeeId)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Nhân viên không thuộc cửa hàng của báo cáo này."));
            selectedName = selected.getFullName();
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
                .statusCode(badge.code())
                .statusLabel(badge.label())
                .statusCss(badge.css())
                .reviewNote(text(report.getReviewNote()))
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
                .historyKind(historyKind(historyKind))
                .days(live.getShifts())
                .trainingDays(live.getTrainings())
                .versions(versions)
                .versionPage(page)
                .versionPageCount(versionPages)
                .build();
    }

    private ManagerAttendanceReportPage managerPage(Store store,
                                                    AuthenticatedUser user,
                                                    MonthlyAttendanceReport report,
                                                    AttendanceReportWorkbook workbook,
                                                    LocalDateTime changedAt,
                                                    Integer employeeId,
                                                    String historyKind) {
        LocalDateTime draftAt = report == null ? null : report.getDraftGeneratedAt();
        LocalDateTime submittedAt = report == null ? null : report.getSubmittedAt();
        boolean sent = submittedAt != null;
        boolean submittedStale = sent && isAfter(changedAt, submittedAt);
        ReportBadge badge = badge(report, sent);
        LocalDateTime lastGenerated = latest(draftAt, submittedAt);
        String selectedName = "";
        List<AttendanceReportWorkbook.ShiftRow> days = List.of();
        List<AttendanceReportWorkbook.TrainingRow> trainingDays = List.of();
        if (employeeId != null) {
            AttendanceReportWorkbook.SummaryRow selected = workbook.getEmployees().stream()
                    .filter(row -> row.getEmployeeId() == employeeId)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý."));
            selectedName = selected.getFullName();
            days = workbook.getShifts().stream()
                    .filter(row -> row.getEmployeeId() == employeeId)
                    .toList();
            trainingDays = workbook.getTrainings().stream()
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
                .statusCode(badge.code())
                .statusLabel(badge.label())
                .statusCss(badge.css())
                .stale(submittedStale)
                .reviewNote(report == null ? "" : text(report.getReviewNote()))
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
                .historyKind(historyKind)
                .days(days)
                .trainingDays(trainingDays)
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

    private void notifyAdminsOfSubmit(MonthlyAttendanceReport report) {
        String sender = text(report.getSubmittedByName());
        if (sender.isBlank()) {
            sender = "Quản lý";
        }
        notificationService.notifyUsers(
                userRepository.findActiveByRoleName(RoleName.ADMIN),
                "Báo cáo chấm công mới",
                sender + " đã gửi báo cáo chấm công tháng " + periodLabel(report.getReportYear(), report.getReportMonth())
                        + " của cửa hàng " + storeName(report) + ".",
                NotificationType.ATTENDANCE_REPORT_SUBMITTED,
                REPORT_REF,
                report.getId());
    }

    private void notifyManagerOfReview(MonthlyAttendanceReport report, boolean approved) {
        User recipient = report.getSubmittedBy();
        if (recipient == null || recipient.getId() == null) {
            Employee manager = report.getStore() == null ? null : report.getStore().getManager();
            if (manager != null && manager.getId() != null) {
                recipient = userRepository.findByEmployee_Id(manager.getId()).orElse(null);
            }
        }
        if (recipient == null || recipient.getId() == null) {
            return;
        }
        String period = periodLabel(report.getReportYear(), report.getReportMonth());
        String store = storeName(report);
        String title = approved ? "Báo cáo đã được duyệt" : "Báo cáo cần gửi lại";
        String message = approved
                ? "Admin đã duyệt báo cáo chấm công tháng " + period + " của cửa hàng " + store + "."
                : "Admin yêu cầu gửi lại báo cáo chấm công tháng " + period + " của cửa hàng " + store + "."
                + (report.getReviewNote() == null || report.getReviewNote().isBlank()
                ? ""
                : " Lý do: " + report.getReviewNote());
        notificationService.notifyUsers(
                List.of(recipient),
                title,
                message,
                approved ? NotificationType.ATTENDANCE_REPORT_APPROVED : NotificationType.ATTENDANCE_REPORT_REVISION,
                REPORT_REF,
                report.getId());
    }

    private String storeName(MonthlyAttendanceReport report) {
        return report.getStore() == null ? "" : text(report.getStore().getStoreName());
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

    private String historyKind(String kind) {
        if ("shift".equals(kind) || "training".equals(kind)) {
            return kind;
        }
        return "all";
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
        if ("submitted".equals(status) || "unsent".equals(status)
                || "waiting".equals(status) || "approved".equals(status) || "revision".equals(status)) {
            return status;
        }
        throw new BusinessException("Trạng thái báo cáo không hợp lệ.");
    }

    private MonthlyAttendanceReport requireSubmitted(AuthenticatedUser user, int reportId) {
        assertAdmin(user);
        MonthlyAttendanceReport report = reportRepository.findByIdDetailed(reportId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy báo cáo."));
        if (report.getSubmittedAt() == null) {
            throw new BusinessException("Báo cáo chưa được gửi.");
        }
        return report;
    }

    private ReportBadge badge(MonthlyAttendanceReport report, boolean sent) {
        if (!sent || report == null) {
            return new ReportBadge("UNSENT", "Chưa gửi", "status-unknown");
        }
        if (report.getStatus() == AttendanceReportStatus.APPROVED) {
            return new ReportBadge("APPROVED", "✓ Đã duyệt", "status-approved");
        }
        if (report.getStatus() == AttendanceReportStatus.REVISION) {
            return new ReportBadge("REVISION", "Yêu cầu làm lại", "status-revision");
        }
        return new ReportBadge("WAITING", "Đang chờ", "status-waiting");
    }

    private record ReportBadge(String code, String label, String css) {
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
