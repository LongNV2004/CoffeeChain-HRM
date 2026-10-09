package com.example.coffee_hrm.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Các ô ca được STAFF tick theo thứ trong tuần.
 * Mỗi phần tử có dạng {@code shiftId:dayOfWeek} với dayOfWeek từ 1 (thứ Hai) đến 7 (Chủ nhật).
 * {@code durationCode} là W1, M1, M2, M6 hoặc Y1.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitWorkAvailabilityRequest {

    @Builder.Default
    private List<String> selectedSlots = new ArrayList<>();

    private String durationCode;
}
