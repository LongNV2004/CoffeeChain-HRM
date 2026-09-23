package com.example.coffee_hrm.service;

import com.example.coffee_hrm.entity.Store;

import java.util.List;

public interface StoreService {

    List<Store> getAllStores();

    Store getStoreById(Integer id);

}