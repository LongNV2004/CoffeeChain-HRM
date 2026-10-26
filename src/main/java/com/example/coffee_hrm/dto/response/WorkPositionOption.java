package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Vị trí làm việc trên ca, ánh xạ từ một kỹ năng đào tạo đang hoạt động.
 */
@Getter
@AllArgsConstructor
public class WorkPositionOption {

    private final Integer id;
    private final String name;
}
