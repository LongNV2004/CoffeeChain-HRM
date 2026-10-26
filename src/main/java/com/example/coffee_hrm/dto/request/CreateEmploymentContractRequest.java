package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.ContractPaymentMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @NotNull(message = "Vui lòng chọn nhân viên.")
    private Integer employeeId;

    @NotNull(message = "Vui lòng nhập ngày bắt đầu hiệu lực.")
    private LocalDate startDate;

    private LocalDate endDate;

    @NotBlank(message = "Vui lòng nhập chức danh công việc theo hợp đồng.")
    @Size(max = 150, message = "Chức danh tối đa 150 ký tự.")
    private String jobTitle;

    @Min(value = 1, message = "Số ca mỗi tuần phải từ 1 đến 14.")
    @Max(value = 14, message = "Số ca mỗi tuần phải từ 1 đến 14.")
    private Integer weeklyShiftCount;

    @NotNull(message = "Vui lòng nhập mức lương cơ bản.")
    private BigDecimal salary;

    private BigDecimal allowance;

    @NotNull(message = "Vui lòng chọn hình thức trả lương.")
    private ContractPaymentMethod paymentMethod;

    public void setJobTitle(String jobTitle) {
        this.jobTitle = blankToNull(jobTitle);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
