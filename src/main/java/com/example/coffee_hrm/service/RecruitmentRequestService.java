package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.dto.request.CreateRecruitmentRequest;
import com.example.coffee_hrm.dto.response.RecruitmentManagerOption;
import com.example.coffee_hrm.dto.response.RecruitmentRequestResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

public interface RecruitmentRequestService {

    RecruitmentRequestResponse create(AuthenticatedUser actor, CreateRecruitmentRequest request);

    List<RecruitmentRequestResponse> listMine(AuthenticatedUser actor);

    RecruitmentRequestResponse getMine(AuthenticatedUser actor, Integer id);

    String managedStoreLabel(AuthenticatedUser actor);

    List<RecruitmentRequestResponse> listForAdmin(AuthenticatedUser actor,
                                                   Integer storeId,
                                                   Integer managerId,
                                                   RecruitmentStatus status,
                                                   LocalDate createdFrom,
                                                   LocalDate createdTo);

    RecruitmentRequestResponse getForAdmin(AuthenticatedUser actor, Integer id);

    List<RecruitmentManagerOption> listManagerOptions();

    void approve(AuthenticatedUser actor, Integer id);

    void reject(AuthenticatedUser actor, Integer id, String rejectReason);

    int countPendingRequests();
}
