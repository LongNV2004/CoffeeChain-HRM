package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.Store;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ca và số chỗ mặc định của một cửa hàng mới, cùng khung với cửa hàng đã vận hành:
 * Ca Sáng 07:00–12:00, Ca Chiều 12:00–17:00, Ca Tối 17:00–22:00.
 * Ngày thường tối đa 3 người, thứ Bảy và Chủ nhật tối đa 4.
 */
public final class DefaultStoreShifts {

    private static final int WEEKDAY_MAX_EMPLOYEES = 3;
    private static final int WEEKEND_MAX_EMPLOYEES = 4;

    private static final List<ShiftTemplate> TEMPLATES = List.of(
            new ShiftTemplate("Ca Sáng", LocalTime.of(7, 0), LocalTime.of(12, 0)),
            new ShiftTemplate("Ca Chiều", LocalTime.of(12, 0), LocalTime.of(17, 0)),
            new ShiftTemplate("Ca Tối", LocalTime.of(17, 0), LocalTime.of(22, 0))
    );

    private DefaultStoreShifts() {
    }

    public static List<Shift> shiftsFor(Store store) {
        return TEMPLATES.stream()
                .map(template -> Shift.builder()
                        .store(store)
                        .shiftName(template.name())
                        .startTime(template.start())
                        .endTime(template.end())
                        .build())
                .toList();
    }

    public static List<ShiftSlotLimit> limitsFor(List<Shift> shifts) {
        LocalDateTime updatedAt = VietnamTime.now();
        List<ShiftSlotLimit> limits = new ArrayList<>();
        for (Shift shift : shifts) {
            for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
                limits.add(ShiftSlotLimit.builder()
                        .shift(shift)
                        .dayOfWeek(dayOfWeek)
                        .maxEmployees(maxEmployees(dayOfWeek))
                        .updatedAt(updatedAt)
                        .build());
            }
        }
        return limits;
    }

    static int maxEmployees(int dayOfWeek) {
        return dayOfWeek >= 6 ? WEEKEND_MAX_EMPLOYEES : WEEKDAY_MAX_EMPLOYEES;
    }

    private record ShiftTemplate(String name, LocalTime start, LocalTime end) {
    }
}
