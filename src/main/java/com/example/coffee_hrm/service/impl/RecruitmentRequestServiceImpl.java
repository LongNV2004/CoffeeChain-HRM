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
    static final String EMAIL_PENDING = "Email này đang được dùng trong một đề xuất tuyển nhân sự đang chờ duyệt.";
    static final String EMAIL_INVALID = "Email không đúng định dạng.";
    static final String PHONE_EXISTS = "Số điện thoại đã tồn tại trong hệ thống.";
    static final String PHONE_PENDING = "Số điện thoại này đang được dùng trong một đề xuất tuyển nhân sự đang chờ duyệt.";
    static final String ALREADY_APPROVED = "Nhân viên này đã được duyệt. Không tạo thêm tài khoản.";
    private static final int MAX_CANDIDATES = 30;
    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    private static final String RECRUITMENT_REF = "RECRUITMENT_REQUEST";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

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
    public String approveCandidate(AuthenticatedUser actor, Integer requestId, Integer candidateId) {
        requireAdmin(actor);
        LockedProposal locked = lockProposal(requestId);
        RecruitmentCandidate candidate = requireCandidate(locked.candidates(), candidateId);
        requirePendingCandidate(candidate, true);
        if (emailTaken(candidate.getEmail())) {
            throw new BusinessException(EMAIL_EXISTS + " Không thể tạo tài khoản.");
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        Employee employee;
        User account;
        try {
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
            candidate.setStatus(RecruitmentStatus.APPROVED);
            candidate.setReviewedAt(VietnamTime.now());
            candidate.setReviewedBy(requireUser(actor.getUserId()));
            candidate.setCreatedEmployee(employee);
            candidate.setRejectReason(null);
            refreshProposalStatus(locked.request(), locked.candidates());
            recruitmentRequestRepository.save(locked.request());
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
                account.getUsername(),
                temporaryPassword);
        log.info("Admin {} đã duyệt nhân viên {} trong đề xuất {} và tạo nhân viên {}",
                actor.getUserId(), candidateId, requestId, employee.getId());
        return candidate.getFullName();
    }

    @Override
    @Transactional
    public String rejectCandidate(AuthenticatedUser actor, Integer requestId, Integer candidateId, String rejectReason) {
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
        notifyManager(
                locked.request(),
                "Đề xuất tuyển nhân sự bị từ chối",
                "Nhân viên " + candidate.getFullName() + " trong đề xuất \"" + locked.request().getTitle()
                        + "\" đã bị từ chối.",
                NotificationType.RECRUITMENT_REQUEST_REJECTED);
        log.info("Admin {} đã từ chối nhân viên {} trong đề xuất {}", actor.getUserId(), candidateId, requestId);
        return candidate.getFullName();
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
                    normalizeAddress(candidate.getAddress())));
        }
        assertUniqueInProposal(normalized);
        for (NormalizedCandidate candidate : normalized) {
            assertEmailAvailable(candidate.email());
            assertPhoneAvailable(candidate.phone());
        }
        return normalized;
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

    private void assertEmailAvailable(String email) {
        if (emailTaken(email)) {
            throw new BusinessException(EMAIL_EXISTS + " (" + email + ")");
        }
        if (recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus(email, RecruitmentStatus.PENDING)) {
            throw new BusinessException(EMAIL_PENDING + " (" + email + ")");
        }
    }

    private void assertPhoneAvailable(String phone) {
        if (employeeRepository.countByNormalizedPhone(phone) > 0) {
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
        String normalized = fullName.trim();
        if (normalized.length() > 100) {
            throw new BusinessException("Họ và tên tối đa 100 ký tự.");
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

    private record NormalizedCandidate(
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String email,
            String phone,
            String address) {
    }

    private record LockedProposal(RecruitmentRequest request, List<RecruitmentCandidate> candidates) {
    }
}
