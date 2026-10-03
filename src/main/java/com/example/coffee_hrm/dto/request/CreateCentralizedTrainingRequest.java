package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCentralizedTrainingRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "Vui lòng chọn ít nhất một kỹ năng đào tạo")
    private List<@NotNull(message = "Kỹ năng không hợp lệ") Integer> skillIds = new ArrayList<>();

    @NotEmpty(message = "Vui lòng chọn ít nhất một cửa hàng")
    private List<@NotNull(message = "Cửa hàng không hợp lệ") Integer> storeIds = new ArrayList<>();

    @NotEmpty(message = "Vui lòng chọn ít nhất một nhân viên")
    private List<@NotNull(message = "Nhân viên không hợp lệ") Integer> employeeIds = new ArrayList<>();

    @NotNull(message = "Vui lòng chọn người đào tạo")
    private Integer trainerId;

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

    public void setClassName(String className) {
        this.className = className == null ? null : className.trim();
    }
}
