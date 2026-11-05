package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.ContractTermType;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.ResignationProposalItemRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ContractRenewalService;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.PendingEmployeeActivation;
import com.example.coffee_hrm.service.impl.ContractFileStore.StoredContractFile;
import com.example.coffee_hrm.service.ocr.ContractOcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class ContractRenewalServiceImpl implements ContractRenewalService {

    private static final DateTimeFormatter CODE_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final EmploymentContractRepository contractRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ResignationProposalItemRepository resignationItemRepository;
    private final ContractFileStore fileStore;
    private final ContractOcrService contractOcrService;
    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;
    private final TransactionTemplate activationTransaction;

    public ContractRenewalServiceImpl(EmploymentContractRepository contractRepository,
                                      StoreRepository storeRepository,
                                      UserRepository userRepository,
                                      ResignationProposalItemRepository resignationItemRepository,
                                      ContractFileStore fileStore,
                                      ContractOcrService contractOcrService,
                                      NotificationService notificationService,
                                      EmployeeRepository employeeRepository,
                                      PlatformTransactionManager transactionManager) {
        this.contractRepository = contractRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.resignationItemRepository = resignationItemRepository;
        this.fileStore = fileStore;
        this.contractOcrService = contractOcrService;
        this.notificationService = notificationService;
        this.employeeRepository = employeeRepository;
        this.activationTransaction = new TransactionTemplate(transactionManager);
        this.activationTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    @Transactional
    public EmploymentContractResponse renew(AuthenticatedUser actor,
                                            Integer sourceContractId,
                                            CreateEmploymentContractRequest request,
                                            ContractFilePayload file) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Bạn không có quyền gia hạn hợp đồng.");
        }
        if (sourceContractId == null) {
            throw new BusinessException("Không tìm thấy hợp đồng cần gia hạn.");
        }
        requirePdf(file);
        LocalDate today = VietnamTime.today();
        LocalDate startDate = requireStart(request, today);
        LocalDate endDate = requireEnd(request, startDate);
        BigDecimal salary = requireSalary(request == null ? null : request.getSalary());
        Integer weeklyShiftCount = requireWeeklyShifts(request == null ? null : request.getWeeklyShiftCount());
        String note = optionalNote(request == null ? null : request.getInternalNote());

        EmploymentContract source = contractRepository.lockById(sourceContractId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hợp đồng cần gia hạn."));
        Employee employee = source.getEmployee();
        if (employee == null || employee.getStore() == null) {
            throw new BusinessException("Nhân viên chưa thuộc cửa hàng.");
        }
        if (request.getEmployeeId() != null && !request.getEmployeeId().equals(employee.getId())) {
            throw new BusinessException("Nhân viên không khớp hợp đồng cần gia hạn.");
        }
        Store managed = requireManagedStore(actor);
        if (!managed.getId().equals(employee.getStore().getId())) {
            throw new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý.");
        }
        if (actor.getEmployeeId() != null && actor.getEmployeeId().equals(employee.getId())) {
            throw new BusinessException("Không thể gia hạn hợp đồng cho chính bạn.");
        }
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException("Chỉ gia hạn hợp đồng cho nhân viên đang làm việc.");
        }
        if (resignationItemRepository.countPendingByEmployeeId(employee.getId()) > 0) {
            throw new BusinessException("Nhân viên đang có yêu cầu chấm dứt hợp đồng chờ Admin phê duyệt.");
        }
        if (source.getEndDate() == null || source.getContractTerm() == ContractTermType.KHONG_XAC_DINH_THOI_HAN) {
            throw new BusinessException("Hợp đồng không xác định thời hạn không được gia hạn.");
        }
        if (source.getRenewalDeclinedAt() != null) {
            throw new BusinessException("Không thể gia hạn hợp đồng đã ngừng gia hạn.");
        }
        if (contractRepository.existsByPreviousContract_Id(source.getId())) {
            throw new BusinessException("Hợp đồng này đã được gia hạn.");
        }
        requireRenewableStatus(source, today, false);
        requireNoOverlap(employee.getId(), source.getId(), startDate, endDate, today);
        ContractOcrService.Acceptance acceptance = contractOcrService.requireAccepted(
                file.originalName(), file.content(), employee);
        User actorUser = actor.getUserId() == null ? null : userRepository.findById(actor.getUserId()).orElse(null);
        StoredContractFile stored = fileStore.store(file);
        try {
            EmploymentContract saved = insertRenewal(
                    source, employee, actorUser, startDate, endDate, salary, weeklyShiftCount, note, stored, acceptance, actor, today);
            notifyRenewed(saved, employee, actorUser);
            if (!startDate.isAfter(today)) {
                PendingEmployeeActivation.afterContractSaved(
                        employeeRepository, contractRepository, employee.getId(), today);
            }
            return EmploymentContractResponse.builder()
                    .id(saved.getId())
                    .contractCode(saved.getContractCode())
                    .employeeId(employee.getId())
                    .build();
        } catch (RuntimeException ex) {
            fileStore.delete(stored.storedName());
            throw ex;
        }
    }

    @Override
    public int activateDue(LocalDate today) {
        if (today == null) {
            return 0;
        }
        int activated = 0;
        for (Integer id : contractRepository.findDueActivationIds(today)) {
            try {
                Boolean done = activationTransaction.execute(status -> activateOne(id, today));
                if (Boolean.TRUE.equals(done)) {
                    activated++;
                }
            } catch (RuntimeException ex) {
                log.error("Không chuyển được hợp đồng {} sang Đang hiệu lực.", id, ex);
            }
        }
        return activated;
    }

    @Override
    @Transactional
    public EmploymentContractResponse stopRenewal(AuthenticatedUser actor, Integer contractId, String reason, String note) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Bạn không có quyền ngừng gia hạn hợp đồng.");
        }
        if (contractId == null) {
            throw new BusinessException("Không tìm thấy hợp đồng cần ngừng gia hạn.");
        }
        String decision = requireDeclineReason(reason, note);
        EmploymentContract source = contractRepository.lockById(contractId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hợp đồng cần ngừng gia hạn."));
        Employee employee = source.getEmployee();
        if (employee == null || employee.getStore() == null) {
            throw new BusinessException("Nhân viên chưa thuộc cửa hàng.");
        }
        Store managed = requireManagedStore(actor);
        if (!managed.getId().equals(employee.getStore().getId())) {
            throw new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý.");
        }
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException("Chỉ ngừng gia hạn hợp đồng của nhân viên đang làm việc.");
        }
        if (source.getRenewalDeclinedAt() != null) {
            throw new BusinessException("Hợp đồng này đã được ngừng gia hạn.");
        }
        if (contractRepository.existsByPreviousContract_Id(source.getId())) {
            throw new BusinessException("Hợp đồng này đã được gia hạn.");
        }
        LocalDate today = VietnamTime.today();
        if (ContractValidityRules.resolve(source, today) != ContractValidityStatus.CHO_GIA_HAN) {
            throw new BusinessException("Chỉ ngừng gia hạn khi hợp đồng đang ở trạng thái Chờ gia hạn.");
        }
        source.setRenewalDeclinedAt(VietnamTime.now());
        source.setRenewalDeclineReason(decision);
        contractRepository.save(source);
        log.info("Hợp đồng {} chuyển sang Đã chấm dứt trước thời hạn. Nhân viên vẫn làm đến {}.",
                source.getContractCode(), source.getEndDate());
        return EmploymentContractResponse.builder()
                .id(source.getId())
                .contractCode(source.getContractCode())
                .employeeId(employee.getId())
                .build();
    }

    @Override
    public int recordExpiredRenewals(LocalDate today) {
        if (today == null) {
            return 0;
        }
        int recorded = 0;
        for (Integer id : contractRepository.findRenewedSourcesPastEnd(today)) {
            try {
                Boolean done = activationTransaction.execute(status -> recordExpiredOne(id, today));
                if (Boolean.TRUE.equals(done)) {
                    recorded++;
                }
            } catch (RuntimeException ex) {
                log.error("Không ghi nhận hết hạn cho hợp đồng {}.", id, ex);
            }
        }
        return recorded;
    }

    @Override
    public int lockDeclinedAccounts(LocalDate today) {
        if (today == null) {
            return 0;
        }
        int locked = 0;
        for (Integer id : contractRepository.findDeclinedIdsPastEnd(today)) {
            try {
                Boolean done = activationTransaction.execute(status -> lockOne(id, today));
                if (Boolean.TRUE.equals(done)) {
                    locked++;
                }
            } catch (RuntimeException ex) {
                log.error("Không khóa được tài khoản cho hợp đồng đã ngừng gia hạn {}.", id, ex);
            }
        }
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean activateOne(Integer contractId, LocalDate today) {
        EmploymentContract renewal = contractRepository.lockById(contractId).orElse(null);
        if (renewal == null || renewal.getActivatedAt() != null || renewal.getPreviousContract() == null
                || renewal.getStartDate() == null || renewal.getStartDate().isAfter(today)) {
            return false;
        }
        EmploymentContract source = contractRepository.lockById(renewal.getPreviousContract().getId()).orElse(null);
        if (source == null) {
            log.error("Hợp đồng gia hạn {} không còn hợp đồng nguồn.", contractId);
            return false;
        }
        ContractValidityStatus sourceStatus = ContractValidityRules.resolve(source, today);
        if (sourceStatus == ContractValidityStatus.DA_CHAM_DUT
                || sourceStatus == ContractValidityStatus.DA_CHAM_DUT_TRUOC_THOI_HAN) {
            log.error("Không kích hoạt hợp đồng {} vì hợp đồng nguồn đã chấm dứt.", contractId);
            return false;
        }
        if (source.getEndDate() != null && source.getEndDate().isBefore(today) && source.getSupersededDate() == null) {
            source.setSupersededDate(source.getEndDate().plusDays(1));
            contractRepository.save(source);
            log.info("Hợp đồng nguồn {} đã đến ngày kết thúc và chuyển sang Đã hết hạn.", source.getContractCode());
        }
        renewal.setActivatedAt(VietnamTime.now());
        contractRepository.save(renewal);
        log.info("Hợp đồng {} chuyển từ Chờ hiệu lực sang Đang hiệu lực.", renewal.getContractCode());
        if (renewal.getEmployee() != null) {
            PendingEmployeeActivation.afterContractSaved(
                    employeeRepository, contractRepository, renewal.getEmployee().getId(), today);
        }
        return true;
    }

    private boolean recordExpiredOne(Integer contractId, LocalDate today) {
        EmploymentContract source = contractRepository.lockById(contractId).orElse(null);
        if (source == null || source.getSupersededDate() != null || source.getEndDate() == null
                || !source.getEndDate().isBefore(today)) {
            return false;
        }
        if (!contractRepository.existsByPreviousContract_Id(source.getId())) {
            return false;
        }
        source.setSupersededDate(source.getEndDate().plusDays(1));
        contractRepository.save(source);
        log.info("Hợp đồng {} chuyển từ Đã gia hạn sang Đã hết hạn.", source.getContractCode());
        return true;
    }

    private boolean lockOne(Integer contractId, LocalDate today) {
        EmploymentContract contract = contractRepository.lockById(contractId).orElse(null);
        if (contract == null || contract.getRenewalDeclinedAt() == null || contract.getEndDate() == null
                || !contract.getEndDate().isBefore(today)) {
            return false;
        }
        Employee employee = contract.getEmployee();
        if (employee == null || employee.getId() == null || employee.getStatus() != EmployeeStatus.ACTIVE) {
            return false;
        }
        for (EmploymentContract other : contractRepository.findByEmployeeId(employee.getId())) {
            if (other.getId() != null && other.getId().equals(contract.getId())) {
                continue;
            }
            if (other.getStartDate() != null && other.getStartDate().isAfter(contract.getEndDate())) {
                log.info("Không khóa nhân viên {} vì còn hợp đồng {} sau ngày kết thúc.",
                        employee.getId(), other.getContractCode());
                return false;
            }
        }
        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setTerminationDate(contract.getEndDate().plusDays(1));
        employeeRepository.save(employee);
        userRepository.findByEmployee_Id(employee.getId()).ifPresent(account -> {
            if (account.getRole() != null && account.getRole().getRoleName() == RoleName.STAFF
                    && !Boolean.FALSE.equals(account.getIsActive())) {
                account.setIsActive(false);
                userRepository.save(account);
            }
        });
        log.info("Hợp đồng {} chuyển từ Đã chấm dứt trước thời hạn sang Đã chấm dứt. Đã khóa tài khoản nhân viên {}.",
                contract.getContractCode(), employee.getId());
        return true;
    }

    private EmploymentContract insertRenewal(EmploymentContract source,
                                             Employee employee,
                                             User actorUser,
                                             LocalDate startDate,
                                             LocalDate endDate,
                                             BigDecimal salary,
                                             Integer weeklyShiftCount,
                                             String note,
                                             StoredContractFile stored,
                                             ContractOcrService.Acceptance acceptance,
                                             AuthenticatedUser actor,
                                             LocalDate today) {
        try {
            EmploymentContract created = contractRepository.saveAndFlush(EmploymentContract.builder()
                        .contractCode(nextCode())
                        .employee(employee)
                        .previousContract(source)
                        .startDate(startDate)
                        .endDate(endDate)
                        .contractTerm(ContractTermType.XAC_DINH_THOI_HAN)
                        .salary(salary)
                        .weeklyShiftCount(weeklyShiftCount)
                        .internalNote(note)
                        .originalFileName(stored.originalName())
                        .fileContentType(stored.contentType())
                        .fileSizeBytes(stored.size())
                        .createdBy(actorUser)
                        .renewedBy(actorUser)
                        .renewedAt(VietnamTime.now())
                        .activatedAt(startDate.isAfter(today) ? null : VietnamTime.now())
                        .signingStatus(ContractSigningStatus.DA_TAI_BAN_KY)
                        .signedFilePath(stored.storedName())
                        .terminatedDate(null)
                        .signatureFileHash(acceptance.fileHash())
                        .signatureCheckedAt(VietnamTime.now())
                        .signatureCheckResult(acceptance.result())
                        .signatureCheckedByUserId(actor.getUserId())
                        .build());
            return created;
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Hợp đồng này đã được gia hạn hoặc số hợp đồng bị trùng. Hãy tải lại và thử lại.");
        }
    }

    private void notifyRenewed(EmploymentContract contract, Employee employee, User actorUser) {
        try {
            List<User> recipients = new ArrayList<>();
            userRepository.findByEmployee_Id(employee.getId()).ifPresent(recipients::add);
            recipients.addAll(userRepository.findActiveByRoleName(RoleName.ADMIN));
            if (recipients.isEmpty()) {
                return;
            }
            String actorName = actorUser != null && actorUser.getEmployee() != null
                    && actorUser.getEmployee().getFullName() != null
                    ? actorUser.getEmployee().getFullName()
                    : "Quản lý cửa hàng";
            notificationService.notifyUsers(
                    recipients,
                    "Hợp đồng đã được gia hạn",
                    actorName + " đã gia hạn hợp đồng thành " + contract.getContractCode()
                            + " cho " + employee.getFullName()
                            + ". Hiệu lực từ " + contract.getStartDate() + " đến " + contract.getEndDate() + ".",
                    NotificationType.CONTRACT_RENEWED,
                    "EMPLOYMENT_CONTRACT",
                    contract.getId());
        } catch (RuntimeException ex) {
            log.warn("Đã tạo hợp đồng {} nhưng chưa gửi được thông báo gia hạn.", contract.getContractCode(), ex);
        }
    }

    private void requireRenewableStatus(EmploymentContract source, LocalDate today, boolean hasDirectRenewal) {
        ContractValidityStatus status = ContractValidityRules.resolve(source, today, hasDirectRenewal);
        if (status.isRenewable()) {
            return;
        }
        throw new BusinessException("Không thể gia hạn hợp đồng đang ở trạng thái " + status.getLabel() + ".");
    }

    private void requireNoOverlap(Integer employeeId, Integer sourceId, LocalDate start, LocalDate end, LocalDate today) {
        for (EmploymentContract contract : contractRepository.findByEmployeeId(employeeId)) {
            if (contract.getId() != null && contract.getId().equals(sourceId)) {
                continue;
            }
            if (contract.getStartDate() == null) {
                continue;
            }
            LocalDate lastWorkDay = ContractValidityRules.lastWorkDay(contract);
            boolean stillCoversToday = lastWorkDay == null || !lastWorkDay.isBefore(today);
            if (ContractValidityRules.resolve(contract, today).isClosed() && !stillCoversToday) {
                continue;
            }
            LocalDate otherEnd = lastWorkDay == null ? LocalDate.MAX : lastWorkDay;
            LocalDate newEnd = end == null ? LocalDate.MAX : end;
            if (!start.isAfter(otherEnd) && !contract.getStartDate().isAfter(newEnd)) {
                throw new BusinessException("Hợp đồng gia hạn bị trùng thời gian hiệu lực với hợp đồng khác.");
            }
        }
    }

    private Store requireManagedStore(AuthenticatedUser actor) {
        if (actor.getEmployeeId() == null) {
            throw new BusinessException("Bạn chưa được gán làm quản lý cửa hàng.");
        }
        return storeRepository.findByManager_Id(actor.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được gán làm quản lý cửa hàng."));
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

    private static void requirePdf(ContractFilePayload file) {
        if (file == null || file.content() == null || file.content().length == 0) {
            throw new BusinessException("Vui lòng tải lên file PDF hợp đồng đã ký.");
        }
        String name = file.originalName() == null ? "" : file.originalName().toLowerCase(Locale.ROOT);
        boolean header = file.content().length >= 5
                && file.content()[0] == '%' && file.content()[1] == 'P'
                && file.content()[2] == 'D' && file.content()[3] == 'F';
        if (!name.endsWith(".pdf") || !header) {
            throw new BusinessException("Hợp đồng gia hạn phải là file PDF hợp lệ.");
        }
    }

    private static LocalDate requireStart(CreateEmploymentContractRequest request, LocalDate today) {
        if (request == null || request.getStartDate() == null) {
            throw new BusinessException("Vui lòng nhập ngày bắt đầu hiệu lực.");
        }
        if (request.getStartDate().isBefore(today)) {
            throw new BusinessException("Ngày bắt đầu hợp đồng gia hạn không được nằm trong quá khứ.");
        }
        return request.getStartDate();
    }

    private static LocalDate requireEnd(CreateEmploymentContractRequest request, LocalDate startDate) {
        if (request.getEndDate() == null) {
            throw new BusinessException("Vui lòng nhập ngày kết thúc hợp đồng.");
        }
        if (!request.getEndDate().isAfter(startDate)) {
            throw new BusinessException("Ngày kết thúc phải sau ngày bắt đầu.");
        }
        ContractValidityRules.requireFixedTermLength(startDate, request.getEndDate());
        return request.getEndDate();
    }

    private static BigDecimal requireSalary(BigDecimal salary) {
        if (salary == null || salary.signum() <= 0) {
            throw new BusinessException("Mức lương phải lớn hơn 0.");
        }
        return salary;
    }

    private static Integer requireWeeklyShifts(Integer count) {
        if (count == null || count < 1 || count > 21) {
            throw new BusinessException("Số ca làm việc mỗi tuần phải từ 1 đến 21.");
        }
        return count;
    }

    private static String requireDeclineReason(String reason, String note) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Vui lòng chọn lý do ngừng gia hạn.");
        }
        String label = switch (reason.trim()) {
            case "employee" -> "Nhân viên không muốn gia hạn";
            case "store" -> "Cửa hàng không gia hạn";
            case "position" -> "Hết nhu cầu vị trí";
            case "other" -> "Lý do khác";
            default -> throw new BusinessException("Lý do ngừng gia hạn không hợp lệ.");
        };
        String extra = note == null ? "" : note.trim();
        String decision = extra.isEmpty() ? label : label + ". " + extra;
        if (decision.length() > 500) {
            throw new BusinessException("Lý do ngừng gia hạn không được quá 500 ký tự.");
        }
        return decision;
    }

    private static String optionalNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > 500) {
            throw new BusinessException("Ghi chú không được quá 500 ký tự.");
        }
        return trimmed;
    }
}
