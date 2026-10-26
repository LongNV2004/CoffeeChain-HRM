package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Hồ sơ của chính người đang đăng nhập. Không chứa mật khẩu, hash hay dữ liệu xác thực.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyProfileView {

    private Integer employeeId;
    private String fullName;
    private String initials;
    private String username;
    private String email;
    private String phone;
    private String address;
    private String avatarUrl;
    private RoleName roleName;
    private String roleLabel;
    private String sidebarRole;
    private String storeName;
    private EmployeeStatus employeeStatus;
    private String employeeStatusLabel;
    private boolean accountActive;
    private String accountStatusLabel;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private LocalDate dateOfBirth;
    private String genderLabel;
    private LocalDateTime accountCreatedAt;
    private LocalDateTime lastLogin;
    @Builder.Default
    private List<EmployeeSkillStatusLine> skillCertificates = List.of();
}
