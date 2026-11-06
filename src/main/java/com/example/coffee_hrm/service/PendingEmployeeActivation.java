package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.service.impl.ContractValidityRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chỉ chuyển nhân viên đang chờ hoàn thiện hợp đồng sang đang làm việc
 * khi đã có bản hợp đồng ký và hợp đồng đó đang hiệu lực.
 * Không đụng hồ sơ đã nghỉ việc.
 */
@Component
@RequiredArgsConstructor
public class PendingEmployeeActivation {

    private final EmployeeRepository employeeRepository;
    private final EmploymentContractRepository contractRepository;
    private final UserRepository userRepository;

    @Transactional
    public int promoteReady(LocalDate today) {
        return syncReady(today);
    }

    public int syncReady(LocalDate today) {
        if (today == null) {
            return 0;
        }
        int changed = 0;
        for (EmployeeStatus status : java.util.List.of(
                EmployeeStatus.PENDING_CONTRACT, EmployeeStatus.ACTIVE, EmployeeStatus.ON_LEAVE)) {
            for (Employee employee : employeeRepository.findByStatusWithStore(status)) {
                if (sync(employee, contractRepository.findByEmployeeId(employee.getId()), today,
                        employeeRepository, userRepository)) {
                    changed++;
                }
            }
        }
        return changed;
    }

    public static void afterContractSaved(EmployeeRepository employees,
                                          EmploymentContractRepository contracts,
                                          Integer employeeId,
                                          LocalDate today) {
        if (employees == null || contracts == null || employeeId == null || today == null) {
            return;
        }
        Employee employee = employees.findById(employeeId).orElse(null);
        if (employee == null) {
            return;
        }
        sync(employee, contracts.findByEmployeeId(employeeId), today, employees, null);
    }

    public static void afterContractSaved(EmployeeRepository employees,
                                          EmploymentContractRepository contracts,
                                          UserRepository users,
                                          Integer employeeId,
                                          LocalDate today) {
        if (employees == null || contracts == null || employeeId == null || today == null) {
            return;
        }
        Employee employee = employees.findById(employeeId).orElse(null);
        if (employee == null) {
            return;
        }
        sync(employee, contracts.findByEmployeeId(employeeId), today, employees, users);
    }

    static boolean sync(Employee employee,
                        List<EmploymentContract> contracts,
                        LocalDate today,
                        EmployeeRepository employees,
                        UserRepository users) {
        if (employee == null || today == null || employees == null) {
            return false;
        }
        if (employee.getStatus() == EmployeeStatus.TERMINATED && (contracts == null || contracts.isEmpty())) {
            return false;
        }
        ContractValidityRules.WorkAccess access = ContractValidityRules.workAccess(contracts, today);
        if (employee.getStatus() == EmployeeStatus.ON_LEAVE
                && access == ContractValidityRules.WorkAccess.WORKING) {
            return false;
        }
        EmployeeStatus target = switch (access) {
            case WORKING -> EmployeeStatus.ACTIVE;
            case WAITING -> EmployeeStatus.PENDING_CONTRACT;
            case ENDED -> EmployeeStatus.TERMINATED;
        };
        boolean changed = false;
        if (employee.getStatus() != target) {
            employee.setStatus(target);
            if (target == EmployeeStatus.TERMINATED && employee.getTerminationDate() == null) {
                employee.setTerminationDate(today);
            }
            if (target != EmployeeStatus.TERMINATED) {
                employee.setTerminationDate(null);
            }
            employees.save(employee);
            changed = true;
        }
        boolean lock = access == ContractValidityRules.WorkAccess.ENDED;
        if (syncAccount(employee, users, lock)) {
            changed = true;
        }
        return changed;
    }

    private static boolean syncAccount(Employee employee, UserRepository users, boolean lock) {
        if (users == null || employee == null || employee.getId() == null) {
            return false;
        }
        User account = users.findByEmployee_Id(employee.getId()).orElse(null);
        if (account == null || account.getRole() == null || account.getRole().getRoleName() == null) {
            return false;
        }
        RoleName role = account.getRole().getRoleName();
        if (role != RoleName.STAFF && role != RoleName.MANAGER) {
            return false;
        }
        boolean shouldBeActive = !lock;
        boolean currentlyActive = !Boolean.FALSE.equals(account.getIsActive());
        if (currentlyActive == shouldBeActive) {
            return false;
        }
        account.setIsActive(shouldBeActive);
        users.save(account);
        return true;
    }

    static boolean promote(Employee employee,
                           List<EmploymentContract> contracts,
                           LocalDate today,
                           EmployeeRepository employees) {
        if (employee == null || employee.getStatus() != EmployeeStatus.PENDING_CONTRACT || today == null) {
            return false;
        }
        if (!hasInForceSignedContract(contracts, today)) {
            return false;
        }
        employee.setStatus(EmployeeStatus.ACTIVE);
        employees.save(employee);
        return true;
    }

    static boolean hasInForceSignedContract(List<EmploymentContract> contracts, LocalDate today) {
        if (contracts == null || contracts.isEmpty() || today == null) {
            return false;
        }
        Set<Integer> renewedSourceIds = new HashSet<>();
        for (EmploymentContract contract : contracts) {
            if (contract.getPreviousContract() != null && contract.getPreviousContract().getId() != null) {
                renewedSourceIds.add(contract.getPreviousContract().getId());
            }
        }
        for (EmploymentContract contract : contracts) {
            if (contract.getSigningStatus() != ContractSigningStatus.DA_TAI_BAN_KY) {
                continue;
            }
            if (contract.getStartDate() == null || contract.getStartDate().isAfter(today)) {
                continue;
            }
            boolean renewed = contract.getId() != null && renewedSourceIds.contains(contract.getId());
            if (ContractValidityRules.resolve(contract, today, renewed).allowsWork()) {
                return true;
            }
        }
        return false;
    }
}
