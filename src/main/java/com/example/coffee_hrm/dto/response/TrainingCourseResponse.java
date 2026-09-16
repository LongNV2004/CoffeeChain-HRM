package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingCourseResponse {

    private Integer id;
    private Integer storeId;
    private String storeName;
    private String courseName;
    private String description;
    private BigDecimal passScore;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
