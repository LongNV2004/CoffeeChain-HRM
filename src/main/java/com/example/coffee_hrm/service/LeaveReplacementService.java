package com.example.coffee_hrm.service;
import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.dto.response.*;
import com.example.coffee_hrm.security.AuthenticatedUser;
import java.util.List;

public interface LeaveReplacementService {
    LeaveWorkspaceSummary summary(AuthenticatedUser manager);
    LeaveWorkspacePage search(AuthenticatedUser manager, int page, ApprovalStatus status, String keyword);
    List<LeaveReplacementGap> storeGaps(AuthenticatedUser manager);
    List<LeaveReplacementGap> leaveGaps(AuthenticatedUser manager, Integer requestId);
    LeaveReplacementSuggestion suggest(AuthenticatedUser manager, Integer requestId,
                                       Integer assignmentId);
}
