package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLeaveRequest {
    @NotNull(message = "Vui lòng chọn ngày bắt đầu.")
    private LocalDate startDate;

    @NotNull(message = "Vui lòng chọn ngày kết thúc.")
    private LocalDate endDate;

    @NotBlank(message = "Vui lòng nhập lý do xin nghỉ.")
    @Size(max = 255, message = "Lý do không được vượt quá 255 ký tự.")
    private String reason;
}
