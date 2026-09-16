package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Integer> {

    long countByIsActiveTrue();

    Optional<Store> findByManager_Id(Integer managerEmployeeId);
}
