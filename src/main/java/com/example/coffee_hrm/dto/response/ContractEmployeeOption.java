package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ContractEmployeeOption {

    private final Integer id;
    private final String fullName;
    private final String citizenId;
    private final String storeName;
    private final String positionLabel;
    private final LocalDate hireDate;
}
