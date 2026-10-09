package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.RecruitmentProposalStatus;
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
                                                   RecruitmentProposalStatus status,
                                                   LocalDate createdFrom,
                                                   LocalDate createdTo);

    RecruitmentRequestResponse getForAdmin(AuthenticatedUser actor, Integer id);

    List<RecruitmentManagerOption> listManagerOptions();

    String approveCandidate(AuthenticatedUser actor, Integer requestId, Integer candidateId);

    String rejectCandidate(AuthenticatedUser actor, Integer requestId, Integer candidateId, String rejectReason);

    int countPendingRequests();
}
