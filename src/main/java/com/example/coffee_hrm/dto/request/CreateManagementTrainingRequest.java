package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateManagementTrainingRequest {

    @NotBlank(message = "Vui lòng nhập tên lớp")
    @Size(max = 150, message = "Tên lớp tối đa 150 ký tự")
    private String className;

    @NotNull(message = "Vui lòng chọn ngày bắt đầu")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "Vui lòng chọn ngày kết thúc")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @NotNull(message = "Vui lòng chọn giờ bắt đầu")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @NotNull(message = "Vui lòng chọn giờ kết thúc")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;

    @Min(value = 1, message = "Sĩ số tối đa phải lớn hơn 0")
    private Integer maxParticipants;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String notes;

    private List<Integer> employeeIds = new ArrayList<>();

    public void setClassName(String className) {
        this.className = className == null ? null : className.trim();
    }
}
