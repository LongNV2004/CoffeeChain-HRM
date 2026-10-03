package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddTrainingClassStudentsRequest {

    @NotEmpty(message = "Vui lòng chọn ít nhất một nhân viên.")
    private List<@NotNull(message = "Nhân viên không hợp lệ.") Integer> employeeIds;
}
