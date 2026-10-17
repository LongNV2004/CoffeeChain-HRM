package com.example.coffee_hrm.dto.response;

import java.util.List;

/**
 * Nhân sự của một cửa hàng Manager đang quản lý.
 * Các số liệu được đếm từ chính danh sách hồ sơ, mỗi nhân viên một lần.
 */
public record ManagedEmployeeDirectory(
        Integer storeId,
        String storeName,
        String storeAddress,
        long totalCount,
        long activeCount,
        long onLeaveCount,
        long terminatedCount,
        long managerCount,
        long staffCount,
        long noAccountCount,
        long otherCount,
        List<EmployeeResponse> employees) {
}
