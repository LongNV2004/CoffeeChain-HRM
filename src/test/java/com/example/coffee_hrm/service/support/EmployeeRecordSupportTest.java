package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.dto.response.EmployeeHistoryEvent;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeRecordSupportTest {

    @Test
    void employeeWithoutContractIsMarkedAndExpiredContractDoesNotCountAsEffective() {
        EmployeeResponse employee = EmployeeResponse.builder().id(4).fullName("Quý").build();
        EmploymentContractResponse expired = contract(8, 4, ContractValidityStatus.DA_HET_HAN, "Đã hết hạn",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        EmployeeRecordSupport.ContractTotals totals = EmployeeRecordSupport.applyCurrentStatus(
                List.of(employee), List.of(expired));

        assertEquals("DA_HET_HAN", employee.getContractStatusKey());
        assertEquals(0, totals.effectiveCount());
        assertEquals(0, totals.expiringCount());
    }

    @Test
    void missingContractIsExplicitAndOnlyOneCurrentStatusIsShown() {
        EmployeeResponse employee = EmployeeResponse.builder().id(5).build();
        EmploymentContractResponse active = contract(1, 5, ContractValidityStatus.DANG_HIEU_LUC, "Đang hiệu lực",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        EmploymentContractResponse expiring = contract(2, 5, ContractValidityStatus.SAP_HET_HAN, "Sắp hết hạn",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31));

        EmployeeRecordSupport.ContractTotals totals = EmployeeRecordSupport.applyCurrentStatus(
                List.of(employee), List.of(active, expiring));

        assertEquals("SAP_HET_HAN", employee.getContractStatusKey());
        assertEquals(2, totals.effectiveCount());
        assertEquals(1, totals.expiringCount());
    }

    @Test
    void laterContractIsRenewalAndEarlierContractStaysInHistory() {
        EmploymentContractResponse first = contract(1, 5, ContractValidityStatus.DA_HET_HAN, "Đã hết hạn",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        EmploymentContractResponse second = contract(2, 5, ContractValidityStatus.DANG_HIEU_LUC, "Đang hiệu lực",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        List<EmployeeHistoryEvent> events = EmployeeRecordSupport.history(
                null, LocalDate.of(2025, 1, 1), null, "Cầu Giấy", null, false, List.of(), List.of(second, first));

        assertTrue(events.stream().anyMatch(event -> event.title().startsWith("Tạo hợp đồng")));
        assertTrue(events.stream().anyMatch(event -> event.title().startsWith("Gia hạn hợp đồng")));
        assertTrue(events.stream().anyMatch(event -> "Cửa hàng hiện tại".equals(event.title())
                && event.detail().contains("Cầu Giấy")));
        assertTrue(events.stream().anyMatch(event -> event.title().contains("hết hạn")));
    }

    private static EmploymentContractResponse contract(int id, int employeeId, ContractValidityStatus status,
                                                       String label, LocalDate start, LocalDate end) {
        return EmploymentContractResponse.builder()
                .id(id)
                .employeeId(employeeId)
                .contractCode("HD-" + id)
                .validityStatus(status)
                .validityStatusLabel(label)
                .startDate(start)
                .endDate(end)
                .build();
    }
}
