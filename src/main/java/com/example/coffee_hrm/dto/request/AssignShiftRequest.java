package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignShiftRequest {

    @NotNull(message = "Vui lòng chọn ca làm việc.")
    private Integer shiftId;

    @NotNull(message = "Vui lòng chọn nhân viên.")
    private Integer employeeId;

    @NotNull(message = "Vui lòng chọn ngày làm việc.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate workDate;
}
