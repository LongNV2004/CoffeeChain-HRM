package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminDashboardView {

    private final String username;
    private final String displayName;
    private final long totalStores;
    private final long activeStores;
    private final long totalEmployees;
    private final long pendingTrainingClassCount;
}
