package com.example.coffee_hrm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu giao diện cho luồng "Lời mời hỗ trợ ca gấp".
 */
public final class CoverInvitationView {

    private CoverInvitationView() {
    }

    /** Bảng điều phối của Quản lý cho một đơn đang cần người thay. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ManagerPanel {
        private Integer requestId;
        private boolean urgent;
        private boolean canInvite;
        private List<InvitationItem> invitations;
        private List<Candidate> candidates;
        private long pendingCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvitationItem {
        private Integer invitationId;
        private String employeeName;
        private String statusCode;
        private String statusLabel;
        private String declineReasonLabel;
        private String declineNote;
        private String timeLabel;
    }

    /** Nhân viên đủ điều kiện nhận ca (đã lọc trùng giờ, nghỉ phép, chứng chỉ). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private Integer employeeId;
        private String fullName;
        private int monthScore;
        private int urgentDeclinesThisMonth;
        private boolean alreadyInvited;
    }

    /** Thẻ lời mời hiển thị cho nhân viên được mời. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffCard {
        private Integer invitationId;
        private Integer requestId;
        private String requesterName;
        private String shiftName;
        private String timeRange;
        private LocalDate workDate;
        private boolean urgent;
        private String expiresAtLabel;
        private long minutesLeft;
        private int declinesUsed;
        private int freeDeclines;
        private boolean nextDeclinePenalized;
    }
}

