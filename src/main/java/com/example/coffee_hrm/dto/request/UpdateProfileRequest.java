package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thông tin liên hệ mà Manager hoặc Staff được tự sửa trên hồ sơ của chính mình.
 * Không chứa CCCD, vai trò, cửa hàng, trạng thái hay tên đăng nhập.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @NotBlank(message = "Vui lòng nhập họ và tên.")
    @Size(max = 100, message = "Họ và tên tối đa 100 ký tự.")
    @Pattern(regexp = "^[\\p{L}]+(?:\\s+[\\p{L}]+)*$", message = "Họ và tên chỉ được chứa chữ cái, không được nhập số.")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 20, message = "Số điện thoại gồm 9 đến 15 chữ số.")
    private String phone;

    @NotBlank(message = "Vui lòng nhập email.")
    @Size(max = 100, message = "Email không hợp lệ.")
    private String email;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự.")
    private String address;
}
