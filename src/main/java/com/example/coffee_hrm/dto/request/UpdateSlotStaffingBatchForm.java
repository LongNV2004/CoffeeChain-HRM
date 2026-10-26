package com.example.coffee_hrm.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class UpdateSlotStaffingBatchForm {

    private String weekStart;
    private List<UpdateSlotStaffingForm> slots = new ArrayList<>();
}
