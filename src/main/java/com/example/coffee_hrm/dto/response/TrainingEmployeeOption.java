package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingEmployeeOption {

    private Integer employeeId;
    private String fullName;
    private String email;
    private Integer storeId;
    private String storeName;
}
