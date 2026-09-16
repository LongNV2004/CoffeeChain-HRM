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
public class RoleResponse {

    private Integer id;
    private RoleName roleName;
}
