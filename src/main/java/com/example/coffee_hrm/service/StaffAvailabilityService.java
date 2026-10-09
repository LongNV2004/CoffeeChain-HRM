package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
import com.example.coffee_hrm.dto.response.AvailabilityPreview;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.dto.response.WorkAvailabilityResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.util.List;

public interface StaffAvailabilityService {

    /** Lưới ca theo thứ trong tuần để STAFF chọn lịch làm việc định kỳ. */
    WeeklyAvailabilityView getNextWeekAvailabilityGrid(AuthenticatedUser staff);

    List<WorkAvailabilityResponse> listMyNextWeekAvailabilities(AuthenticatedUser staff);

    /**
     * Lý do nhân viên đang đăng nhập không được đăng ký ca.
     * {@code null} khi {@code certificationStatus = CERTIFIED}.
     */
    String registrationBlockedMessage(AuthenticatedUser staff);

    /** Xem trước các ngày sẽ được lặp trong thời hạn, chưa ghi dữ liệu. */
    AvailabilityPreview previewRegistration(SubmitWorkAvailabilityRequest request, AuthenticatedUser staff);

    /**
     * Ghi đăng ký định kỳ ở trạng thái chờ duyệt và thông báo Manager.
     * Không tạo ca làm việc. Không sửa lịch đã duyệt.
     */
    int submitNextWeekAvailability(SubmitWorkAvailabilityRequest request, AuthenticatedUser staff);

    /** Manager xem đăng ký định kỳ còn hiệu lực hoặc đang chờ của cửa hàng. */
    WeeklyAvailabilityView getStoreNextWeekAvailabilityGrid(AuthenticatedUser manager);

    List<WorkAvailabilityResponse> listStoreNextWeekAvailabilities(AuthenticatedUser manager);

    /**
     * Duyệt hoặc từ chối một đề xuất.
     * Khi duyệt, các ngày còn hiệu lực được xếp vào lịch làm việc.
     * Ca đã phân trùng đúng slot được giữ nguyên.
     */
    void reviewAvailability(Integer availabilityId, boolean approved, AuthenticatedUser manager);

    /** Duyệt hoặc từ chối cả một lần đăng ký định kỳ. Duyệt thì xếp ca vào lịch tương lai và báo nhân viên. */
    void reviewRegistration(String registrationKey, boolean approved, AuthenticatedUser manager);

    /**
     * Duyệt hoặc từ chối mọi đề xuất đang chờ.
     *
     * @return thông báo kết quả cho Manager
     */
    String reviewAllNextWeekAvailabilities(boolean approved, AuthenticatedUser manager);
}
