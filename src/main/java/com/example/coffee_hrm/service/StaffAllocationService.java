package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.StaffAllocationRoster;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

/**
 * Phân bổ nhân sự trên màn hình Duyệt đăng ký lịch làm việc.
 * Chỉ Manager dùng được, trong phạm vi cửa hàng mình quản lý.
 */
public interface StaffAllocationService {

    /**
     * Danh sách Staff của cửa hàng kèm hợp đồng, tình trạng đăng ký ca của tuần và điều kiện được phân bổ.
     * Không ghi dữ liệu.
     */
    StaffAllocationRoster roster(AuthenticatedUser manager, LocalDate weekStart);

    /**
     * Phân bổ các nhân viên đã chọn trong một giao dịch, không cần duyệt thêm.
     * Điều kiện của từng người được kiểm tra lại trên dữ liệu mới nhất; một người không còn đủ điều kiện thì không phân bổ ai.
     * Mỗi nhân viên nhận một đơn 1 tuần đã duyệt với đúng số ca còn thiếu theo hợp đồng, xếp vào các ca còn thiếu người;
     * ca được đưa thẳng vào lịch làm việc và nhân viên được thông báo. Không đổi trạng thái hợp đồng hay nhân viên.
     */
    Outcome allocate(AuthenticatedUser manager, LocalDate weekStart, List<Integer> employeeIds);

    record Outcome(int employees, int shifts, LocalDate weekStart) {
    }
}
