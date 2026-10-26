package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @NotNull(message = "Vui lòng nhập ngày kết thúc.")
    private LocalDate endDate;

    @NotNull(message = "Vui lòng nhập số ca làm việc quy định.")
    @Min(value = 1, message = "Số ca làm việc quy định phải lớn hơn 0.")
    @Max(value = 21, message = "Số ca làm việc quy định không được quá 21 ca/tuần.")
    private Integer weeklyShiftCount;

    @NotNull(message = "Vui lòng nhập mức lương.")
    private BigDecimal salary;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
    private String internalNote;

    public void setInternalNote(String internalNote) {
        this.internalNote = blankToNull(internalNote);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
