package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftResponse {

    private Integer id;
    private Integer storeId;
    private String storeName;
    private String shiftName;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime createdAt;
}
