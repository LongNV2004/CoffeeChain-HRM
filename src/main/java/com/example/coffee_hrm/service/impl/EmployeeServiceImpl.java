package com.example.coffee_hrm.service.impl;
import com.example.coffee_hrm.dto.request.CreateEmployeeRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.RoleRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.coffee_hrm.dto.request.UpdateEmployeeRequest;
import java.time.LocalDate;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    static final String ACCESS_DENIED = "Access denied";
    static final String EMPLOYEE_NOT_FOUND = "Employee not found";
    static final String STORE_NOT_FOUND = "Store not found";
    static final String EMPLOYEE_NOT_IN_STORE = "Nhân viên không thuộc cửa hàng này";
    static final String INVALID_ROLE = "Role không hợp lệ. Chỉ chấp nhận MANAGER hoặc STAFF";
    static final String STORE_ALREADY_HAS_MANAGER =
            "Cửa hàng đã có Manager. Hãy chuyển Manager hiện tại về STAFF trước khi chỉ định Manager mới";
    static final String ACCOUNT_REQUIRED = "Nhân viên chưa có tài khoản đăng nhập nên không thể phân quyền";
    static final String ADMIN_ROLE_LOCKED = "Không thể thay đổi role của tài khoản Admin";
    static final String ROLE_UNCHANGED = "Nhân viên đã có role %s";
    static final String TERMINATED_CANNOT_BE_MANAGER = "Nhân viên đã nghỉ việc không thể được chỉ định làm Manager";
    static final String INACTIVE_ACCOUNT_CANNOT_BE_MANAGER =
            "Tài khoản của nhân viên đang bị khóa nên không thể chỉ định làm Manager";
    static final String ROLE_NOT_CONFIGURED = "Role %s chưa được cấu hình trong hệ thống";
    private final PasswordEncoder passwordEncoder;
    private static final Set<RoleName> ASSIGNABLE_ROLES = EnumSet.of(RoleName.MANAGER, RoleName.STAFF);

    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public List<EmployeeResponse> getStoreEmployees(AuthenticatedUser actor, Integer storeId) {
        requireAdmin(actor);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
        return employeeRepository.findByStoreIdWithAccount(storeId).stream()
                .map(employee -> toResponse(employee, employee.getUser(), store))
                .toList();
    }

    @Override
    @Transactional

    public EmployeeResponse createEmployee(
            AuthenticatedUser actor,
            CreateEmployeeRequest request
    ) {
        requireAdmin(actor);

        RoleName role = parseAssignableRole(request.getRole());

        if (role == RoleName.MANAGER && !request.isCreateAccount()) {
            throw new BusinessException(
                    "Nhân viên cần có tài khoản để được chỉ định làm Manager"
            );
        }

        String email = request.getEmail() == null
                ? "" : request.getEmail().trim();

        if (email.isBlank()) {
            throw new BusinessException("Email không được để trống");
        }

        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email đã được sử dụng");
        }

        String username = null;

        if (request.isCreateAccount()) {
            username = request.getUsername() == null
                    ? "" : request.getUsername().trim();

            if (username.isBlank()
                    || request.getTemporaryPassword() == null
                    || request.getTemporaryPassword().isBlank()) {
                throw new BusinessException(
                        "Vui lòng nhập tên đăng nhập và mật khẩu tạm thời"
                );
            }

            if (userRepository.findByUsername(username).isPresent()) {
                throw new BusinessException(
                        "Tên đăng nhập đã tồn tại"
                );
            }
        }

        Store store = role == RoleName.MANAGER
                ? storeRepository.findByIdForUpdate(request.getStoreId())
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND))
                : storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));

        Employee employee = Employee.builder()
                .fullName(request.getFullName().trim())
                .phone(request.getPhone().trim())
                .email(email)
                .address(request.getAddress())
                .avatarUrl(request.getAvatarUrl())
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .hireDate(request.getHireDate())
                .build();

        employeeRepository.save(employee);

        User account = null;

        if (request.isCreateAccount()) {
            account = User.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(
                            request.getTemporaryPassword()
                    ))
                    .employee(employee)
                    .role(requireRole(RoleName.STAFF))
                    .build();

            userRepository.save(account);
            employee.setUser(account);

            if (role == RoleName.MANAGER) {
                promoteToManager(employee, account, store);
            }
        }

        return toResponse(employee, account, store);
    }
    @Override
    @Transactional
    public EmployeeResponse updateEmployee(
            AuthenticatedUser actor,
            Integer storeId,
            Integer employeeId,
            UpdateEmployeeRequest request
    ) {
        requireAdmin(actor);

        Employee employee = employeeRepository
                .findByIdWithStoreAndManager(employeeId)
                .orElseThrow(() -> new BusinessException(EMPLOYEE_NOT_FOUND));

        if (!storeId.equals(employee.getStore().getId())) {
            throw new BusinessException(EMPLOYEE_NOT_IN_STORE);
        }

        RoleName targetRole = parseAssignableRole(request.getRole());
        User account = userRepository.findByEmployee_Id(employeeId).orElse(null);

        if (account != null
                && account.getRole().getRoleName() == RoleName.ADMIN) {
            throw new BusinessException(ADMIN_ROLE_LOCKED);
        }

        if (targetRole == RoleName.MANAGER && account == null) {
            throw new BusinessException(ACCOUNT_REQUIRED);
        }

        if (targetRole == RoleName.MANAGER
                && request.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException(TERMINATED_CANNOT_BE_MANAGER);
        }

        List<Store> managedStores =
                storeRepository.findAllByManager_Id(employeeId);

        boolean currentlyManager = !managedStores.isEmpty()
                || (account != null
                && account.getRole().getRoleName() == RoleName.MANAGER);

        if (targetRole == RoleName.MANAGER
                && currentlyManager
                && !storeId.equals(request.getStoreId())) {
            throw new BusinessException(
                    "Hãy chuyển Manager về Staff trước khi chuyển cửa hàng"
            );
        }

        if (request.getStatus() == EmployeeStatus.TERMINATED) {
            LocalDate terminationDate = request.getTerminationDate();

            if (terminationDate == null
                    || terminationDate.isBefore(request.getHireDate())) {
                throw new BusinessException(
                        "Ngày nghỉ việc phải có và không được trước ngày vào làm"
                );
            }
        }

        String email = request.getEmail().trim();

        if ((employee.getEmail() == null
                || !employee.getEmail().equalsIgnoreCase(email))
                && employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email đã được sử dụng");
        }

        if (targetRole == RoleName.STAFF && currentlyManager) {
            if (account == null) {
                throw new BusinessException(ACCOUNT_REQUIRED);
            }
            demoteToStaff(employee, account);
        }

        Store targetStore = storeRepository
                .findByIdForUpdate(request.getStoreId())
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));

        employee.setFullName(request.getFullName().trim());
        employee.setPhone(request.getPhone().trim());
        employee.setEmail(email);
        employee.setAddress(request.getAddress());
        employee.setAvatarUrl(request.getAvatarUrl());
        employee.setStore(targetStore);
        employee.setHireDate(request.getHireDate());
        employee.setStatus(request.getStatus());
        employee.setTerminationDate(
                request.getStatus() == EmployeeStatus.TERMINATED
                        ? request.getTerminationDate() : null
        );

        if (targetRole == RoleName.MANAGER
                && !targetStore.getId().equals(
                managedStores.isEmpty()
                        ? null : managedStores.get(0).getId()
        )) {
            promoteToManager(employee, account, targetStore);
        }

        return toResponse(employee, account, targetStore);
    }
    @Override
    @Transactional
    public EmployeeResponse changeEmployeeRole(AuthenticatedUser actor, Integer storeId, Integer employeeId, String role) {
        requireAdmin(actor);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(EMPLOYEE_NOT_FOUND));
        // Khóa dòng Store để hai Admin không thể đồng thời chỉ định hai Manager cho cùng một cửa hàng.
        Store store = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
        if (employee.getStore() == null || !store.getId().equals(employee.getStore().getId())) {
            throw new BusinessException(EMPLOYEE_NOT_IN_STORE);
        }
        RoleName targetRole = parseAssignableRole(role);

        User user = userRepository.findByEmployee_Id(employeeId)
                .orElseThrow(() -> new BusinessException(ACCOUNT_REQUIRED));
        if (!ASSIGNABLE_ROLES.contains(user.getRole().getRoleName())) {
            throw new BusinessException(ADMIN_ROLE_LOCKED);
        }

        if (targetRole == RoleName.MANAGER) {
            promoteToManager(employee, user, store);
        } else {
            demoteToStaff(employee, user);
        }
        return toResponse(employee, user, store);
    }

    private void promoteToManager(Employee employee, User user, Store store) {
        Employee currentManager = store.getManager();
        boolean linkedToStore = currentManager != null && currentManager.getId().equals(employee.getId());
        if (user.getRole().getRoleName() == RoleName.MANAGER && linkedToStore) {
            throw new BusinessException(ROLE_UNCHANGED.formatted(RoleName.MANAGER.name()));
        }
        boolean storeHasOtherManager = (currentManager != null && !linkedToStore)
                || userRepository.countOtherStoreMembersWithRole(store.getId(), employee.getId(), RoleName.MANAGER) > 0;
        if (storeHasOtherManager) {
            throw new BusinessException(STORE_ALREADY_HAS_MANAGER);
        }
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException(TERMINATED_CANNOT_BE_MANAGER);
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(INACTIVE_ACCOUNT_CANNOT_BE_MANAGER);
        }

        // Manager chỉ được gắn với cửa hàng của chính mình: gỡ mọi liên kết Manager ở cửa hàng khác.
        List<Store> otherManagedStores = storeRepository.findAllByManager_Id(employee.getId()).stream()
                .filter(managed -> !managed.getId().equals(store.getId()))
                .toList();
        if (!otherManagedStores.isEmpty()) {
            otherManagedStores.forEach(managed -> managed.setManager(null));
            // Phải ghi trước để không vi phạm UX_Stores_ManagerId khi gán cho cửa hàng hiện tại.
            storeRepository.flush();
        }

        user.setRole(requireRole(RoleName.MANAGER));
        store.setManager(employee);
    }

    private void demoteToStaff(Employee employee, User user) {
        List<Store> managedStores = storeRepository.findAllByManager_Id(employee.getId());
        if (user.getRole().getRoleName() == RoleName.STAFF && managedStores.isEmpty()) {
            throw new BusinessException(ROLE_UNCHANGED.formatted(RoleName.STAFF.name()));
        }
        managedStores.forEach(managed -> managed.setManager(null));
        user.setRole(requireRole(RoleName.STAFF));
    }

    private RoleName parseAssignableRole(String role) {
        if (role != null && !role.isBlank()) {
            try {
                RoleName parsed = RoleName.valueOf(role.trim().toUpperCase(Locale.ROOT));
                if (ASSIGNABLE_ROLES.contains(parsed)) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // Rơi xuống thông báo role không hợp lệ bên dưới.
            }
        }
        throw new BusinessException(INVALID_ROLE);
    }

    private Role requireRole(RoleName roleName) {
        return roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new BusinessException(ROLE_NOT_CONFIGURED.formatted(roleName.name())));
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException(ACCESS_DENIED);
        }
    }

    private EmployeeResponse toResponse(Employee employee, User user, Store store) {
        Employee manager = store.getManager();
        return EmployeeResponse.builder()
                .id(employee.getId())
                .fullName(employee.getFullName())
                .phone(employee.getPhone())
                .email(employee.getEmail())
                .address(employee.getAddress())
                .avatarUrl(employee.getAvatarUrl())
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .status(employee.getStatus())
                .hireDate(employee.getHireDate())
                .terminationDate(employee.getTerminationDate())
                .createdAt(employee.getCreatedAt())
                .userId(user != null ? user.getId() : null)
                .roleName(user != null ? user.getRole().getRoleName() : null)
                .hasCertificate(Boolean.TRUE.equals(employee.getHasCertificate()))
                .storeManager(manager != null && manager.getId().equals(employee.getId()))
                .build();
    }
}
