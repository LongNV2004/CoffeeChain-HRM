package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class LoginRequest {

    @NotBlank(message = "Vui lòng nhập email hoặc tên đăng nhập")
    @Size(min = 3, max = 100, message = "Email hoặc tên đăng nhập phải từ 3 đến 100 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9._@+%\\-]+$", message = "Email hoặc tên đăng nhập không hợp lệ")
    private String username;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6 đến 100 ký tự")
    private String password;

    private Boolean rememberMe;

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }
}
