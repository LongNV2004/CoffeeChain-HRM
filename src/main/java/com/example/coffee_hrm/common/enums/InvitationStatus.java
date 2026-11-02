package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái lời mời hỗ trợ ca gấp do Quản lý gửi cho nhân viên.
 */
@Getter
@RequiredArgsConstructor
public enum InvitationStatus {

    PENDING("Chờ phản hồi"),
    ACCEPTED("Đã nhận ca"),
    DECLINED("Từ chối"),
    EXPIRED("Hết hạn"),
    CANCELLED("Đã hủy");

    private final String label;
}

