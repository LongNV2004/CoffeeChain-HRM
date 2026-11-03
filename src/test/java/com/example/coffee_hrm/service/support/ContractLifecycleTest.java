package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractLifecycleTest {

    private final LocalDate today = LocalDate.of(2026, 11, 2);

    @Test
    void effectiveTerminationClosesAnOpenContractAndLeavesAClosedOneUntouched() {
        EmploymentContract open = contract(1, today.minusMonths(1), today.plusMonths(2), null);
        EmploymentContract alreadyEnded = contract(2, today.minusYears(1), today.minusMonths(1), null);
        EmploymentContract alreadyEarly = contract(3, today.minusMonths(6), today.plusMonths(1), today.minusDays(10));

        int changed = ContractLifecycle.applyEffectiveTermination(
                List.of(open, alreadyEnded, alreadyEarly), today.minusDays(1), today);

        assertEquals(1, changed);
        assertEquals(today.minusDays(1), open.getTerminatedDate());
        assertNull(alreadyEnded.getTerminatedDate());
        assertEquals(today.minusDays(10), alreadyEarly.getTerminatedDate());
    }

    @Test
    void futureTerminationDateIsRecordedWithoutPretendingTheContractAlreadyEnded() {
        EmploymentContract open = contract(1, today.minusMonths(1), today.plusMonths(2), null);

        ContractLifecycle.applyEffectiveTermination(List.of(open), today.plusDays(5), today);

        assertEquals(today.plusDays(5), open.getTerminatedDate());
    }

    @Test
    void earlyTerminatedContractCannotBeRenewedWhileTheSameEmploymentContinues() {
        EmploymentContract early = contract(4, today.minusMonths(3), today.plusMonths(3), today.minusDays(2));
        Employee stillEmployed = Employee.builder().id(8).status(EmployeeStatus.ON_LEAVE)
                .terminationDate(today.minusDays(2)).build();

        BusinessException error = assertThrows(BusinessException.class,
                () -> ContractLifecycle.requireRenewable(List.of(early), stillEmployed, today));

        assertEquals(ContractLifecycle.CANNOT_RENEW_EARLY_TERMINATION, error.getMessage());
    }

    @Test
    void rehiredEmployeeCanReceiveANewContractAfterEarlyTermination() {
        EmploymentContract early = contract(4, today.minusYears(1), today.minusMonths(6), today.minusMonths(8));
        Employee rehired = Employee.builder().id(8).status(EmployeeStatus.ACTIVE).terminationDate(null).build();

        ContractLifecycle.requireRenewable(List.of(early), rehired, today);
    }

    private static EmploymentContract contract(int id, LocalDate start, LocalDate end, LocalDate terminated) {
        return EmploymentContract.builder()
                .id(id)
                .startDate(start)
                .endDate(end)
                .terminatedDate(terminated)
                .build();
    }
}
