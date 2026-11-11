package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.BirthDates;
import com.example.coffee_hrm.common.time.VietnamTime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractMoneyAndBirthDateTest {

    @Test
    void salaryShowsTheContractAmountWithoutTrailingDecimals() {
        // Cột DECIMAL(18,2) trả về 25000.00 — không hiển thị ",00".
        assertEquals("25.000", EmploymentContractResponse.formatMoney(new BigDecimal("25000.00")));
        assertEquals("100.000", EmploymentContractResponse.formatMoney(new BigDecimal("100000.00")));
        assertEquals("25", EmploymentContractResponse.formatMoney(new BigDecimal("25.00")));
        assertEquals("1.250.000", EmploymentContractResponse.formatMoney(new BigDecimal("1250000")));
        // Số lẻ thật sự trong hợp đồng vẫn giữ nguyên, không bị làm tròn.
        assertEquals("25.500,5", EmploymentContractResponse.formatMoney(new BigDecimal("25500.50")));
        assertEquals("30.000,75", EmploymentContractResponse.formatMoney(new BigDecimal("30000.75")));
        assertNull(EmploymentContractResponse.formatMoney(null));

        EmploymentContractResponse contract = EmploymentContractResponse.builder()
                .salary(new BigDecimal("32000.00")).allowance(new BigDecimal("500000.00")).build();
        assertEquals("32.000", contract.getSalaryLabel());
        assertEquals("500.000", contract.getAllowanceLabel());
    }

    @Test
    void birthDateIsOptionalButMustBeAPastDateFrom1900() {
        assertNull(BirthDates.validateOptional(null));
        LocalDate valid = LocalDate.of(2000, 5, 2);
        assertEquals(valid, BirthDates.validateOptional(valid));
        assertEquals(BirthDates.FUTURE_MESSAGE, assertThrows(BusinessException.class,
                () -> BirthDates.validateOptional(VietnamTime.today())).getMessage());
        assertEquals(BirthDates.FUTURE_MESSAGE, assertThrows(BusinessException.class,
                () -> BirthDates.validateOptional(VietnamTime.today().plusDays(1))).getMessage());
        assertEquals(BirthDates.TOO_EARLY_MESSAGE, assertThrows(BusinessException.class,
                () -> BirthDates.validateOptional(LocalDate.of(1899, 12, 31))).getMessage());
    }
}
