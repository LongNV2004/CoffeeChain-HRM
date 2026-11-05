package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PendingEmployeeActivationTest {

    private final LocalDate today = LocalDate.of(2026, 11, 5);

    @Test
    void signedContractAlreadyInForcePromotesOnlyPendingEmployee() {
        Employee employee = Employee.builder().id(1).status(EmployeeStatus.PENDING_CONTRACT).build();
        EmploymentContract contract = signed(today.minusDays(1), today.plusMonths(6));
        EmployeeRepository employees = mock(EmployeeRepository.class);

        assertTrue(PendingEmployeeActivation.promote(employee, List.of(contract), today, employees));
        assertEquals(EmployeeStatus.ACTIVE, employee.getStatus());
        verify(employees).save(employee);
    }

    @Test
    void futureStartDoesNotPromoteEvenWhenTheLabelWouldBeInForce() {
        Employee employee = Employee.builder().id(1).status(EmployeeStatus.PENDING_CONTRACT).build();
        EmploymentContract contract = signed(today.plusDays(3), today.plusMonths(6));

        assertFalse(PendingEmployeeActivation.hasInForceSignedContract(List.of(contract), today));
        assertFalse(PendingEmployeeActivation.promote(employee, List.of(contract), today, mock(EmployeeRepository.class)));
        assertEquals(EmployeeStatus.PENDING_CONTRACT, employee.getStatus());
    }

    @Test
    void renewalWaitingToStartDoesNotPromote() {
        EmploymentContract source = signed(today.minusMonths(2), today.plusDays(10));
        source.setId(8);
        EmploymentContract renewal = signed(today.plusDays(11), today.plusMonths(6));
        renewal.setPreviousContract(source);

        assertFalse(PendingEmployeeActivation.hasInForceSignedContract(List.of(source, renewal), today.plusDays(11).minusDays(1)));
    }

    @Test
    void unsignedOrTerminatedEmployeeIsLeftAlone() {
        Employee terminated = Employee.builder().id(2).status(EmployeeStatus.TERMINATED).build();
        EmploymentContract unsigned = EmploymentContract.builder()
                .startDate(today)
                .endDate(today.plusMonths(3))
                .signingStatus(ContractSigningStatus.CHUA_CO_BAN_KY)
                .build();
        Employee pending = Employee.builder().id(3).status(EmployeeStatus.PENDING_CONTRACT).build();

        assertFalse(PendingEmployeeActivation.promote(terminated, List.of(signed(today, today.plusMonths(1))), today,
                mock(EmployeeRepository.class)));
        assertEquals(EmployeeStatus.TERMINATED, terminated.getStatus());
        assertFalse(PendingEmployeeActivation.promote(pending, List.of(unsigned), today, mock(EmployeeRepository.class)));
        assertEquals(EmployeeStatus.PENDING_CONTRACT, pending.getStatus());
    }

    private static EmploymentContract signed(LocalDate start, LocalDate end) {
        return EmploymentContract.builder()
                .startDate(start)
                .endDate(end)
                .signingStatus(ContractSigningStatus.DA_TAI_BAN_KY)
                .build();
    }
}
