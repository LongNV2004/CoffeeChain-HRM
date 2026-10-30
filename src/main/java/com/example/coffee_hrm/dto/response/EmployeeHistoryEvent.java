package com.example.coffee_hrm.dto.response;

import java.time.LocalDate;

public record EmployeeHistoryEvent(LocalDate date, String title, String detail) {
}
