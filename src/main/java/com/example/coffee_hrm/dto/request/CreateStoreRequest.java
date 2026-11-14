package com.example.coffee_hrm.dto.request;

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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStoreRequest {

    @NotBlank(message = "Vui lòng nhập tên cửa hàng")
    @Size(max = 100, message = "Tên cửa hàng tối đa 100 ký tự")
    private String storeName;

    @NotBlank(message = "Vui lòng nhập địa chỉ")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @NotNull(message = "Vui lòng nhập số ca phép")
    @Min(value = 0, message = "Số ca phép không được âm")
    private Integer totalLeaveSlots;

    /** Kept so existing day balances are not rewritten from this form. */
    private Integer totalLeaveDays;

    @NotNull(message = "Vui lòng chọn ngày khai trương")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate openingDate;

    /** Địa chỉ IP mạng được phép chấm công: nhiều IP, phân cách bằng xuống dòng hoặc dấu phẩy. */
    @Size(max = 2000, message = "Danh sách địa chỉ IP tối đa 2000 ký tự")
    private String allowedIps;
}
