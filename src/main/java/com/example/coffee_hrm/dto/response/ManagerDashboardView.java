package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ManagerDashboardView {

    private final String username;
    private final String displayName;
    private final Integer storeId;
    private final String storeName;
    private final String storeAddress;
    private final long storeEmployeeCount;
    private final long todayShiftCount;
    private final long pendingLeaveCount;
    private final long pendingShiftChangeCount;
}
