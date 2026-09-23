package com.example.coffee_hrm.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Các ô ca được STAFF tick trên lưới tuần kế tiếp.
 * Mỗi phần tử có dạng {@code shiftId:yyyy-MM-dd}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitWorkAvailabilityRequest {

    @Builder.Default
    private List<String> selectedSlots = new ArrayList<>();
}
