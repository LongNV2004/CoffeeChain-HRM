package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlotConfigTotals {

    private final int weeklyDemand;
    private final int registrations;
    private final int positionTarget;
    private final int positionShortfall;
    private final String shortfallText;
}
