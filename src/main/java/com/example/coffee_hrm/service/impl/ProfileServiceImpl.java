package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.CitizenIds;
import com.example.coffee_hrm.dto.request.ChangePasswordRequest;
import com.example.coffee_hrm.dto.request.UpdateProfileRequest;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.MyProfileView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.RecruitmentCandidate;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.RecruitmentCandidateRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.CoffeeRememberMeServices;
import com.example.coffee_hrm.service.ProfileService;
import com.example.coffee_hrm.service.TrainingService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final String CURRENT_PASSWORD_REJECTED = "Mật khẩu hiện tại không chính xác.";
    private static final String PASSWORD_MISMATCH = "Mật khẩu mới và xác nhận mật khẩu không trùng nhau.";
    private static final String PASSWORD_LENGTH = "Mật khẩu mới phải từ 6 đến 100 ký tự.";
    private static final String PASSWORD_INCOMPLETE =
            "Vui lòng nhập đủ mật khẩu hiện tại, mật khẩu mới và xác nhận mật khẩu.";
    private static final String PROFILE_MISSING = "Không tìm thấy hồ sơ nhân viên của tài khoản này.";
    private static final String PASSWORD_UNAVAILABLE = "Không thể đổi mật khẩu. Vui lòng đăng nhập lại.";
    private static final String PROFILE_DENIED = "Bạn không có quyền chỉnh sửa hồ sơ này.";
    private static final String NAME_REQUIRED = "Vui lòng nhập họ và tên.";
    private static final String NAME_TOO_LONG = "Họ và tên tối đa 100 ký tự.";
    private static final String NAME_NOT_LETTERS = "Họ và tên chỉ được chứa chữ cái, không được nhập số.";
    private static final String PHONE_REQUIRED = "Vui lòng nhập số điện thoại.";
    private static final String PHONE_INVALID = "Số điện thoại gồm 9 đến 15 chữ số.";
    private static final String PHONE_TAKEN = "Số điện thoại đã được sử dụng.";
    private static final String PHONE_PENDING = "Số điện thoại đang được dùng trong một đề xuất tuyển dụng.";
    private static final String EMAIL_REQUIRED = "Vui lòng nhập email.";
    private static final String EMAIL_INVALID = "Email không hợp lệ.";
    private static final String EMAIL_TAKEN = "Email đã được sử dụng.";
    private static final String EMAIL_PENDING = "Email đang được dùng trong một đề xuất tuyển dụng.";
    private static final String ADDRESS_TOO_LONG = "Địa chỉ tối đa 255 ký tự.";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final DateTimeFormatter CERTIFICATE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("^[\\p{L}]+(?:\\s+[\\p{L}]+)*$");

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final RecruitmentCandidateRepository recruitmentCandidateRepository;
    private final TrainingService trainingService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final CoffeeRememberMeServices rememberMeServices;

    @Override
    @Transactional(readOnly = true)
    public MyProfileView getProfile(AuthenticatedUser actor) {
        User user = requireOwnAccount(actor, PROFILE_MISSING);
        Employee employee = user.getEmployee();
        if (employee == null) {
            throw new BusinessException(PROFILE_MISSING);
        }
        Store store = employee.getStore();
        PersonalRecord personal = personalRecord(employee.getId());
        RoleName roleName = user.getRole().getRoleName();
        List<EmployeeSkillStatusLine> skills = certificatesFor(roleName, employee);
        boolean accountActive = Boolean.TRUE.equals(user.getIsActive());
        return MyProfileView.builder()
                .employeeId(employee.getId())
                .fullName(displayName(employee, user))
                .initials(initials(displayName(employee, user)))
                .username(user.getUsername())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .citizenIdMasked(CitizenIds.mask(employee.getCitizenId()))
                .address(employee.getAddress())
                .avatarUrl(employee.getAvatarUrl())
                .roleName(roleName)
                .roleLabel(roleLabel(roleName))
                .sidebarRole(sidebarRole(roleName))
                .storeName(store != null ? store.getStoreName() : null)
                .employeeStatus(employee.getStatus())
                .employeeStatusLabel(employeeStatusLabel(employee.getStatus()))
                .accountActive(accountActive)
                .accountStatusLabel(accountActive ? "Đang hoạt động" : "Bị khóa")
                .hireDate(employee.getHireDate())
                .terminationDate(employee.getTerminationDate())
                .dateOfBirth(personal.dateOfBirth())
                .genderLabel(personal.genderLabel())
                .accountCreatedAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .skillCertificates(skills == null ? List.of() : List.copyOf(skills))
                .build();
    }

    @Override
    @Transactional
    public void updateProfile(AuthenticatedUser actor, UpdateProfileRequest request) {
        if (actor == null || (actor.getRoleName() != RoleName.MANAGER && actor.getRoleName() != RoleName.STAFF)) {
            throw new BusinessException(PROFILE_DENIED);
        }
        User user = requireOwnAccount(actor, PROFILE_MISSING);
        if (user.getRole() == null
                || (user.getRole().getRoleName() != RoleName.MANAGER && user.getRole().getRoleName() != RoleName.STAFF)) {
            throw new BusinessException(PROFILE_DENIED);
        }
        Employee employee = user.getEmployee();
        if (employee == null || employee.getId() == null) {
            throw new BusinessException(PROFILE_MISSING);
        }

        String fullName = requireFullName(request == null ? null : request.getFullName());
        String phone = requirePhone(request == null ? null : request.getPhone());
        String email = requireEmail(request == null ? null : request.getEmail());
        String address = optionalAddress(request == null ? null : request.getAddress());

        String currentPhone = compactPhone(employee.getPhone());
        if (!phone.equals(currentPhone)) {
            if (employeeRepository.countOtherEmployeesByNormalizedPhone(phone, employee.getId()) > 0) {
                throw new BusinessException(PHONE_TAKEN);
            }
            if (recruitmentCandidateRepository.existsByPhoneAndStatus(phone, RecruitmentStatus.PENDING)) {
                throw new BusinessException(PHONE_PENDING);
            }
        }
        String currentEmail = employee.getEmail() == null ? "" : employee.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(currentEmail)) {
            if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, employee.getId())) {
                throw new BusinessException(EMAIL_TAKEN);
            }
            if (recruitmentCandidateRepository.existsByEmailIgnoreCaseAndStatus(email, RecruitmentStatus.PENDING)) {
                throw new BusinessException(EMAIL_PENDING);
            }
        }

        employee.setFullName(fullName);
        employee.setPhone(phone);
        employee.setEmail(email);
        employee.setAddress(address);
        try {
            employeeRepository.save(employee);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(EMAIL_TAKEN);
        }
    }

    @Override
    @Transactional
    public void changePassword(AuthenticatedUser actor,
                               ChangePasswordRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        String currentPassword = request != null ? request.getCurrentPassword() : null;
        String newPassword = request != null ? request.getNewPassword() : null;
        String confirmPassword = request != null ? request.getConfirmPassword() : null;
        if (request != null) {
            request.setCurrentPassword(null);
            request.setNewPassword(null);
            request.setConfirmPassword(null);
        }
        validateNewPassword(currentPassword, newPassword, confirmPassword);

        User user = requireOwnAccount(actor, PASSWORD_UNAVAILABLE);
        if (!matchesPassword(currentPassword, user)) {
            throw new BusinessException(CURRENT_PASSWORD_REJECTED);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        AuthenticatedUser principal = AuthenticatedUser.from(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute("CURRENT_USER_ID", principal.getUserId());
        session.setAttribute("CURRENT_ROLE", principal.getRoleName().name());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        refreshRememberMe(httpRequest, httpResponse, authentication);
    }

    private String requireFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException(NAME_REQUIRED);
        }
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 100) {
            throw new BusinessException(NAME_TOO_LONG);
        }
        if (!PERSON_NAME_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(NAME_NOT_LETTERS);
        }
        return normalized;
    }

    private String requirePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(PHONE_REQUIRED);
        }
        String normalized = compactPhone(phone);
        if (normalized == null || !normalized.matches("^[0-9]{9,15}$")) {
            throw new BusinessException(PHONE_INVALID);
        }
        return normalized;
    }

    private String compactPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s.\\-]", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(EMAIL_REQUIRED);
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(EMAIL_INVALID);
        }
        return normalized;
    }

    private String optionalAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String normalized = address.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 255) {
            throw new BusinessException(ADDRESS_TOO_LONG);
        }
        return normalized;
    }

    private void validateNewPassword(String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()
                || confirmPassword == null || confirmPassword.isBlank()) {
            throw new BusinessException(PASSWORD_INCOMPLETE);
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH || newPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(PASSWORD_LENGTH);
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(PASSWORD_MISMATCH);
        }
    }

    private User requireOwnAccount(AuthenticatedUser actor, String missingMessage) {
        if (actor == null || actor.getUserId() == null) {
            throw new BusinessException(missingMessage);
        }
        return userRepository.findByIdWithRole(actor.getUserId())
                .orElseThrow(() -> new BusinessException(missingMessage));
    }

    private PersonalRecord personalRecord(Integer employeeId) {
        List<RecruitmentCandidate> records = recruitmentCandidateRepository.findByLinkedEmployeeId(employeeId);
        LocalDate dateOfBirth = null;
        String genderLabel = null;
        if (records != null) {
            for (RecruitmentCandidate candidate : records) {
                if (candidate == null) {
                    continue;
                }
                if (dateOfBirth == null) {
                    dateOfBirth = candidate.getDateOfBirth();
                }
                if (genderLabel == null && candidate.getGender() != null) {
                    genderLabel = candidate.getGender().getLabel();
                }
            }
        }
        return new PersonalRecord(dateOfBirth, genderLabel);
    }

    private void refreshRememberMe(HttpServletRequest request,
                                   HttpServletResponse response,
                                   UsernamePasswordAuthenticationToken authentication) {
        if (!hasRememberMeCookie(request)) {
            return;
        }
        try {
            request.setAttribute(CoffeeRememberMeServices.ISSUE_COOKIE_ATTRIBUTE, Boolean.TRUE);
            rememberMeServices.loginSuccess(request, response, authentication);
        } catch (RuntimeException ex) {
            log.warn("Không làm mới được cookie ghi nhớ đăng nhập sau khi đổi mật khẩu.");
        }
    }

    private boolean hasRememberMeCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if ("coffee-hrm-remember-me".equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPassword(String rawPassword, User user) {
        if (matchesStored(rawPassword, user)) {
            return true;
        }
        if (rawPassword == null) {
            return false;
        }
        String trimmed = rawPassword.trim();
        return !trimmed.equals(rawPassword) && !trimmed.isEmpty() && matchesStored(trimmed, user);
    }

    private boolean matchesStored(String rawPassword, User user) {
        String stored = user.getPasswordHash();
        if (rawPassword == null || rawPassword.isEmpty() || stored == null || stored.isBlank()) {
            return false;
        }
        if (isBcryptHash(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        return stored.equals(rawPassword);
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private String displayName(Employee employee, User user) {
        if (employee.getFullName() != null && !employee.getFullName().isBlank()) {
            return employee.getFullName().trim();
        }
        return user.getUsername();
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) {
            return "•";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private List<EmployeeSkillStatusLine> certificatesFor(RoleName roleName, Employee employee) {
        if (roleName == RoleName.MANAGER) {
            boolean certified = Boolean.TRUE.equals(employee.getManagementCertified());
            String issuedOn = employee.getManagementCertifiedAt() == null
                    ? null
                    : employee.getManagementCertifiedAt().format(CERTIFICATE_DATE);
            return List.of(EmployeeSkillStatusLine.builder()
                    .skillName("Chứng chỉ quản lý")
                    .certificationStatus(certified
                            ? CertificationStatus.CERTIFIED
                            : CertificationStatus.NOTCERTIFIED)
                    .certificationLabel(certified ? "Đã có chứng chỉ" : "Chưa có chứng chỉ")
                    .certifiedDateLabel(issuedOn)
                    .build());
        }
        List<EmployeeSkillStatusLine> skills = trainingService.listCurrentSkillStatuses(employee.getId());
        return skills == null ? List.of() : List.copyOf(skills);
    }

    private String roleLabel(RoleName roleName) {
        if (roleName == RoleName.MANAGER) {
            return "Quản lý cửa hàng";
        }
        if (roleName == RoleName.STAFF) {
            return "Nhân viên";
        }
        return roleName != null ? roleName.getDbValue() : "Tài khoản";
    }

    private String sidebarRole(RoleName roleName) {
        return roleName == RoleName.MANAGER ? "manager" : "staff";
    }

    private String employeeStatusLabel(EmployeeStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case ACTIVE -> "Đang làm việc";
            case ON_LEAVE -> "Đang nghỉ phép";
            case TERMINATED -> "Đã nghỉ việc";
        };
    }

    private record PersonalRecord(LocalDate dateOfBirth, String genderLabel) {
    }
}
