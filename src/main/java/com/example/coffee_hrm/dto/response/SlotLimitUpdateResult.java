package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlotLimitUpdateResult {

    private final String message;
    private final boolean warning;
}
