package com.example.coffee_hrm.dto.response;

import java.time.LocalDate;
import java.util.List;

public record LeaveSlotPage(
        List<LeaveSlotOption> slots,
        LocalDate fromDate,
        LocalDate toDate,
        boolean truncated) {
}
