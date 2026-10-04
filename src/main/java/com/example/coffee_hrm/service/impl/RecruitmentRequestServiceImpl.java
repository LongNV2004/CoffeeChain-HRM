package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.TemporaryPasswordGenerator;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateRecruitmentRequest;
import com.example.coffee_hrm.dto.response.RecruitmentManagerOption;
import com.example.coffee_hrm.dto.response.RecruitmentRequestResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.RecruitmentRequest;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
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
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentRequestServiceImpl implements RecruitmentRequestService {

    static final String EMAIL_EXISTS = "Email đã tồn tại trong hệ thống.";
    static final String EMAIL_PENDING = "Email này đang được dùng trong một đề xuất tuyển nhân sự đang chờ duyệt.";
    static final String EMAIL_INVALID = "Email không đúng định dạng.";
    private static final String RECRUITMENT_REF = "RECRUITMENT_REQUEST";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final RecruitmentRequestRepository recruitmentRequestRepository;
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
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        String fullName = requireFullName(request.getFullName());
        String address = normalizeAddress(request.getAddress());
        assertEmailAvailable(email);

        RecruitmentRequest saved;
        try {
            saved = recruitmentRequestRepository.save(RecruitmentRequest.builder()
                    .store(store)
                    .requestedBy(manager)
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .address(address)
                    .status(RecruitmentStatus.PENDING)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.error("Không lưu được đề xuất tuyển nhân sự vì email {} bị trùng", email, ex);
            throw new BusinessException(EMAIL_EXISTS);
        }

        String managerName = manager.getFullName() != null ? manager.getFullName() : actor.getDisplayName();
        notificationService.notifyUsers(
                userRepository.findActiveByRoleName(RoleName.ADMIN),
                "Đề xuất tuyển nhân sự mới",
                "Manager " + managerName + " đã gửi đề xuất tuyển nhân sự mới cho cửa hàng " + store.getStoreName() + ".",
                NotificationType.RECRUITMENT_REQUEST_CREATED,
                RECRUITMENT_REF,
                saved.getId());
        log.info("Manager {} đã tạo đề xuất tuyển nhân sự {}", manager.getId(), saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecruitmentRequestResponse> listMine(AuthenticatedUser actor) {
        requireManager(actor);
        return recruitmentRequestRepository.findMine(actor.getEmployeeId()).stream()
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
                                                          RecruitmentStatus status,
                                                          LocalDate createdFrom,
                                                          LocalDate createdTo) {
        requireAdmin(actor);
        if (createdFrom != null && createdTo != null && createdTo.isBefore(createdFrom)) {
            throw new BusinessException("Khoảng thời gian không hợp lệ.");
        }
        LocalDateTime from = createdFrom == null ? null : createdFrom.atStartOfDay();
        LocalDateTime toExclusive = createdTo == null ? null : createdTo.plusDays(1).atStartOfDay();
        return recruitmentRequestRepository.search(storeId, managerId, status, from, toExclusive).stream()
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
    public void approve(AuthenticatedUser actor, Integer id) {
        requireAdmin(actor);
        RecruitmentRequest request = requirePending(id);
        if (emailTaken(request.getEmail())) {
            throw new BusinessException(EMAIL_EXISTS + " Không thể tạo tài khoản.");
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        Employee employee;
        User account;
        try {
            employee = employeeRepository.save(Employee.builder()
                    .fullName(request.getFullName())
                    .phone(request.getPhone())
                    .email(request.getEmail())
                    .address(request.getAddress())
                    .store(request.getStore())
                    .status(EmployeeStatus.ACTIVE)
                    .certificationStatus(CertificationStatus.NOTCERTIFIED)
                    .hasCertificate(false)
                    .hireDate(VietnamTime.today())
                    .build());
            account = userRepository.save(User.builder()
                    .username(request.getEmail())
                    .passwordHash(passwordEncoder.encode(temporaryPassword))
                    .role(requireStaffRole())
                    .employee(employee)
                    .isActive(true)
                    .build());
            request.setStatus(RecruitmentStatus.APPROVED);
            request.setReviewedAt(VietnamTime.now());
            request.setReviewedBy(requireUser(actor.getUserId()));
            request.setCreatedEmployee(employee);
            request.setRejectReason(null);
            recruitmentRequestRepository.save(request);
        } catch (DataIntegrityViolationException ex) {
            log.error("Không tạo được tài khoản cho đề xuất {} vì email bị trùng", id, ex);
            throw new BusinessException(EMAIL_EXISTS + " Không thể tạo tài khoản.");
        }

        notifyManager(
                request,
                "Đề xuất tuyển nhân sự đã được duyệt",
                "Đề xuất tuyển nhân sự " + request.getFullName() + " đã được Admin duyệt.",
                NotificationType.RECRUITMENT_REQUEST_APPROVED);

        // Gửi email trong cùng transaction: nếu SMTP lỗi thì rollback, không tạo tài khoản mà không giao được mật khẩu.
        accountMailService.sendTemporaryPassword(
                request.getEmail(),
                request.getFullName(),
                account.getUsername(),
                temporaryPassword);
        log.info("Admin {} đã duyệt đề xuất {} và tạo nhân viên {}", actor.getUserId(), id, employee.getId());
    }

    @Override
    @Transactional
    public void reject(AuthenticatedUser actor, Integer id, String rejectReason) {
        requireAdmin(actor);
        RecruitmentRequest request = requirePending(id);
        String reason = normalizeRejectReason(rejectReason);
        request.setStatus(RecruitmentStatus.REJECTED);
        request.setRejectReason(reason);
        request.setReviewedAt(VietnamTime.now());
        request.setReviewedBy(requireUser(actor.getUserId()));
        recruitmentRequestRepository.save(request);
        notifyManager(
                request,
                "Đề xuất tuyển nhân sự bị từ chối",
                "Đề xuất tuyển nhân sự " + request.getFullName() + " đã bị từ chối.",
                NotificationType.RECRUITMENT_REQUEST_REJECTED);
        log.info("Admin {} đã từ chối đề xuất {}", actor.getUserId(), id);
    }

    @Override
    @Transactional(readOnly = true)
    public int countPendingRequests() {
        return recruitmentRequestRepository.countByStatus(RecruitmentStatus.PENDING);
    }

    private RecruitmentRequest requirePending(Integer id) {
        RecruitmentRequest request = requireRequest(id);
        if (request.getStatus() != RecruitmentStatus.PENDING) {
            throw new BusinessException("Chỉ có thể xử lý đề xuất đang chờ duyệt.");
        }
        return request;
    }

    private RecruitmentRequest requireRequest(Integer id) {
        return recruitmentRequestRepository.findDetailById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đề xuất tuyển nhân sự."));
    }

    private void assertEmailAvailable(String email) {
        if (emailTaken(email)) {
            throw new BusinessException(EMAIL_EXISTS);
        }
        if (recruitmentRequestRepository.existsByEmailIgnoreCaseAndStatus(email, RecruitmentStatus.PENDING)) {
            throw new BusinessException(EMAIL_PENDING);
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
        String normalized = email.trim().toLowerCase();
        if (normalized.length() > 100 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(EMAIL_INVALID);
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("Vui lòng nhập số điện thoại.");
        }
        String normalized = phone.replaceAll("[\\s-]", "");
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

    private RecruitmentRequestResponse toResponse(RecruitmentRequest request) {
        Store store = request.getStore();
        Employee manager = request.getRequestedBy();
        Employee createdEmployee = request.getCreatedEmployee();
        RecruitmentStatus status = request.getStatus();
        return RecruitmentRequestResponse.builder()
                .id(request.getId())
                .storeId(store != null ? store.getId() : null)
                .storeName(store != null ? store.getStoreName() : null)
                .managerId(manager != null ? manager.getId() : null)
                .managerName(manager != null ? manager.getFullName() : null)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .status(status)
                .statusLabel(status != null ? status.getLabel() : null)
                .rejectReason(request.getRejectReason())
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .createdEmployeeId(createdEmployee != null ? createdEmployee.getId() : null)
                .build();
    }
}
