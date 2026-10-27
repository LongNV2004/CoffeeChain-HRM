package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.CitizenIds;
import com.example.coffee_hrm.common.security.TemporaryPasswordGenerator;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateStoreManagerRequest;
import com.example.coffee_hrm.dto.response.ManagerProvisionResult;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.RoleRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AccountMailService;
import com.example.coffee_hrm.service.StoreManagerAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
public class StoreManagerAccountServiceImpl implements StoreManagerAccountService {

    public static final String ACCESS_DENIED = "Access denied";
    public static final String STORE_NOT_FOUND = "Không tìm thấy cửa hàng.";
    public static final String STORE_NOT_ELIGIBLE = "Cửa hàng đang ngừng hoạt động nên không thể phân công Manager.";
    public static final String STORE_ALREADY_HAS_MANAGER = "Cửa hàng đã có Manager được phân công.";
    public static final String EMAIL_REQUIRED = "Vui lòng nhập email.";
    public static final String EMAIL_INVALID = "Email không đúng định dạng.";
    public static final String EMAIL_EXISTS = "Email đã được sử dụng.";
    public static final String PHONE_REQUIRED = "Vui lòng nhập số điện thoại.";
    public static final String PHONE_INVALID = "Số điện thoại gồm 9 đến 15 chữ số.";
    public static final String PHONE_EXISTS = "Số điện thoại đã được sử dụng.";
    public static final String NAME_REQUIRED = "Vui lòng nhập họ và tên.";
    public static final String ROLE_NOT_CONFIGURED = "Role MANAGER chưa được cấu hình trong hệ thống.";
    public static final String MANAGER_ACCOUNT_MISSING = "Cửa hàng chưa có Manager để gửi lại email.";
    public static final String ACCOUNT_NOT_READY = "Tài khoản Manager chưa sẵn sàng để gửi lại email.";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{9,15}$");

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final AccountMailService accountMailService;
    private final TransactionTemplate transactionTemplate;

    public StoreManagerAccountServiceImpl(StoreRepository storeRepository,
                                          EmployeeRepository employeeRepository,
                                          UserRepository userRepository,
                                          RoleRepository roleRepository,
                                          PasswordEncoder passwordEncoder,
                                          TemporaryPasswordGenerator temporaryPasswordGenerator,
                                          AccountMailService accountMailService,
                                          PlatformTransactionManager transactionManager) {
        this.storeRepository = storeRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.accountMailService = accountMailService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public ManagerProvisionResult createManager(AuthenticatedUser actor,
                                                Integer storeId,
                                                CreateStoreManagerRequest request) {
        requireAdmin(actor);
        IssuedCredentials issued = transactionTemplate.execute(status -> insertManager(storeId, request));
        if (issued == null) {
            throw new BusinessException("Không tạo được tài khoản Manager.");
        }
        boolean emailSent = deliver(issued);
        log.info("Admin {} đã tạo Manager {} cho cửa hàng {}. Email gửi: {}.",
                actor.getUserId(), issued.managerName(), issued.storeName(), emailSent);
        return ManagerProvisionResult.builder()
                .managerName(issued.managerName())
                .storeName(issued.storeName())
                .emailSent(emailSent)
                .resent(false)
                .build();
    }

    @Override
    public ManagerProvisionResult resendCredentials(AuthenticatedUser actor, Integer storeId) {
        requireAdmin(actor);
        IssuedCredentials issued = transactionTemplate.execute(status -> rotateCredentials(storeId));
        if (issued == null) {
            throw new BusinessException(ACCOUNT_NOT_READY);
        }
        boolean emailSent = deliver(issued);
        log.info("Admin {} gửi lại email tài khoản Manager {} của cửa hàng {}. Email gửi: {}.",
                actor.getUserId(), issued.managerName(), issued.storeName(), emailSent);
        return ManagerProvisionResult.builder()
                .managerName(issued.managerName())
                .storeName(issued.storeName())
                .emailSent(emailSent)
                .resent(true)
                .build();
    }

    private IssuedCredentials insertManager(Integer storeId, CreateStoreManagerRequest request) {
        String fullName = requireName(request == null ? null : request.getFullName());
        String email = requireEmail(request == null ? null : request.getEmail());
        String phone = requirePhone(request == null ? null : request.getPhone());
        String citizenId = CitizenIds.require(request == null ? null : request.getCitizenId());
        Store store = lockAssignableStore(storeId);
        if (store.getManager() != null) {
            throw new BusinessException(STORE_ALREADY_HAS_MANAGER);
        }
        if (userRepository.countOtherStoreMembersWithRole(store.getId(), -1, RoleName.MANAGER) > 0) {
            throw new BusinessException(STORE_ALREADY_HAS_MANAGER);
        }
        assertIdentityAvailable(email, phone, citizenId);

        String temporaryPassword = temporaryPasswordGenerator.generate();
        try {
            Employee employee = employeeRepository.saveAndFlush(Employee.builder()
                    .fullName(fullName)
                    .phone(phone)
                    .citizenId(citizenId)
                    .email(email)
                    .store(store)
                    .status(EmployeeStatus.ACTIVE)
                    .hireDate(VietnamTime.today())
                    .build());
            User account = userRepository.saveAndFlush(User.builder()
                    .username(email)
                    .passwordHash(passwordEncoder.encode(temporaryPassword))
                    .role(requireManagerRole())
                    .employee(employee)
                    .isActive(true)
                    .mustChangePassword(false)
                    .build());
            employee.setUser(account);
            store.setManager(employee);
            storeRepository.saveAndFlush(store);
            return new IssuedCredentials(employee.getFullName(), store.getStoreName(), email, temporaryPassword);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(conflictMessage(ex));
        }
    }

    private IssuedCredentials rotateCredentials(Integer storeId) {
        Store store = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
        Employee manager = store.getManager();
        if (manager == null) {
            throw new BusinessException(MANAGER_ACCOUNT_MISSING);
        }
        User account = userRepository.findByEmployee_Id(manager.getId())
                .orElseThrow(() -> new BusinessException(ACCOUNT_NOT_READY));
        if (account.getRole() == null || account.getRole().getRoleName() != RoleName.MANAGER) {
            throw new BusinessException(ACCOUNT_NOT_READY);
        }
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new BusinessException(ACCOUNT_NOT_READY);
        }
        String email = manager.getEmail();
        if (email == null || email.isBlank()) {
            throw new BusinessException(EMAIL_REQUIRED);
        }
        String temporaryPassword = temporaryPasswordGenerator.generate();
        account.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        account.setMustChangePassword(false);
        userRepository.saveAndFlush(account);
        return new IssuedCredentials(manager.getFullName(), store.getStoreName(), email.trim(), temporaryPassword);
    }

    /**
     * Gửi email sau khi transaction đã ghi tài khoản. Lỗi SMTP không xóa tài khoản.
     */
    private boolean deliver(IssuedCredentials issued) {
        try {
            accountMailService.sendManagerCredentials(
                    issued.email(),
                    issued.managerName(),
                    issued.storeName(),
                    issued.email(),
                    issued.temporaryPassword());
            return true;
        } catch (RuntimeException ex) {
            log.error("Đã lưu tài khoản Manager {} nhưng gửi email thất bại tới {}",
                    issued.managerName(), issued.email(), ex);
            return false;
        }
    }

    private Store lockAssignableStore(Integer storeId) {
        Store store = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
        StoreStatus status = store.getStatus();
        if (status == StoreStatus.INACTIVE || (status == null && Boolean.FALSE.equals(store.getIsActive()))) {
            throw new BusinessException(STORE_NOT_ELIGIBLE);
        }
        return store;
    }

    private void assertIdentityAvailable(String email, String phone, String citizenId) {
        if (employeeRepository.existsByEmailIgnoreCase(email) || userRepository.existsByUsernameIgnoreCase(email)) {
            throw new BusinessException(EMAIL_EXISTS);
        }
        if (employeeRepository.countByNormalizedPhone(phone) > 0) {
            throw new BusinessException(PHONE_EXISTS);
        }
        Employee owner = employeeRepository.findByCitizenId(citizenId).orElse(null);
        if (owner != null) {
            throw new BusinessException(owner.getStatus() == EmployeeStatus.TERMINATED
                    ? CitizenIds.BELONGS_TO_FORMER
                    : CitizenIds.DUPLICATE_MESSAGE);
        }
    }

    private String conflictMessage(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause() == null ? "" : ex.getMostSpecificCause().getMessage();
        String normalized = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (normalized.contains("managerid") || normalized.contains("ux_stores_managerid")) {
            return STORE_ALREADY_HAS_MANAGER;
        }
        if (normalized.contains("phone")) {
            return PHONE_EXISTS;
        }
        if (normalized.contains("citizenid")) {
            return CitizenIds.DUPLICATE_MESSAGE;
        }
        return EMAIL_EXISTS;
    }

    private Role requireManagerRole() {
        return roleRepository.findByRoleName(RoleName.MANAGER)
                .orElseThrow(() -> new BusinessException(ROLE_NOT_CONFIGURED));
    }

    private String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException(NAME_REQUIRED);
        }
        String trimmed = fullName.trim();
        if (trimmed.length() > 100) {
            throw new BusinessException("Họ và tên tối đa 100 ký tự.");
        }
        return trimmed;
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

    private String requirePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(PHONE_REQUIRED);
        }
        String normalized = phone.replaceAll("[\\s.\\-]", "");
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(PHONE_INVALID);
        }
        return normalized;
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException(ACCESS_DENIED);
        }
    }

    private record IssuedCredentials(String managerName, String storeName, String email, String temporaryPassword) {
    }
}
