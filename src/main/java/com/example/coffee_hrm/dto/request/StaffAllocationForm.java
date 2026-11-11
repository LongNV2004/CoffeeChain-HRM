package com.example.coffee_hrm.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Nhân viên Manager chọn để phân bổ. Máy chủ kiểm tra lại điều kiện của từng người. */
@Getter
@Setter
public class StaffAllocationForm {

    private String week;
    private List<Integer> employeeIds = new ArrayList<>();
}
