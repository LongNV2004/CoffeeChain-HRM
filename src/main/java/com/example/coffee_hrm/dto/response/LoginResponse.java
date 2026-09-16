package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.RoleName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private Integer userId;
    private String username;
    private RoleName roleName;
    private Integer employeeId;
    private String employeeName;
}
