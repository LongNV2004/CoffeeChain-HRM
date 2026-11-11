package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.dto.response.PayrollDetailView;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface PayrollService {

    PayrollBoardView board(AuthenticatedUser user,
                           Integer year,
                           Integer month,
                           String period,
                           Integer storeId,
                           Integer employeeId,
                           String status);

    PayrollDetailView detail(AuthenticatedUser user, int year, int month, Integer employeeId);

    void calculate(AuthenticatedUser user, int year, int month);

    void lock(AuthenticatedUser user, int year, int month);

    /**
     * Chốt bảng lương khi Manager gửi báo cáo cửa hàng: bảng lương phải đã tính, khớp dữ liệu chấm công hiện tại
     * và không còn ca thiếu dữ liệu. Bảng lương đã chốt thì giữ nguyên.
     */
    void lockForReport(AuthenticatedUser user, int year, int month);

    /** Admin trả lại báo cáo: mở khoá bảng lương để Manager tính lại rồi gửi lại. */
    void reopenForRevision(Integer storeId, int year, int month);

    PayrollBoardView empty(AuthenticatedUser user);
}
