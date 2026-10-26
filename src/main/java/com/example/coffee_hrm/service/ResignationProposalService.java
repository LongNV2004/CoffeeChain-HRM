package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import com.example.coffee_hrm.dto.request.CreateResignationProposalRequest;
import com.example.coffee_hrm.dto.response.ResignationDecisionResult;
import com.example.coffee_hrm.dto.response.ResignationEmployeeOption;
import com.example.coffee_hrm.dto.response.ResignationProposalResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

public interface ResignationProposalService {

    ResignationDecisionResult create(AuthenticatedUser actor, CreateResignationProposalRequest request);

    List<ResignationProposalResponse> listMine(AuthenticatedUser actor);

    ResignationProposalResponse getMine(AuthenticatedUser actor, Integer id);

    List<ResignationEmployeeOption> listSelectableEmployees(AuthenticatedUser actor);

    String managedStoreLabel(AuthenticatedUser actor);

    List<ResignationProposalResponse> listForAdmin(AuthenticatedUser actor,
                                                   Integer storeId,
                                                   ResignationProposalStatus status,
                                                   String keyword);

    ResignationProposalResponse getForAdmin(AuthenticatedUser actor, Integer id);

    ResignationDecisionResult approveItem(AuthenticatedUser actor, Integer proposalId, Integer itemId);

    ResignationDecisionResult resendDecisionNotice(AuthenticatedUser actor, Integer proposalId, Integer itemId);

    ResignationDecisionResult rejectItem(AuthenticatedUser actor, Integer proposalId, Integer itemId, String rejectReason);

    ResignationDecisionResult approvePending(AuthenticatedUser actor, Integer proposalId);

    ResignationDecisionResult rejectPending(AuthenticatedUser actor, Integer proposalId, String rejectReason);

    int applyDueTerminations(LocalDate today);

    int countPendingProposals();
}
