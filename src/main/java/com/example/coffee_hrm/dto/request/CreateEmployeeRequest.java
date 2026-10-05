package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeRequest {

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Size(max = 15)
    private String phone;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 255)
    private String address;

    @Size(max = 255)
    private String avatarUrl;

    @NotNull
    private Integer storeId;

    private EmployeeStatus status;

    @NotNull
    private LocalDate hireDate;
    @NotBlank
    private String role;

    private boolean createAccount;

    @Size(max = 50)
    private String username;

    private String temporaryPassword;
}
