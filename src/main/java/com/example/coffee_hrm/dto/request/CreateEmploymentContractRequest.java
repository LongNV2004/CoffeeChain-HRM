package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CreateEmploymentContractRequest {

    @NotBlank(message = "Vui lòng nhập mã hợp đồng")
    @Size(max = 50, message = "Mã hợp đồng tối đa 50 ký tự")
    private String contractCode;

    @NotNull(message = "Vui lòng chọn nhân viên")
    private Integer employeeId;

    @NotNull(message = "Vui lòng nhập ngày bắt đầu hiệu lực")
    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull(message = "Vui lòng nhập mức lương")
    private BigDecimal salary;

    public void setContractCode(String contractCode) {
        if (contractCode == null || contractCode.isBlank()) {
            this.contractCode = null;
            return;
        }
        this.contractCode = contractCode.trim();
    }
}
