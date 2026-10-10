package com.example.coffee_hrm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class UpdateStoreRequest {

    @NotBlank(message = "Vui lòng nhập tên cửa hàng")
    @Size(max = 100, message = "Tên cửa hàng tối đa 100 ký tự")
    private String storeName;

    @NotBlank(message = "Vui lòng nhập địa chỉ")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @NotNull(message = "Vui lòng nhập số ngày phép")
    @Min(value = 0, message = "Số ngày phép không được âm")
    private Integer totalLeaveDays;

    @NotNull(message = "Vui lòng chọn trạng thái")
    private Boolean isActive;

    @Valid
    private List<StoreOperatingHourRequest> operatingHours;

    private String latitude;

    private String longitude;
}
