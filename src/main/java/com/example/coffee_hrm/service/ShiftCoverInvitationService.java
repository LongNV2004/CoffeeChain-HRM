package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.CoverInvitationView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.util.List;
import java.util.Map;

/**
 * Luồng "Lời mời hỗ trợ ca gấp": Quản lý mời nhân viên đủ điều kiện nhận ca đang cần người thay,
 * nhân viên Đồng ý / Từ chối (kèm lý do), quá hạn thì tự chuyển Hết hạn.
 */
public interface ShiftCoverInvitationService {

    /** Thời gian tối đa để nhân viên phản hồi một lời mời (giờ). */
    int RESPONSE_WINDOW_HOURS = 2;

    /** Bảng điều phối cho các đơn "Nhờ Quản lý tìm người" đang chờ, key = requestId. */
    Map<Integer, CoverInvitationView.ManagerPanel> getManagerPanels(AuthenticatedUser manager);

    /** Gửi lời mời cho các nhân viên được chọn. Trả về số lời mời đã gửi. */
    int invite(Integer requestId, List<Integer> employeeIds, AuthenticatedUser manager);

    /** Lời mời đang chờ phản hồi của nhân viên đăng nhập. */
    List<CoverInvitationView.StaffCard> getPendingForStaff(AuthenticatedUser staff);

    /** Nhân viên đồng ý: nhận ca (đơn chuyển lên Quản lý duyệt), các lời mời khác bị hủy. */
    void accept(Integer invitationId, AuthenticatedUser staff);

    /**
     * Nhân viên từ chối kèm lý do.
     *
     * @return số lần từ chối lời mời gấp trong tháng sau lần này (0 nếu lời mời không gấp)
     */
    int decline(Integer invitationId, String reason, String note, AuthenticatedUser staff);

    /** Chuyển các lời mời quá hạn sang Hết hạn (tính như từ chối). Trả về số lời mời đã xử lý. */
    int expireOverdue();
}

