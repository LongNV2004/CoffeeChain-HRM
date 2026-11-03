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
    void employeeWithoutContractIsMarkedAndClosedContractDoesNotCountAsEffective() {
        EmployeeResponse employee = EmployeeResponse.builder().id(4).fullName("Quý").build();
        EmploymentContractResponse expired = contract(8, 4, ContractValidityStatus.DA_CHAM_DUT, "Đã chấm dứt",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        EmployeeRecordSupport.ContractTotals totals = EmployeeRecordSupport.applyCurrentStatus(
                List.of(employee), List.of(expired));

        assertEquals("DA_CHAM_DUT", employee.getContractStatusKey());
        assertEquals(0, totals.effectiveCount());
        assertEquals(0, totals.expiringCount());
    }

    @Test
    void missingContractIsExplicitAndOnlyOneCurrentStatusIsShown() {
        EmployeeResponse employee = EmployeeResponse.builder().id(5).build();
        EmploymentContractResponse active = contract(1, 5, ContractValidityStatus.DANG_HIEU_LUC, "Đang hiệu lực",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        EmploymentContractResponse expiring = contract(2, 5, ContractValidityStatus.CHO_GIA_HAN, "Chờ gia hạn",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31));

        EmployeeRecordSupport.ContractTotals totals = EmployeeRecordSupport.applyCurrentStatus(
                List.of(employee), List.of(active, expiring));

        assertEquals("CHO_GIA_HAN", employee.getContractStatusKey());
        assertEquals(2, totals.effectiveCount());
        assertEquals(1, totals.expiringCount());
    }

    @Test
    void laterContractIsRenewalAndEarlierContractStaysInHistory() {
        EmploymentContractResponse first = contract(1, 5, ContractValidityStatus.DA_CHAM_DUT, "Đã chấm dứt",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        EmploymentContractResponse second = contract(2, 5, ContractValidityStatus.DANG_HIEU_LUC, "Đang hiệu lực",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        List<EmployeeHistoryEvent> events = EmployeeRecordSupport.history(
                null, LocalDate.of(2025, 1, 1), null, "Cầu Giấy", null, false, List.of(), List.of(second, first));

        assertTrue(events.stream().anyMatch(event -> event.title().startsWith("Tạo hợp đồng")));
        assertTrue(events.stream().anyMatch(event -> event.title().startsWith("Gia hạn hợp đồng")));
        assertTrue(events.stream().anyMatch(event -> "Cửa hàng hiện tại".equals(event.title())
                && event.detail().contains("Cầu Giấy")));
        assertTrue(events.stream().anyMatch(event -> event.title().contains("Đã chấm dứt")));
        assertTrue(events.stream().anyMatch(event -> event.title().contains("Chờ gia hạn")));
    }

    @Test
    void earlyTerminationIsNotLabeledAsARenewalOfTheNextContract() {
        EmploymentContractResponse first = EmploymentContractResponse.builder()
                .id(1)
                .employeeId(5)
                .contractCode("HD-1")
                .validityStatus(ContractValidityStatus.DA_CHAM_DUT_TRUOC_THOI_HAN)
                .validityStatusLabel("Đã chấm dứt trước thời hạn")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .terminatedDate(LocalDate.of(2025, 6, 1))
                .build();
        EmploymentContractResponse second = contract(2, 5, ContractValidityStatus.DANG_HIEU_LUC, "Đang hiệu lực",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31));

        List<EmployeeHistoryEvent> events = EmployeeRecordSupport.history(
                null, LocalDate.of(2026, 3, 1), null, "Cầu Giấy", null, false, List.of(), List.of(first, second));

        assertTrue(events.stream().noneMatch(event -> event.title().startsWith("Gia hạn hợp đồng")));
        assertTrue(events.stream().anyMatch(event -> event.title().contains("Đã chấm dứt trước thời hạn")));
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
