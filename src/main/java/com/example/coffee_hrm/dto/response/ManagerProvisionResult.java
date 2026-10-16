package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ManagerProvisionResult {

    private final String managerName;
    private final String storeName;
    private final boolean emailSent;
    private final boolean resent;
}
