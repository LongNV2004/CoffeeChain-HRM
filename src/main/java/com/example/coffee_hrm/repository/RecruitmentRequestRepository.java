package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.RecruitmentRequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecruitmentRequestRepository
        extends JpaRepository<RecruitmentRequest, Integer> {

    List<RecruitmentRequest> findByStoreId(Integer storeId);
}