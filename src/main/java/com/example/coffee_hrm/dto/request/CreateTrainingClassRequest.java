package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTrainingClassRequest {

    @NotNull(message = "Vui lòng chọn kỹ năng đào tạo")
    private Integer skillId;

    @NotBlank(message = "Vui lòng nhập tên lớp")
    @Size(max = 150, message = "Tên lớp tối đa 150 ký tự")
    private String className;

    @Size(max = 100, message = "Tên giảng viên tối đa 100 ký tự")
    private String trainer;

    @NotNull(message = "Vui lòng chọn ngày bắt đầu")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @FutureOrPresent(message = "Ngày bắt đầu không được ở quá khứ")
    private LocalDate startDate;

    @NotNull(message = "Vui lòng chọn ngày kết thúc")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;

    @Size(max = 255, message = "Địa điểm tối đa 255 ký tự")
    private String location;

    @Min(value = 1, message = "Sĩ số tối đa phải lớn hơn 0")
    private Integer maxParticipants;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String notes;

    public void setClassName(String className) {
        this.className = className == null ? null : className.trim();
    }

    public void setTrainer(String trainer) {
        this.trainer = trainer == null ? null : trainer.trim();
    }

    public void setLocation(String location) {
        this.location = location == null ? null : location.trim();
    }
}
