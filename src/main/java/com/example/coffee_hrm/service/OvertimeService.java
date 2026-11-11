package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.OvertimePageView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

/**
 * Đơn tăng ca. Bình thường nhân viên làm tối đa {@link #NORMAL_DAILY_LIMIT} ca/ngày; đơn tăng ca được duyệt
 * cho phép tới {@link #OVERTIME_DAILY_LIMIT} ca/ngày.
 */
public interface OvertimeService {

    int NORMAL_DAILY_LIMIT = 2;
    int OVERTIME_DAILY_LIMIT = 3;

    OvertimePageView staffPage(AuthenticatedUser user);

    OvertimePageView managerPage(AuthenticatedUser user);

    void submit(AuthenticatedUser user, Integer shiftId, LocalDate workDate, String reason);

    void cancel(AuthenticatedUser user, Integer requestId);

    void approve(AuthenticatedUser user, Integer requestId);

    void reject(AuthenticatedUser user, Integer requestId, String reason);
}
