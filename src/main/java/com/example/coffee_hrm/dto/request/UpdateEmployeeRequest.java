package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.security.CitizenIds;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class UpdateEmployeeRequest {

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Size(max = 15)
    private String phone;

    @Pattern(regexp = "^[0-9]{12}$", message = "Số căn cước công dân phải gồm đúng 12 chữ số.")
    private String citizenId;

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

    @NotNull
    private EmployeeStatus status;

    @NotNull
    private LocalDate hireDate;

    private LocalDate terminationDate;
    @NotBlank
    private String role;

    public void setCitizenId(String citizenId) {
        this.citizenId = CitizenIds.normalize(citizenId);
    }
}
