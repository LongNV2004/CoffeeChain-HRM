package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor

public class StoreServiceImpl implements StoreService {
    private final StoreRepository storeRepository;
    @Override
    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }
    @Override
    public Store getStoreById(Integer id) {
        return storeRepository.findById(id)
                .orElse(null);
    }
}