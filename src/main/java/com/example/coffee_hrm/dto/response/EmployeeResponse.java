package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponse {

    private Integer id;
    private String fullName;
    private String phone;
    private String email;
    private String address;
    private String avatarUrl;
    private Integer storeId;
    private String storeName;
    private EmployeeStatus status;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private LocalDateTime createdAt;
    private Integer userId;
}
