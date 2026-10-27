package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResignationEmployeeOption {

    private Integer id;
    private String fullName;
    private String citizenId;
    private String positionLabel;
    private String statusLabel;
    private String hireDate;
}
