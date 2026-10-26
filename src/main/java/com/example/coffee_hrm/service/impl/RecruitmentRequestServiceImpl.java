package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.Gender;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RecruitmentProposalStatus;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.TemporaryPasswordGenerator;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateRecruitmentRequest;
import com.example.coffee_hrm.dto.request.RecruitmentCandidateRequest;
import com.example.coffee_hrm.dto.response.CandidateApprovalResult;
import com.example.coffee_hrm.dto.response.CandidateRejectionResult;
import com.example.coffee_hrm.security.StaffEmploymentGuard;
import com.example.coffee_hrm.dto.response.RecruitmentContactCheckResponse;
import com.example.coffee_hrm.dto.response.RecruitmentCandidateResponse;
import com.example.coffee_hrm.dto.response.RecruitmentManagerOption;
import com.example.coffee_hrm.dto.response.RecruitmentRequestResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.RecruitmentCandidate;
import com.example.coffee_hrm.entity.RecruitmentRequest;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.RecruitmentCandidateRepository;
import com.example.coffee_hrm.repository.RecruitmentRequestRepository;
import com.example.coffee_hrm.repository.RoleRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AccountMailService;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentRequestServiceImpl implements RecruitmentRequestService {

    static final String EMAIL_EXISTS = "Email đã tồn tại trong hệ thống.";
    static final String EMAIL_APPROVED_OR_WORKING = "Email đã trùng với nhân viên đã được duyệt hoặc đang làm việc.";
    static final String EMAIL_ACTIVE_EMPLOYEE = "Email đã trùng với nhân viên đang hoạt động trong hệ thống.";
    static final String PHONE_ACTIVE_EMPLOYEE = "Số điện thoại đã trùng với nhân viên đang hoạt động trong hệ thống.";
    static final String EMAIL_PENDING = "Email này đang được dùng trong một đề xuất tuyển nhân sự đang chờ duyệt.";
    static final String EMAIL_INVALID = "Email không đúng định dạng.";
    static final String NAME_NOT_LETTERS = "Họ và tên chỉ được chứa chữ cái, không được nhập số.";
    static final String TITLE_EXISTS = "Tên đề xuất đã trùng với một đơn đã gửi cho Admin.";
    static final String PHONE_EXISTS = "Số điện thoại đã tồn tại trong hệ thống.";
    static final String PHONE_PENDING = "Số điện thoại này đang được dùng trong một đề xuất tuyển nhân sự đang chờ duyệt.";
    static final String ALREADY_APPROVED = "Nhân viên này đã được duyệt. Không tạo thêm tài khoản.";
    static final String REHIRE_WORKING = "Không thể đề xuất tuyển dụng lại nhân viên đang làm việc.";
    static final String REHIRE_PENDING = "Nhân viên này đang có đề xuất tuyển dụng lại chờ duyệt.";
    static final String REHIRE_NOT_STAFF = "Chỉ được tuyển dụng lại nhân viên Staff đã nghỉ việc.";
    private static final int MAX_CANDIDATES = 30;
    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    private static final String RECRUITMENT_REF = "RECRUITMENT_REQUEST";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("^[\\p{L}]+(?:\\s+[\\p{L}]+)*$");
    private static final List<EmployeeStatus> WORKING_STATUSES = List.of(EmployeeStatus.ACTIVE, EmployeeStatus.ON_LEAVE);

    private final RecruitmentRequestRepository recruitmentRequestRepository;
    private final RecruitmentCandidateRepository recruitmentCandidateRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    private final AccountMailService accountMailService;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;

    @Override
    @Transactional
    public RecruitmentRequestResponse create(AuthenticatedUser actor, CreateRecruitmentRequest request) {
        Store store = requireManagedStore(actor);
        Employee manager = employeeRepository.findById(actor.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên của Manager."));
        String title = requireTitle(request.getTitle());
        String note = normalizeNote(request.getNote());
        List<NormalizedCandidate> people = normalizeCandidates(request.getCandidates());

        RecruitmentRequest proposal = RecruitmentRequest.builder()
                .store(store)
                .requestedBy(manager)
                .title(title)
                .note(note)
                .status(RecruitmentProposalStatus.PENDING)
                .candidates(new ArrayList<>())
                .build();
        int sortOrder = 1;
        for (NormalizedCandidate person : people) {
            RecruitmentCandidate candidate = RecruitmentCandidate.builder()
                    .sortOrder(sortOrder++)
                    .fullName(person.fullName())
                    .dateOfBirth(person.dateOfBirth())
                    .gender(person.gender())
                    .email(person.email())
                    .phone(person.phone())
                    .address(person.address())
                    .status(RecruitmentStatus.PENDING)
                    .rehireEmployee(person.rehireEmployee())
                    .build();
            candidate.setRequest(proposal);
            proposal.getCandidates().add(candidate);
        }

        RecruitmentRequest saved;
        try {
            saved = recruitmentRequestRepository.save(proposal);
        } catch (DataIntegrityViolationException ex) {
            log.error("Không lưu được đề xuất tuyển nhân sự vì email hoặc số điện thoại bị trùng", ex);
            throw new BusinessException("Email hoặc số điện thoại bị trùng với một đề xuất đang chờ duyệt.");
        }

        String managerName = manager.getFullName() != null ? manager.getFullName() : actor.getDisplayName();
        notificationService.notifyUsers(
                userRepository.findActiveByRoleName(RoleName.ADMIN),
                "Đề xuất tuyển nhân sự mới",
                "Manager " + managerName + " đã gửi đề xuất \"" + saved.getTitle() + "\" ("
                        + saved.getCandidates().size() + " nhân viên) cho cửa hàng " + store.getStoreName() + ".",
                NotificationType.RECRUITMENT_REQUEST_CREATED,
                RECRUITMENT_REF,
                saved.getId());
        log.info("Manager {} đã tạo đề xuất tuyển nhân sự {} với {} nhân viên",
                manager.getId(), saved.getId(), saved.getCandidates().size());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecruitmentRequestResponse> listMine(AuthenticatedUser actor) {
        requireManager(actor);
        return prepareList(recruitmentRequestRepository.findMine(actor.getEmployeeId())).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecruitmentRequestResponse getMine(AuthenticatedUser actor, Integer id) {
        requireManager(actor);
        RecruitmentRequest request = recruitmentRequestRepository.findDetailById(id)
                .filter(item -> item.getRequestedBy() != null
                        && actor.getEmployeeId().equals(item.getRequestedBy().getId()))
                .orElseThrow(() -> new BusinessException("Không tìm thấy đề xuất tuyển nhân sự."));
        return toResponse(request);
    }

    @Override
    @Transactional(readOnly = true)
    public String managedStoreLabel(AuthenticatedUser actor) {
        return requireManagedStore(actor).getStoreName();
    }

    @Override
    @Transactional(readOnly = true)
    public RecruitmentContactCheckResponse checkActiveEmployeeContact(AuthenticatedUser actor,
                                                                       String email,
                                                                       String phone) {
        requireManager(actor);
        return RecruitmentContactCheckResponse.builder()
                .emailMessage(activeEmailMessage(email))
                .phoneMessage(activePhoneMessage(phone))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecruitmentRequestResponse> listForAdmin(AuthenticatedUser actor,
                                                          Integer storeId,
                                                          Integer managerId,
                                                          RecruitmentProposalStatus status,
                                                          LocalDate createdFrom,
                                                          LocalDate createdTo) {
        requireAdmin(actor);
        if (createdFrom != null && createdTo != null && createdTo.isBefore(createdFrom)) {
            throw new BusinessException("Khoảng thời gian không hợp lệ.");
        }
        LocalDateTime from = createdFrom == null ? null : createdFrom.atStartOfDay();
        LocalDateTime toExclusive = createdTo == null ? null : createdTo.plusDays(1).atStartOfDay();
        return prepareList(recruitmentRequestRepository.search(storeId, managerId, status, from, toExclusive)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecruitmentRequestResponse getForAdmin(AuthenticatedUser actor, Integer id) {
        requireAdmin(actor);
        return toResponse(requireRequest(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecruitmentManagerOption> listManagerOptions() {
        return storeRepository.findAllWithManager().stream()
                .filter(store -> store.getManager() != null)
                .map(store -> RecruitmentManagerOption.builder()
                        .id(store.getManager().getId())
                        .name(store.getManager().getFullName())
                        .storeName(store.getStoreName())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public CandidateApprovalResult approveCandidate(AuthenticatedUser actor, Integer requestId, Integer candidateId) {
        requireAdmin(actor);
        LockedProposal locked = lockProposal(requestId);
        RecruitmentCandidate candidate = requireCandidate(locked.candidates(), candidateId);
        requirePendingCandidate(candidate, true);

        String temporaryPassword = temporaryPasswordGenerator.generate();
        Employee employee;
        User account;
        boolean reactivated;
        try {
            Employee former = resolveFormerForApproval(candidate);
            if (former != null) {
                account = reactivateFormerEmployee(former, candidate, locked.request().getStore(), temporaryPassword);
                employee = former;
                reactivated = true;
            } else {
                if (emailTaken(candidate.getEmail())) {
                    throw new BusinessException(EMAIL_EXISTS + " Không thể tạo tài khoản.");
                }
                employee = employeeRepository.save(Employee.builder()
                        .fullName(candidate.getFullName())
                        .phone(candidate.getPhone())
                        .email(candidate.getEmail())
                        .address(candidate.getAddress())
                        .store(locked.request().getStore())
                        .status(EmployeeStatus.ACTIVE)
                        .certificationStatus(CertificationStatus.NOTCERTIFIED)
                        .hasCertificate(false)
                        .hireDate(VietnamTime.today())
                        .build());
                account = userRepository.save(User.builder()
                        .username(candidate.getEmail())
                        .passwordHash(passwordEncoder.encode(temporaryPassword))
                        .role(requireStaffRole())
                        .employee(employee)
                        .isActive(true)
                        .build());
                reactivated = false;
            }
            candidate.setStatus(RecruitmentStatus.APPROVED);
            candidate.setReviewedAt(VietnamTime.now());
            candidate.setReviewedBy(requireUser(actor.getUserId()));
            candidate.setCreatedEmployee(employee);
            candidate.setRejectReason(null);
            refreshProposalStatus(locked.request(), locked.candidates());
            recruitmentRequestRepository.save(locked.request());
            if (account.getUsername() == null || !account.getUsername().equalsIgnoreCase(candidate.getEmail())) {
                account.setUsername(candidate.getEmail());
                account = userRepository.save(account);
            }
            userRepository.flush();
            employeeRepository.flush();
            if (!passwordEncoder.matches(temporaryPassword, account.getPasswordHash())
                    || !Boolean.TRUE.equals(account.getIsActive())
                    || account.getEmployee() == null
                    || account.getEmployee().getStatus() == EmployeeStatus.TERMINATED) {
                throw new BusinessException("Không mở được tài khoản email để đăng nhập. Chưa gửi mật khẩu mới.");
            }
        } catch (DataIntegrityViolationException ex) {
            log.error("Không tạo được tài khoản cho nhân viên {} trong đề xuất {} vì email bị trùng",
                    candidateId, requestId, ex);
            throw new BusinessException(EMAIL_EXISTS + " Không thể tạo tài khoản.");
        }

        notifyManager(
                locked.request(),
                "Đề xuất tuyển nhân sự đã được duyệt",
                "Nhân viên " + candidate.getFullName() + " trong đề xuất \"" + locked.request().getTitle()
                        + "\" đã được Admin duyệt.",
                NotificationType.RECRUITMENT_REQUEST_APPROVED);

        accountMailService.sendTemporaryPassword(
                candidate.getEmail(),
                candidate.getFullName(),
                candidate.getEmail(),
                temporaryPassword);
        log.info("Admin {} đã duyệt nhân viên {} trong đề xuất {} và {} nhân viên {}",
                actor.getUserId(), candidateId, requestId,
                reactivated ? "kích hoạt lại" : "tạo",
                employee.getId());
        return new CandidateApprovalResult(candidate.getFullName(), reactivated);
    }

    @Override
    @Transactional
    public CandidateRejectionResult rejectCandidate(AuthenticatedUser actor, Integer requestId, Integer candidateId, String rejectReason) {
        requireAdmin(actor);
        LockedProposal locked = lockProposal(requestId);
        RecruitmentCandidate candidate = requireCandidate(locked.candidates(), candidateId);
        requirePendingCandidate(candidate, false);
        candidate.setStatus(RecruitmentStatus.REJECTED);
        candidate.setRejectReason(normalizeRejectReason(rejectReason));
        candidate.setReviewedAt(VietnamTime.now());
        candidate.setReviewedBy(requireUser(actor.getUserId()));
        refreshProposalStatus(locked.request(), locked.candidates());
        recruitmentRequestRepository.save(locked.request());
        recruitmentRequestRepository.flush();
        notifyManager(
                locked.request(),
                "Đề xuất tuyển nhân sự bị từ chối",
                "Nhân viên " + candidate.getFullName() + " trong đề xuất \"" + locked.request().getTitle()
                        + "\" đã bị từ chối.",
                NotificationType.RECRUITMENT_REQUEST_REJECTED);
        boolean noticeEmailSent = sendRejectionNotice(candidate);
        log.info("Admin {} đã từ chối nhân viên {} trong đề xuất {}", actor.getUserId(), candidateId, requestId);
        return new CandidateRejectionResult(candidate.getFullName(), noticeEmailSent);
    }

    private boolean sendRejectionNotice(RecruitmentCandidate candidate) {
        try {
            boolean sent = accountMailService.sendRecruitmentRejection(
                    candidate.getEmail(),
                    candidate.getFullName(),
                    candidate.getRejectReason());
            if (!sent) {
                log.error("Đã lưu từ chối ứng viên {} nhưng không gửi được email thông báo không trúng tuyển tới {}",
                        candidate.getId(), candidate.getEmail());
            }
            return sent;
        } catch (RuntimeException ex) {
            log.error("Gửi email thông báo không trúng tuyển thất bại tới {} sau khi đã lưu trạng thái từ chối của ứng viên {}",
                    candidate.getEmail(), candidate.getId(), ex);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int countPendingRequests() {
        return recruitmentRequestRepository.countByStatusIn(List.of(
                RecruitmentProposalStatus.PENDING,
                RecruitmentProposalStatus.PARTIALLY_PROCESSED));
    }

    private LockedProposal lockProposal(Integer requestId) {
        RecruitmentRequest request = recruitmentRequestRepository.lockById(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đề xuất tuyển nhân sự."));
        List<RecruitmentCandidate> candidates = new ArrayList<>(recruitmentCandidateRepository.lockByRequestId(requestId));
        candidates.sort(candidateOrder());
        return new LockedProposal(request, candidates);
    }

    private RecruitmentCandidate requireCandidate(List<RecruitmentCandidate> candidates, Integer candidateId) {
        return candidates.stream()
                .filter(candidate -> candidateId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên trong đề xuất này."));
    }

    private void requirePendingCandidate(RecruitmentCandidate candidate, boolean approving) {
        if (candidate.getCreatedEmployee() != null || candidate.getStatus() == RecruitmentStatus.APPROVED) {
            throw new BusinessException(approving
                    ? ALREADY_APPROVED
                    : "Nhân viên này đã được duyệt.");
        }
        if (candidate.getStatus() == RecruitmentStatus.REJECTED) {
            throw new BusinessException("Nhân viên này đã bị từ chối.");
        }
        if (candidate.getStatus() != RecruitmentStatus.PENDING) {
            throw new BusinessException("Chỉ có thể xử lý nhân viên đang chờ duyệt.");
        }
    }

    private void refreshProposalStatus(RecruitmentRequest request, List<RecruitmentCandidate> candidates) {
        long pending = candidates.stream()
                .filter(candidate -> candidate.getStatus() == RecruitmentStatus.PENDING)
                .count();
        if (pending == candidates.size()) {
            request.setStatus(RecruitmentProposalStatus.PENDING);
        } else if (pending == 0) {
            request.setStatus(RecruitmentProposalStatus.COMPLETED);
        } else {
            request.setStatus(RecruitmentProposalStatus.PARTIALLY_PROCESSED);
        }
    }

    private RecruitmentRequest requireRequest(Integer id) {
        return recruitmentRequestRepository.findDetailById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đề xuất tuyển nhân sự."));
    }

    private List<NormalizedCandidate> normalizeCandidates(List<RecruitmentCandidateRequest> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException("Vui lòng thêm ít nhất một nhân viên vào danh sách.");
        }
        if (candidates.size() > MAX_CANDIDATES) {
            throw new BusinessException("Mỗi đề xuất chứa tối đa 30 nhân viên.");
        }
        List<NormalizedCandidate> normalized = new ArrayList<>();
        for (RecruitmentCandidateRequest candidate : candidates) {
            normalized.add(new NormalizedCandidate(
                    requireFullName(candidate.getFullName()),
                    requireDateOfBirth(candidate.getDateOfBirth()),
                    candidate.getGender(),
                    normalizeEmail(candidate.getEmail()),
                    normalizePhone(candidate.getPhone()),
                    normalizeAddress(candidate.getAddress()),
                    candidate.getRehireEmployeeId(),
                    null));
        }
        assertUniqueInProposal(normalized);
        List<NormalizedCandidate> linked = new ArrayList<>();
        Set<Integer> rehireIds = new HashSet<>();
        for (NormalizedCandidate candidate : normalized) {
            Employee rehire = resolveRehire(candidate);
            if (rehire != null && !rehireIds.add(rehire.getId())) {
                throw new BusinessException("Mỗi nhân viên đã nghỉ việc chỉ được chọn một lần trong đề xuất.");
            }
            assertPhoneAvailable(candidate.phone(), rehire);
            linked.add(new NormalizedCandidate(
                    candidate.fullName(),
                    candidate.dateOfBirth(),
                    candidate.gender(),
                    candidate.email(),
                    candidate.phone(),
                    candidate.address(),
                    candidate.rehireEmployeeId(),
                    rehire));
        }
        return linked;
    }

    private Employee resolveFormerForApproval(RecruitmentCandidate candidate) {
        Employee linked = candidate.getRehireEmployee();
        if (linked != null && linked.getId() != null) {
            Employee locked = employeeRepository.lockByIdForLeaveRequest(linked.getId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên để tuyển dụng lại."));
            if (locked.getStatus() != EmployeeStatus.TERMINATED) {
                throw new BusinessException(REHIRE_WORKING);
            }
            return locked;
        }
        return employeeRepository.findByEmailIgnoreCaseWithAccount(candidate.getEmail())
                .filter(employee -> employee.getStatus() == EmployeeStatus.TERMINATED)
                .filter(employee -> StaffEmploymentGuard.isRehirableStaff(employee.getUser()))
                .map(employee -> employeeRepository.lockByIdForLeaveRequest(employee.getId()).orElse(employee))
                .filter(employee -> employee.getStatus() == EmployeeStatus.TERMINATED)
                .orElse(null);
    }

    private User reactivateFormerEmployee(Employee employee,
                                          RecruitmentCandidate candidate,
                                          Store store,
                                          String temporaryPassword) {
        if (!StaffEmploymentGuard.isRehirableStaff(employee.getUser())) {
            throw new BusinessException(REHIRE_NOT_STAFF);
        }
        User account = employee.getUser();
        if (account == null || account.getId() == null) {
            account = userRepository.findByEmployee_Id(employee.getId()).orElse(null);
        }
        if (account == null && employee.getEmail() != null) {
            account = userRepository.findByUsername(employee.getEmail())
                    .filter(found -> found.getEmployee() == null
                            || found.getEmployee().getId() == null
                            || found.getEmployee().getId().equals(employee.getId()))
                    .orElse(null);
        }
        if (account != null && !StaffEmploymentGuard.isRehirableStaff(account)) {
            throw new BusinessException(REHIRE_NOT_STAFF);
        }
        applyRehireProfile(employee, candidate, store);
        String passwordHash = passwordEncoder.encode(temporaryPassword);
        if (account == null) {
            account = userRepository.save(User.builder()
                    .username(employee.getEmail())
                    .passwordHash(passwordHash)
                    .role(requireStaffRole())
                    .employee(employee)
                    .isActive(true)
                    .build());
            employee.setUser(account);
        } else {
            account.setUsername(employee.getEmail());
            account.setPasswordHash(passwordHash);
            account.setRole(requireStaffRole());
            account.setEmployee(employee);
            account.setIsActive(true);
            account.setMustChangePassword(false);
            userRepository.save(account);
        }
        employeeRepository.save(employee);
        return account;
    }

    private void applyRehireProfile(Employee employee, RecruitmentCandidate candidate, Store store) {
        Store current = employee.getStore();
        if (current != null && current.getManager() != null && employee.getId().equals(current.getManager().getId())) {
            current.setManager(null);
            storeRepository.flush();
        }
        if (employee.getEmail() == null || !candidate.getEmail().equalsIgnoreCase(employee.getEmail())) {
            if (employeeRepository.existsByEmailIgnoreCase(candidate.getEmail())
                    || userRepository.existsByUsernameIgnoreCase(candidate.getEmail())) {
                throw new BusinessException(EMAIL_EXISTS + " Không thể tạo tài khoản.");
            }
            employee.setEmail(candidate.getEmail());
        }
        employee.setFullName(candidate.getFullName());
        employee.setPhone(candidate.getPhone());
        employee.setAddress(candidate.getAddress());
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setTerminationDate(null);
        employee.setStore(store);
    }

    private Employee resolveRehire(NormalizedCandidate candidate) {
        if (employeeRepository.existsByEmailIgnoreCaseAndStatusIn(candidate.email(), WORKING_STATUSES)) {
            throw new BusinessException(EMAIL_APPROVED_OR_WORKING + " (" + candidate.email() + ")");
        }
        Employee requested = null;
        if (candidate.rehireEmployeeId() != null) {
            requested = employeeRepository.findByIdWithStoreAccount(candidate.rehireEmployeeId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên để tuyển dụng lại."));
            if (requested.getStatus() != EmployeeStatus.TERMINATED) {
                throw new BusinessException(REHIRE_WORKING);
            }
            if (!StaffEmploymentGuard.isRehirableStaff(requested.getUser())) {
                throw new BusinessException(REHIRE_NOT_STAFF);
            }
        }
        Employee byEmail = employeeRepository.findByEmailIgnoreCaseWithAccount(candidate.email()).orElse(null);
        if (byEmail != null && byEmail.getStatus() != EmployeeStatus.TERMINATED) {
            throw new BusinessException(EMAIL_APPROVED_OR_WORKING + " (" + candidate.email() + ")");
        }
        if (requested != null && byEmail != null && !requested.getId().equals(byEmail.getId())) {
            throw new BusinessException(EMAIL_EXISTS + " (" + candidate.email() + ")");
        }
        Employee former = requested != null ? requested : byEmail;
        if (former != null) {
            if (!StaffEmploymentGuard.isRehirableStaff(former.getUser())) {
                throw new BusinessException(REHIRE_NOT_STAFF);
            }
            if (recruitmentCandidateRepository.existsByRehireEmployee_IdAndStatus(former.getId(), RecruitmentStatus.PENDING)) {
                throw new BusinessException(REHIRE_PENDING + " (" + displayName(former) + ")");
            }
            if (recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus(candidate.email(), RecruitmentStatus.PENDING)) {
                throw new BusinessException(EMAIL_PENDING + " (" + candidate.email() + ")");
            }
            return former;
        }
        if (recruitmentCandidateRepository.existsApprovedByEmailIgnoreCase(candidate.email())) {
            throw new BusinessException(EMAIL_APPROVED_OR_WORKING + " (" + candidate.email() + ")");
        }
        if (emailTaken(candidate.email())) {
            throw new BusinessException(EMAIL_EXISTS + " (" + candidate.email() + ")");
        }
        if (recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus(candidate.email(), RecruitmentStatus.PENDING)) {
            throw new BusinessException(EMAIL_PENDING + " (" + candidate.email() + ")");
        }
        return null;
    }

    private String displayName(Employee employee) {
        if (employee == null || employee.getFullName() == null || employee.getFullName().isBlank()) {
            return "nhân viên";
        }
        return employee.getFullName().trim();
    }

    private void assertUniqueInProposal(List<NormalizedCandidate> candidates) {
        Set<String> emails = new HashSet<>();
        Set<String> phones = new HashSet<>();
        for (NormalizedCandidate candidate : candidates) {
            if (!emails.add(candidate.email())) {
                throw new BusinessException("Email " + candidate.email() + " bị trùng trong danh sách nhân viên.");
            }
            if (!phones.add(candidate.phone())) {
                throw new BusinessException("Số điện thoại " + candidate.phone() + " bị trùng trong danh sách nhân viên.");
            }
        }
    }

    private String activeEmailMessage(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!employeeRepository.existsByEmailIgnoreCaseAndStatusIn(normalized, List.of(EmployeeStatus.ACTIVE))) {
            return null;
        }
        return EMAIL_ACTIVE_EMPLOYEE + " (" + normalized + ")";
    }

    private String activePhoneMessage(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s.\\-]", "");
        if (!normalized.matches("^[0-9]{9,15}$")) {
            return null;
        }
        if (employeeRepository.countByNormalizedPhoneAndStatus(normalized, EmployeeStatus.ACTIVE.getDbValue()) == 0) {
            return null;
        }
        return PHONE_ACTIVE_EMPLOYEE + " (" + normalized + ")";
    }

    private void assertPhoneAvailable(String phone, Employee rehire) {
        boolean sameFormerPhone = rehire != null && rehire.getPhone() != null
                && phone.equals(rehire.getPhone().replaceAll("[\\s.\\-]", ""));
        if (sameFormerPhone) {
            if (employeeRepository.countByNormalizedPhoneAndStatus(phone, EmployeeStatus.ACTIVE.getDbValue()) > 0
                    || employeeRepository.countByNormalizedPhoneAndStatus(phone, EmployeeStatus.ON_LEAVE.getDbValue()) > 0) {
                throw new BusinessException(PHONE_ACTIVE_EMPLOYEE + " (" + phone + ")");
            }
        } else if (employeeRepository.countByNormalizedPhone(phone) > 0) {
            throw new BusinessException(PHONE_EXISTS + " (" + phone + ")");
        }
        if (recruitmentCandidateRepository.existsByPhoneAndStatus(phone, RecruitmentStatus.PENDING)) {
            throw new BusinessException(PHONE_PENDING + " (" + phone + ")");
        }
    }

    private boolean emailTaken(String email) {
        return employeeRepository.existsByEmailIgnoreCase(email)
                || userRepository.existsByUsernameIgnoreCase(email);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("Vui lòng nhập email.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(EMAIL_INVALID);
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("Vui lòng nhập số điện thoại.");
        }
        String normalized = phone.replaceAll("[\\s.\\-]", "");
        if (!normalized.matches("^[0-9]{9,15}$")) {
            throw new BusinessException("Số điện thoại gồm 9 đến 15 chữ số.");
        }
        return normalized;
    }

    private String requireFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException("Vui lòng nhập họ và tên.");
        }
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 100) {
            throw new BusinessException("Họ và tên tối đa 100 ký tự.");
        }
        if (!PERSON_NAME_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(NAME_NOT_LETTERS);
        }
        return normalized;
    }

    private String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("Vui lòng nhập tên đề xuất.");
        }
        String normalized = title.trim();
        if (normalized.length() > 150) {
            throw new BusinessException("Tên đề xuất tối đa 150 ký tự.");
        }
        if (recruitmentRequestRepository.existsByTitleIgnoreCase(normalized)) {
            throw new BusinessException(TITLE_EXISTS);
        }
        return normalized;
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.length() > 500) {
            throw new BusinessException("Ghi chú tối đa 500 ký tự.");
        }
        return normalized;
    }

    private LocalDate requireDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            throw new BusinessException("Vui lòng chọn ngày sinh.");
        }
        LocalDate today = VietnamTime.today();
        if (!dateOfBirth.isBefore(today)) {
            throw new BusinessException("Ngày sinh phải là ngày trong quá khứ.");
        }
        if (dateOfBirth.isBefore(EARLIEST_BIRTH_DATE)) {
            throw new BusinessException("Ngày sinh không hợp lệ.");
        }
        return dateOfBirth;
    }

    private String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String normalized = address.trim();
        if (normalized.length() > 255) {
            throw new BusinessException("Địa chỉ tối đa 255 ký tự.");
        }
        return normalized;
    }

    private String normalizeRejectReason(String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            return null;
        }
        String normalized = rejectReason.trim();
        if (normalized.length() > 500) {
            throw new BusinessException("Lý do từ chối tối đa 500 ký tự.");
        }
        return normalized;
    }

    private void notifyManager(RecruitmentRequest request, String title, String message, NotificationType type) {
        Integer managerId = request.getRequestedBy() == null ? null : request.getRequestedBy().getId();
        if (managerId == null) {
            log.warn("Đề xuất {} không có Manager để nhận thông báo", request.getId());
            return;
        }
        userRepository.findByEmployee_Id(managerId).ifPresentOrElse(
                manager -> notificationService.notifyUsers(
                        List.of(manager), title, message, type, RECRUITMENT_REF, request.getId()),
                () -> log.warn("Không tìm thấy tài khoản Manager {} để gửi thông báo đề xuất {}", managerId, request.getId()));
    }

    private Store requireManagedStore(AuthenticatedUser actor) {
        requireManager(actor);
        return storeRepository.findByManager_Id(actor.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được gán làm quản lý cửa hàng."));
    }

    private void requireManager(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER || actor.getEmployeeId() == null) {
            throw new BusinessException("Chỉ Manager mới được thao tác đề xuất tuyển nhân sự của mình.");
        }
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException("Chỉ Admin mới được duyệt hoặc từ chối đề xuất tuyển nhân sự.");
        }
    }

    private Role requireStaffRole() {
        return roleRepository.findByRoleName(RoleName.STAFF)
                .orElseThrow(() -> new BusinessException("Role STAFF chưa được cấu hình trong hệ thống."));
    }

    private User requireUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản người dùng."));
    }

    private List<RecruitmentRequest> prepareList(List<RecruitmentRequest> requests) {
        Map<Integer, RecruitmentRequest> unique = new LinkedHashMap<>();
        for (RecruitmentRequest request : requests) {
            unique.putIfAbsent(request.getId(), request);
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(RecruitmentRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RecruitmentRequest::getId, Comparator.reverseOrder()))
                .toList();
    }

    private Comparator<RecruitmentCandidate> candidateOrder() {
        return Comparator.comparing(RecruitmentCandidate::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RecruitmentCandidate::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private RecruitmentRequestResponse toResponse(RecruitmentRequest request) {
        Store store = request.getStore();
        Employee manager = request.getRequestedBy();
        List<RecruitmentCandidate> people = request.getCandidates() == null
                ? List.of()
                : request.getCandidates().stream().sorted(candidateOrder()).toList();
        int pending = 0;
        int approved = 0;
        int rejected = 0;
        List<RecruitmentCandidateResponse> candidates = new ArrayList<>();
        for (RecruitmentCandidate candidate : people) {
            RecruitmentStatus status = candidate.getStatus() == null ? RecruitmentStatus.PENDING : candidate.getStatus();
            if (status == RecruitmentStatus.APPROVED) {
                approved++;
            } else if (status == RecruitmentStatus.REJECTED) {
                rejected++;
            } else {
                pending++;
            }
            Employee createdEmployee = candidate.getCreatedEmployee();
            candidates.add(RecruitmentCandidateResponse.builder()
                    .id(candidate.getId())
                    .fullName(candidate.getFullName())
                    .dateOfBirth(candidate.getDateOfBirth())
                    .gender(candidate.getGender())
                    .genderLabel(candidate.getGender() != null ? candidate.getGender().getLabel() : null)
                    .email(candidate.getEmail())
                    .phone(candidate.getPhone())
                    .address(candidate.getAddress())
                    .status(status)
                    .statusLabel(status.getLabel())
                    .rejectReason(candidate.getRejectReason())
                    .reviewedAt(candidate.getReviewedAt())
                    .createdEmployeeId(createdEmployee != null ? createdEmployee.getId() : null)
                    .rehire(candidate.getRehireEmployee() != null)
                    .kindLabel(kindLabel(candidate.getRehireEmployee() != null, status))
                    .build());
        }
        RecruitmentProposalStatus status = request.getStatus();
        return RecruitmentRequestResponse.builder()
                .id(request.getId())
                .title(request.getTitle())
                .note(request.getNote())
                .storeId(store != null ? store.getId() : null)
                .storeName(store != null ? store.getStoreName() : null)
                .managerId(manager != null ? manager.getId() : null)
                .managerName(manager != null ? manager.getFullName() : null)
                .status(status)
                .statusLabel(status != null ? status.getLabel() : null)
                .createdAt(request.getCreatedAt())
                .totalCandidates(candidates.size())
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .candidates(candidates)
                .build();
    }

    private String kindLabel(boolean rehire, RecruitmentStatus status) {
        if (!rehire) {
            return null;
        }
        return switch (status) {
            case PENDING -> "Ứng viên đang được đề xuất tuyển dụng lại";
            case APPROVED -> "Ứng viên được Admin duyệt tuyển dụng lại";
            case REJECTED -> "Ứng viên bị Admin từ chối tuyển dụng lại";
        };
    }

    private record NormalizedCandidate(
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String email,
            String phone,
            String address,
            Integer rehireEmployeeId,
            Employee rehireEmployee) {
    }

    private record LockedProposal(RecruitmentRequest request, List<RecruitmentCandidate> candidates) {
    }
}
