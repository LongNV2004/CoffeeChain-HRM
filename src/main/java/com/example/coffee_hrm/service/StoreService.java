package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.CreateStoreRequest;
import com.example.coffee_hrm.dto.request.UpdateStoreRequest;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.util.List;

public interface StoreService {

    List<StoreResponse> getStores(AuthenticatedUser actor);

    StoreResponse getStore(AuthenticatedUser actor, Integer storeId);

    StoreResponse createStore(AuthenticatedUser actor, CreateStoreRequest request);

    StoreResponse updateStore(AuthenticatedUser actor, Integer storeId, UpdateStoreRequest request);
}
