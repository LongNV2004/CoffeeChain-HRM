package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.service.impl.ContractValidityRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    public int promoteReady(LocalDate today) {
        if (today == null) {
            return 0;
        }
        int promoted = 0;
        for (Employee employee : employeeRepository.findByStatusWithStore(EmployeeStatus.PENDING_CONTRACT)) {
            if (promote(employee, contractRepository.findByEmployeeId(employee.getId()), today, employeeRepository)) {
                promoted++;
            }
        }
        return promoted;
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
        promote(employee, contracts.findByEmployeeId(employeeId), today, employees);
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
            if (ContractValidityRules.resolve(contract, today, renewed).isInForce()) {
                return true;
            }
        }
        return false;
    }
}
