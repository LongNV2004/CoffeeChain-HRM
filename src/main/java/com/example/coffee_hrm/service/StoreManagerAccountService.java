package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.CreateStoreManagerRequest;
import com.example.coffee_hrm.dto.response.ManagerProvisionResult;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface StoreManagerAccountService {

    ManagerProvisionResult createManager(AuthenticatedUser actor, Integer storeId, CreateStoreManagerRequest request);

    ManagerProvisionResult resendCredentials(AuthenticatedUser actor, Integer storeId);
}
