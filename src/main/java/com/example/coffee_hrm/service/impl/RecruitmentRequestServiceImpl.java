package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.entity.RecruitmentRequest;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.RecruitmentRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.service.RecruitmentRequestService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecruitmentRequestServiceImpl
        implements RecruitmentRequestService {

    private final RecruitmentRequestRepository recruitmentRequestRepository;
    private final StoreRepository storeRepository;

    @Override
    public RecruitmentRequest createRequest(
            Integer storeId,
            Integer requestedNumber,
            String reason
    ) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() ->
                        new RuntimeException("Store not found")
                );
        RecruitmentRequest request =
                RecruitmentRequest.builder()
                        .store(store)
                        .requestedNumber(requestedNumber)
                        .reason(reason)
                        .status(RecruitmentStatus.PENDING)
                        .build();
        return recruitmentRequestRepository.save(request);
    }
    @Override
    public List<RecruitmentRequest> getAllRequests() {
        return recruitmentRequestRepository.findAll();
    }

    @Override
    public void approveRequest(Integer id) {
        RecruitmentRequest request =
                recruitmentRequestRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Request not found")
                        );
        request.setStatus(
                RecruitmentStatus.APPROVED
        );
        recruitmentRequestRepository.save(request);
    }

    @Override
    public void rejectRequest(Integer id) {

        RecruitmentRequest request =
                recruitmentRequestRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Request not found")
                        );
        request.setStatus(
                RecruitmentStatus.REJECTED
        );
        recruitmentRequestRepository.save(request);
    }

    @Override
    public List<RecruitmentRequest> getRequestsByStore(Integer storeId) {
        return recruitmentRequestRepository
                .findByStoreId(storeId);
    }
}