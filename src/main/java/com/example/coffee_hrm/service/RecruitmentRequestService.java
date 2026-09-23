package com.example.coffee_hrm.service;

import com.example.coffee_hrm.entity.RecruitmentRequest;

import java.util.List;

public interface RecruitmentRequestService {
    // Manager tạo đề xuất
    RecruitmentRequest createRequest(
            Integer storeId,
            Integer requestedNumber,
            String reason
    );
    // Admin xem toàn bộ đề xuất
    List<RecruitmentRequest> getAllRequests();
    // Admin duyệt
    void approveRequest(Integer id);
    // Admin từ chối
    void rejectRequest(Integer id);
    // Manager xem request của cửa hàng mình
    List<RecruitmentRequest> getRequestsByStore(Integer storeId);
}