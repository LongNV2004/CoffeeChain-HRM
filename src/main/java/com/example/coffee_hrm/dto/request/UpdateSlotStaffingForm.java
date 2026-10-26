package com.example.coffee_hrm.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cập nhật tổng chỉ tiêu và nhu cầu theo vị trí của một ca trong một thứ.
 * Khóa của {@code requiredCounts} là mã kỹ năng đào tạo.
 */
@Getter
@Setter
public class UpdateSlotStaffingForm {

    private Integer shiftId;
    private Integer dayOfWeek;
    private String maxEmployees;
    private Map<Integer, String> requiredCounts = new LinkedHashMap<>();
}
