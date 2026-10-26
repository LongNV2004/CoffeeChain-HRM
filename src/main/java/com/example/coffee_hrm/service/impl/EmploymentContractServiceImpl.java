package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractPayPeriod;
import com.example.coffee_hrm.common.enums.ContractPaymentMethod;
import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.ContractDownload;
import com.example.coffee_hrm.dto.response.ContractEmployeeOption;
import com.example.coffee_hrm.dto.response.EmploymentContractListView;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmploymentContractService;
import com.example.coffee_hrm.service.support.ContractShiftQuota;
import com.example.coffee_hrm.service.impl.ContractFileStore.StoredContractFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class EmploymentContractServiceImpl implements EmploymentContractService {

    private static final int PAGE_SIZE = 10;
    private static final int KEYWORD_MAX_LENGTH = 120;
    private static final int SALARY_INTEGER_DIGITS = 16;
    private static final int SALARY_SCALE = 2;
    private static final int CODE_SAVE_ATTEMPTS = 5;
    private static final DateTimeFormatter CODE_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final EmploymentContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ContractFileStore fileStore;
    private final Integer expiringWithinDays;
    private final TransactionTemplate newContractTransaction;

    public EmploymentContractServiceImpl(EmploymentContractRepository contractRepository,
                                         EmployeeRepository employeeRepository,
                                         StoreRepository storeRepository,
                                         UserRepository userRepository,
                                         ContractFileStore fileStore,
                                         PlatformTransactionManager transactionManager,
                                         @Value("${coffee-hrm.contract.expiring-within-days:}") String expiringWithinDays) {
        this.contractRepository = contractRepository;
        this.employeeRepository = employeeRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.fileStore = fileStore;
        this.expiringWithinDays = parseExpiringWithinDays(expiringWithinDays);
        this.newContractTransaction = new TransactionTemplate(transactionManager);
        this.newContractTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public List<EmploymentContractResponse> list(AuthenticatedUser actor) {
        return scoped(actor).stream().map(this::toResponse).toList();
    }

    @Override
    public EmploymentContractListView search(AuthenticatedUser actor, String keyword, Integer storeId,
                                              String validity, LocalDate fromDate, LocalDate toDate, int page) {
        RoleName role = requireReader(actor);
        List<EmploymentContractResponse> scoped = list(actor);
        String keywordValue = normalizeKeyword(keyword);
        ContractValidityStatus validityStatus = parseValidity(validity);
        Integer appliedStoreId = role == RoleName.ADMIN ? storeId : null;
        List<EmploymentContractResponse> filtered = scoped.stream()
                .filter(item -> appliedStoreId == null || appliedStoreId.equals(item.getStoreId()))
                .filter(item -> matchesKeyword(item, keywordValue))
                .filter(item -> validityStatus == null || item.getValidityStatus() == validityStatus)
                .filter(item -> fromDate == null || !item.getStartDate().isBefore(fromDate))
                .filter(item -> toDate == null || !item.getStartDate().isAfter(toDate))
                .toList();
        int matchCount = filtered.size();
        int totalPages = matchCount == 0 ? 0 : (matchCount + PAGE_SIZE - 1) / PAGE_SIZE;
        int safePage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
        int from = totalPages == 0 ? 0 : (safePage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, matchCount);
        List<EmploymentContractResponse> pageItems = matchCount == 0
                ? List.of()
                : List.copyOf(filtered.subList(from, to));
        return EmploymentContractListView.builder()
                .contracts(pageItems)
                .matchCount(matchCount)
                .page(safePage)
                .totalPages(totalPages)
                .fromIndex(pageItems.isEmpty() ? 0 : from + 1)
                .toIndex(to)
                .keyword(keywordValue)
                .storeId(appliedStoreId)
                .validity(validityStatus == null ? null : validityStatus.name())
                .fromDate(fromDate)
                .toDate(toDate)
                .stores(role == RoleName.ADMIN ? storeChoices() : List.of())
                .admin(role == RoleName.ADMIN)
                .manager(role == RoleName.MANAGER)
                .filtered(keywordValue != null || appliedStoreId != null || validityStatus != null
                        || fromDate != null || toDate != null)
                .build();
    }

    @Override
    public EmploymentContractResponse detail(AuthenticatedUser actor, Integer contractId) {
        return toResponse(loadAuthorized(actor, contractId));
    }

    @Override
    public ContractDownload download(AuthenticatedUser actor, Integer contractId) {
        EmploymentContract contract = loadAuthorized(actor, contractId);
        if (contract.getSignedFilePath() == null || contract.getSignedFilePath().isBlank()) {
            throw new BusinessException("Hợp đồng chưa có file để tải.");
        }
        String fileName = contract.getOriginalFileName() == null ? "hop-dong" : contract.getOriginalFileName();
        String contentType = contract.getFileContentType() == null
                ? "application/octet-stream"
                : contract.getFileContentType();
        return new ContractDownload(fileName, contentType, fileStore.read(contract.getSignedFilePath()));
    }

    @Override
    public List<ContractEmployeeOption> listEmployees(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Bạn không có quyền tạo hợp đồng.");
        }
        Store store = requireManagedStore(actor);
        return employeeRepository.findByStoreIdWithAccount(store.getId()).stream()
                .filter(employee -> employee.getStatus() != EmployeeStatus.TERMINATED)
                .filter(employee -> actor.getEmployeeId() == null || !actor.getEmployeeId().equals(employee.getId()))
                .sorted(Comparator.comparing(Employee::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Employee::getId))
                .map(employee -> ContractEmployeeOption.builder()
                        .id(employee.getId())
                        .fullName(employee.getFullName())
                        .storeName(store.getStoreName())
                        .positionLabel(positionLabel(employee))
                        .hireDate(employee.getHireDate())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public EmploymentContractResponse create(AuthenticatedUser actor, CreateEmploymentContractRequest request,
                                              ContractFilePayload file) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Bạn không có quyền tạo hợp đồng.");
        }
        if (file == null || file.content() == null || file.content().length == 0) {
            throw new BusinessException("Vui lòng tải lên file hợp đồng đã ký.");
        }
        LocalDate startDate = requireStartDate(request);
        LocalDate endDate = requireEndDate(request);
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("Ngày kết thúc không được trước ngày bắt đầu hiệu lực.");
        }
        BigDecimal salary = requireSalary(request.getSalary(), "Mức lương");
        Integer weeklyShiftCount = requireWeeklyShiftCount(request == null ? null : request.getWeeklyShiftCount());
        String note = optionalNote(request.getInternalNote());
        Employee employee = employeeRepository.findByIdWithStore(request.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        if (actor.getEmployeeId() != null && actor.getEmployeeId().equals(employee.getId())) {
            throw new BusinessException("Không thể tạo hợp đồng cho chính bạn.");
        }
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException("Nhân viên đã nghỉ việc, không thể tạo hợp đồng mới.");
        }
        if (employee.getStore() == null) {
            throw new BusinessException("Nhân viên chưa thuộc cửa hàng.");
        }
        Store managed = requireManagedStore(actor);
        if (!managed.getId().equals(employee.getStore().getId())) {
            throw new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý.");
        }
        requireNoUnresolvedOverlap(employee.getId(), startDate, endDate);
        User creator = actor.getUserId() == null ? null : userRepository.findById(actor.getUserId()).orElse(null);
        StoredContractFile stored = fileStore.store(file);
        EmploymentContract saved = null;
        for (int attempt = 1; attempt <= CODE_SAVE_ATTEMPTS; attempt++) {
            try {
                saved = newContractTransaction.execute(status -> contractRepository.saveAndFlush(EmploymentContract.builder()
                        .contractCode(nextCode())
                        .employee(employee)
                        .startDate(startDate)
                        .endDate(endDate)
                        .salary(salary)
                        .weeklyShiftCount(weeklyShiftCount)
                        .internalNote(note)
                        .originalFileName(stored.originalName())
                        .fileContentType(stored.contentType())
                        .fileSizeBytes(stored.size())
                        .createdBy(creator)
                        .signingStatus(ContractSigningStatus.DA_TAI_BAN_KY)
                        .signedFilePath(stored.storedName())
                        .terminatedDate(null)
                        .build()));
                break;
            } catch (DataIntegrityViolationException ex) {
                saved = null;
            } catch (RuntimeException ex) {
                fileStore.delete(stored.storedName());
                throw ex;
            }
        }
        if (saved == null) {
            fileStore.delete(stored.storedName());
            throw new BusinessException("Không tạo được hợp đồng vì số hợp đồng bị trùng. Hãy thử lại.");
        }
        return toResponse(saved);
    }

    private List<EmploymentContract> scoped(AuthenticatedUser actor) {
        RoleName role = requireReader(actor);
        if (role == RoleName.ADMIN) {
            return contractRepository.findAllWithEmployee();
        }
        if (role == RoleName.MANAGER) {
            return contractRepository.findByStoreId(requireManagedStore(actor).getId());
        }
        if (actor.getEmployeeId() == null) {
            throw new BusinessException("Bạn không có quyền xem hợp đồng.");
        }
        return contractRepository.findByEmployeeId(actor.getEmployeeId());
    }

    private EmploymentContract loadAuthorized(AuthenticatedUser actor, Integer contractId) {
        RoleName role = requireReader(actor);
        EmploymentContract contract = contractRepository.findDetailById(contractId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hợp đồng."));
        if (role == RoleName.MANAGER) {
            requireSameStore(requireManagedStore(actor), contract);
        } else if (role == RoleName.STAFF) {
            Integer ownerId = contract.getEmployee() == null ? null : contract.getEmployee().getId();
            if (actor.getEmployeeId() == null || !actor.getEmployeeId().equals(ownerId)) {
                throw new BusinessException("Bạn không có quyền xem hợp đồng này.");
            }
        }
        return contract;
    }

    private void requireNoUnresolvedOverlap(Integer employeeId, LocalDate startDate, LocalDate endDate) {
        LocalDate today = VietnamTime.today();
        for (EmploymentContract existing : contractRepository.findByEmployeeId(employeeId)) {
            if (isClosed(existing, today)) {
                continue;
            }
            if (overlaps(startDate, endDate, existing.getStartDate(), existing.getEndDate())) {
                throw new BusinessException(
                        "Nhân viên đang có hợp đồng hiệu lực trùng thời gian. Hãy xử lý hợp đồng hiện tại trước khi tạo hợp đồng mới.");
            }
        }
    }

    private static boolean isClosed(EmploymentContract contract, LocalDate today) {
        if (contract.getTerminatedDate() != null && !contract.getTerminatedDate().isAfter(today)) {
            return true;
        }
        return contract.getEndDate() != null && contract.getEndDate().isBefore(today);
    }

    private static boolean overlaps(LocalDate start, LocalDate end, LocalDate otherStart, LocalDate otherEnd) {
        if (otherStart == null) {
            return false;
        }
        LocalDate endBound = end == null ? LocalDate.MAX : end;
        LocalDate otherEndBound = otherEnd == null ? LocalDate.MAX : otherEnd;
        return !start.isAfter(otherEndBound) && !otherStart.isAfter(endBound);
    }

    private String nextCode() {
        String prefix = "HD" + VietnamTime.today().format(CODE_MONTH) + "-";
        int max = 0;
        for (String code : contractRepository.findCodesByPrefix(prefix)) {
            if (code == null || !code.startsWith(prefix)) {
                continue;
            }
            String suffix = code.substring(prefix.length());
            if (!suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                max = Math.max(max, Integer.parseInt(suffix));
            }
        }
        return prefix + String.format("%04d", max + 1);
    }

    private EmploymentContractResponse toResponse(EmploymentContract contract) {
        Employee employee = contract.getEmployee();
        Store store = employee == null ? null : employee.getStore();
        ContractValidityStatus validity = ContractValidityRules.resolve(
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getTerminatedDate(),
                VietnamTime.today(),
                expiringWithinDays);
        ContractSigningStatus signing = contract.getSigningStatus();
        ContractPaymentMethod payment = contract.getPaymentMethod();
        ContractPayPeriod period = contract.getPayPeriod();
        return EmploymentContractResponse.builder()
                .id(contract.getId())
                .contractCode(contract.getContractCode())
                .employeeId(employee == null ? null : employee.getId())
                .employeeName(employee == null ? null : employee.getFullName())
                .positionLabel(positionLabel(employee))
                .hireDate(employee == null ? null : employee.getHireDate())
                .storeId(store == null ? null : store.getId())
                .storeName(store == null ? null : store.getStoreName())
                .signedDate(contract.getSignedDate())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .jobTitle(contract.getJobTitle())
                .workLocation(contract.getWorkLocation())
                .weeklyShiftCount(contract.getWeeklyShiftCount())
                .internalNote(contract.getInternalNote())
                .salary(contract.getSalary())
                .allowance(contract.getAllowance())
                .paymentMethod(payment)
                .paymentMethodLabel(payment == null ? null : payment.getLabel())
                .payPeriod(period)
                .payPeriodLabel(period == null ? null : period.getLabel())
                .benefitNote(contract.getBenefitNote())
                .originalFileName(contract.getOriginalFileName())
                .fileSizeBytes(contract.getFileSizeBytes())
                .hasFile(contract.getSignedFilePath() != null && !contract.getSignedFilePath().isBlank())
                .createdByName(creatorName(contract.getCreatedBy()))
                .createdAt(contract.getCreatedAt())
                .signingStatus(signing)
                .signingStatusLabel(signing == null ? null : signing.getLabel())
                .validityStatus(validity)
                .validityStatusLabel(validity.getLabel())
                .terminatedDate(contract.getTerminatedDate())
                .build();
    }

    private List<EmploymentContractListView.StoreChoice> storeChoices() {
        return storeRepository.findAll().stream()
                .sorted(Comparator.comparing(Store::getStoreName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(store -> EmploymentContractListView.StoreChoice.builder()
                        .id(store.getId())
                        .name(store.getStoreName())
                        .build())
                .toList();
    }

    private RoleName requireReader(AuthenticatedUser actor) {
        if (actor == null || (actor.getRoleName() != RoleName.ADMIN
                && actor.getRoleName() != RoleName.MANAGER
                && actor.getRoleName() != RoleName.STAFF)) {
            throw new BusinessException("Bạn không có quyền xem hợp đồng.");
        }
        return actor.getRoleName();
    }

    private Store requireManagedStore(AuthenticatedUser actor) {
        if (actor.getEmployeeId() == null) {
            throw new BusinessException("Bạn chưa được gán làm quản lý cửa hàng.");
        }
        return storeRepository.findByManager_Id(actor.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được gán làm quản lý cửa hàng."));
    }

    private void requireSameStore(Store managed, EmploymentContract contract) {
        Store store = contract.getEmployee() == null ? null : contract.getEmployee().getStore();
        if (store == null || managed.getId() == null || !managed.getId().equals(store.getId())) {
            throw new BusinessException("Bạn không có quyền xem hợp đồng này.");
        }
    }

    private static String positionLabel(Employee employee) {
        if (employee == null || employee.getUser() == null || employee.getUser().getRole() == null
                || employee.getUser().getRole().getRoleName() == null) {
            return "Nhân viên";
        }
        return switch (employee.getUser().getRole().getRoleName()) {
            case MANAGER -> "Quản lý";
            case ADMIN -> "Admin";
            case STAFF -> "Nhân viên";
        };
    }

    private static String creatorName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getEmployee() != null && user.getEmployee().getFullName() != null) {
            return user.getEmployee().getFullName();
        }
        return user.getUsername();
    }

    private static LocalDate requireStartDate(CreateEmploymentContractRequest request) {
        if (request == null || request.getStartDate() == null) {
            throw new BusinessException("Vui lòng nhập ngày bắt đầu hiệu lực.");
        }
        return request.getStartDate();
    }

    private static LocalDate requireEndDate(CreateEmploymentContractRequest request) {
        if (request == null || request.getEndDate() == null) {
            throw new BusinessException("Vui lòng nhập ngày kết thúc.");
        }
        return request.getEndDate();
    }

    private static String optionalNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 500) {
            throw new BusinessException("Ghi chú tối đa 500 ký tự.");
        }
        return trimmed;
    }

    private static Integer requireWeeklyShiftCount(Integer value) {
        if (value == null) {
            throw new BusinessException("Vui lòng nhập số ca làm việc quy định.");
        }
        if (value < 1) {
            throw new BusinessException("Số ca làm việc quy định phải lớn hơn 0.");
        }
        if (value > ContractShiftQuota.MAX_PER_WEEK) {
            throw new BusinessException("Số ca làm việc quy định không được quá 21 ca/tuần.");
        }
        return value;
    }

    private static BigDecimal requireSalary(BigDecimal salary, String label) {
        if (salary == null) {
            throw new BusinessException("Vui lòng nhập " + label.toLowerCase(Locale.ROOT) + ".");
        }
        if (salary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(label + " phải lớn hơn 0.");
        }
        return normalizeMoney(salary, label);
    }

    private static BigDecimal normalizeMoney(BigDecimal amount, String label) {
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        if (normalized.scale() > SALARY_SCALE) {
            throw new BusinessException(label + " chỉ được có tối đa 2 chữ số thập phân.");
        }
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > SALARY_INTEGER_DIGITS) {
            throw new BusinessException(label + " vượt quá giới hạn cho phép.");
        }
        return normalized;
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.length() > KEYWORD_MAX_LENGTH ? trimmed.substring(0, KEYWORD_MAX_LENGTH) : trimmed;
    }

    private static ContractValidityStatus parseValidity(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ContractValidityStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean matchesKeyword(EmploymentContractResponse contract, String keyword) {
        if (keyword == null) {
            return true;
        }
        String needle = keyword.toLowerCase(Locale.ROOT);
        if (contract.getContractCode() != null && contract.getContractCode().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (contract.getEmployeeName() != null && contract.getEmployeeName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return contract.getEmployeeId() != null && String.valueOf(contract.getEmployeeId()).contains(keyword);
    }

    private static Integer parseExpiringWithinDays(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw new IllegalStateException("coffee-hrm.contract.expiring-within-days phải là số nguyên dương.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("coffee-hrm.contract.expiring-within-days phải là số nguyên dương.", ex);
        }
    }
}
