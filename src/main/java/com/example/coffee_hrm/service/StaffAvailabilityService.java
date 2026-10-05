package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.dto.response.WorkAvailabilityResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.util.List;

public interface StaffAvailabilityService {

    /** Lưới ca cửa hàng cho TUẦN LÀM VIỆC KẾ TIẾP (STAFF tick chọn). */
    WeeklyAvailabilityView getNextWeekAvailabilityGrid(AuthenticatedUser staff);

    List<WorkAvailabilityResponse> listMyNextWeekAvailabilities(AuthenticatedUser staff);

    /**
     * Lý do nhân viên đang đăng nhập không được đăng ký ca.
     * {@code null} khi {@code certificationStatus = CERTIFIED}.
     */
    String registrationBlockedMessage(AuthenticatedUser staff);

    /** Ghi đè đề xuất tuần kế tiếp và thông báo Manager cửa hàng. */
    int submitNextWeekAvailability(SubmitWorkAvailabilityRequest request, AuthenticatedUser staff);

    /** Manager xem đề xuất của STAFF thuộc cửa hàng mình cho tuần kế tiếp. */
    WeeklyAvailabilityView getStoreNextWeekAvailabilityGrid(AuthenticatedUser manager);

    List<WorkAvailabilityResponse> listStoreNextWeekAvailabilities(AuthenticatedUser manager);

    /**
     * Duyệt: chuyển APPROVED và tạo assignment tuần tương ứng nếu chưa có.
     * Từ chối: chuyển REJECTED, không tạo assignment.
     */
    void reviewAvailability(Integer availabilityId, boolean approved, AuthenticatedUser manager);

    /**
     * Duyệt hoặc từ chối mọi đề xuất đang chờ của tuần kế tiếp.
     * Duyệt thì mỗi slot được xếp thẳng vào lịch làm việc.
     *
     * @return thông báo kết quả cho Manager
     */
    String reviewAllNextWeekAvailabilities(boolean approved, AuthenticatedUser manager);
}
