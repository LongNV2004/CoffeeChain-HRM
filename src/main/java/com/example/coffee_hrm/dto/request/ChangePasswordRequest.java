package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    @Size(max = 100, message = "Mật khẩu hiện tại không hợp lệ")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 6, max = 100, message = "Mật khẩu mới phải từ 6 đến 100 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới")
    @Size(min = 6, max = 100, message = "Mật khẩu xác nhận phải từ 6 đến 100 ký tự")
    private String confirmPassword;
}
