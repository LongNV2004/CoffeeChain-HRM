package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmploymentContractService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmploymentContractServiceImpl implements EmploymentContractService {

    private static final int CODE_MAX_LENGTH = 50;
    private static final int SALARY_INTEGER_DIGITS = 16;
    private static final int SALARY_SCALE = 2;

    private final EmploymentContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final Integer expiringWithinDays;

    public EmploymentContractServiceImpl(EmploymentContractRepository contractRepository,
                                         EmployeeRepository employeeRepository,
                                         StoreRepository storeRepository,
                                         @Value("${coffee-hrm.contract.expiring-within-days:}") String expiringWithinDays) {
        this.contractRepository = contractRepository;
        this.employeeRepository = employeeRepository;
        this.storeRepository = storeRepository;
        this.expiringWithinDays = parseExpiringWithinDays(expiringWithinDays);
    }

    @Override
    public List<EmploymentContractResponse> list(AuthenticatedUser actor) {
        RoleName role = requireReader(actor);
        List<EmploymentContract> contracts = role == RoleName.ADMIN
                ? contractRepository.findAllWithEmployee()
                : contractRepository.findByStoreId(requireManagedStore(actor).getId());
        return contracts.stream().map(this::toResponse).toList();
    }

    @Override
    public EmploymentContractResponse detail(AuthenticatedUser actor, Integer contractId) {
        RoleName role = requireReader(actor);
        EmploymentContract contract = contractRepository.findDetailById(contractId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hợp đồng."));
        if (role == RoleName.MANAGER) {
            requireSameStore(requireManagedStore(actor), contract);
        }
        return toResponse(contract);
    }

    @Override
    @Transactional
    public EmploymentContractResponse create(AuthenticatedUser actor, CreateEmploymentContractRequest request) {
        RoleName role = requireWriter(actor);
        String code = normalizeCode(request);
        LocalDate startDate = requireStartDate(request);
        LocalDate endDate = request.getEndDate();
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("Ngày kết thúc không được trước ngày bắt đầu.");
        }
        BigDecimal salary = requireSalary(request.getSalary());
        Employee employee = employeeRepository.findByIdWithStore(request.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        if (employee.getStore() == null) {
            throw new BusinessException("Nhân viên chưa thuộc cửa hàng.");
        }
        if (role == RoleName.MANAGER) {
            Store managed = requireManagedStore(actor);
            if (!managed.getId().equals(employee.getStore().getId())) {
                throw new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý.");
            }
        }
        if (contractRepository.countByContractCodeIgnoreCase(code) > 0) {
            throw new BusinessException("Mã hợp đồng đã tồn tại.");
        }
        // Chưa chặn hợp đồng trùng thời gian hiệu lực vì quy tắc này chưa được chốt.

        EmploymentContract contract = EmploymentContract.builder()
                .contractCode(code)
                .employee(employee)
                .startDate(startDate)
                .endDate(endDate)
                .salary(salary)
                .signingStatus(ContractSigningStatus.CHUA_CO_BAN_KY)
                .signedDate(null)
                .signedFilePath(null)
                .terminatedDate(null)
                .build();
        try {
            contract = contractRepository.save(contract);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Mã hợp đồng đã tồn tại.");
        }
        return toResponse(contract);
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
        return EmploymentContractResponse.builder()
                .id(contract.getId())
                .contractCode(contract.getContractCode())
                .employeeId(employee == null ? null : employee.getId())
                .employeeName(employee == null ? null : employee.getFullName())
                .storeId(store == null ? null : store.getId())
                .storeName(store == null ? null : store.getStoreName())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .salary(contract.getSalary())
                .signingStatus(signing)
                .signingStatusLabel(signing == null ? null : signing.getLabel())
                .signedDate(contract.getSignedDate())
                .signedFilePath(contract.getSignedFilePath())
                .validityStatus(validity)
                .validityStatusLabel(validity.getLabel())
                .terminatedDate(contract.getTerminatedDate())
                .build();
    }

    private RoleName requireReader(AuthenticatedUser actor) {
        if (actor == null || (actor.getRoleName() != RoleName.ADMIN && actor.getRoleName() != RoleName.MANAGER)) {
            throw new BusinessException("Bạn không có quyền xem hợp đồng.");
        }
        return actor.getRoleName();
    }

    private RoleName requireWriter(AuthenticatedUser actor) {
        if (actor == null || (actor.getRoleName() != RoleName.ADMIN && actor.getRoleName() != RoleName.MANAGER)) {
            throw new BusinessException("Bạn không có quyền tạo hợp đồng.");
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

    private String normalizeCode(CreateEmploymentContractRequest request) {
        if (request == null || request.getContractCode() == null || request.getContractCode().isBlank()) {
            throw new BusinessException("Vui lòng nhập mã hợp đồng.");
        }
        String code = request.getContractCode().trim();
        if (code.length() > CODE_MAX_LENGTH) {
            throw new BusinessException("Mã hợp đồng tối đa 50 ký tự.");
        }
        if (request.getEmployeeId() == null) {
            throw new BusinessException("Vui lòng chọn nhân viên.");
        }
        return code;
    }

    private LocalDate requireStartDate(CreateEmploymentContractRequest request) {
        if (request.getStartDate() == null) {
            throw new BusinessException("Vui lòng nhập ngày bắt đầu hiệu lực.");
        }
        return request.getStartDate();
    }

    private BigDecimal requireSalary(BigDecimal salary) {
        if (salary == null) {
            throw new BusinessException("Vui lòng nhập mức lương.");
        }
        if (salary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Mức lương phải lớn hơn 0.");
        }
        BigDecimal normalized = salary.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        if (normalized.scale() > SALARY_SCALE) {
            throw new BusinessException("Mức lương chỉ được có tối đa 2 chữ số thập phân.");
        }
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > SALARY_INTEGER_DIGITS) {
            throw new BusinessException("Mức lương vượt quá giới hạn cho phép.");
        }
        return normalized;
    }

    private static Integer parseExpiringWithinDays(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw new IllegalStateException(
                        "coffee-hrm.contract.expiring-within-days phải là số nguyên dương.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "coffee-hrm.contract.expiring-within-days phải là số nguyên dương.", ex);
        }
    }
}
