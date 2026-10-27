package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
import com.example.coffee_hrm.dto.request.UpdateSlotStaffingForm;
import com.example.coffee_hrm.dto.response.AvailabilityPreview;
import com.example.coffee_hrm.dto.response.SlotLimitUpdateResult;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.dto.response.WorkAvailabilityResponse;
import com.example.coffee_hrm.dto.response.WorkPositionOption;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    /**
     * Cùng lưới cấu hình ca, số đăng ký tính theo tuần chứa {@code weekStart}.
     * Chỉ tiêu theo vị trí vẫn là cấu hình lặp theo thứ, không gắn một tuần.
     */
    WeeklyAvailabilityView getStoreWeekStaffingGrid(AuthenticatedUser manager, LocalDate weekStart);

    List<WorkAvailabilityResponse> listStoreNextWeekAvailabilities(AuthenticatedUser manager);

    /**
     * Duyệt hoặc từ chối một đề xuất.
     * Khi duyệt, các ngày còn hiệu lực được xếp vào lịch làm việc.
     * Ca đã phân trùng đúng ca được giữ nguyên.
     */
    void reviewAvailability(Integer availabilityId, boolean approved, AuthenticatedUser manager);

    /** Duyệt hoặc từ chối đơn đăng ký ca của một nhân viên. Duyệt thì xếp ca vào lịch tương lai và báo nhân viên đó. */
    void reviewRegistration(String registrationKey, boolean approved, AuthenticatedUser manager);

    /**
     * Duyệt hoặc từ chối mọi đơn đang chờ của cửa hàng.
     * Mỗi đơn vẫn được xử lý riêng; đơn đã duyệt hoặc đã từ chối không đổi.
     *
     * @return số đơn đang chờ đã xử lý
     */
    int reviewAllPendingRegistrations(boolean approved, AuthenticatedUser manager);

    /**
     * Số nhân viên Staff đang làm việc tại cửa hàng của Manager.
     * Không tính Manager, Admin, nhân viên không còn hoạt động, hoặc cửa hàng khác.
     */
    int countStaffForSlotLimit(AuthenticatedUser manager);

    /**
     * Cập nhật số nhân viên tối đa của một ca trong một thứ.
     * Giá trị phải từ 1 đến số Staff đang làm việc tại cửa hàng.
     * Giới hạn giữ nguyên cho các tuần và tháng sau cho đến khi Manager đổi.
     * Không đổi trạng thái các đăng ký đã có và không sửa cấu hình cũ khi số nhân viên thay đổi.
     */
    SlotLimitUpdateResult updateSlotLimit(Integer shiftId,
                                           Integer dayOfWeek,
                                           String maxEmployees,
                                           AuthenticatedUser manager);

    /**
     * Cập nhật số nhân viên tối đa của một ca. Chỉ tiêu theo vị trí không còn được dùng.
     */
    SlotLimitUpdateResult updateSlotStaffing(Integer shiftId,
                                              Integer dayOfWeek,
                                              String maxEmployees,
                                              Map<Integer, String> requiredCounts,
                                              AuthenticatedUser manager);

    /** Lưu nhiều ô ca trong một giao dịch. Một ô lỗi thì không lưu ô nào. */
    SlotLimitUpdateResult updateSlotStaffingBatch(List<UpdateSlotStaffingForm> slots, AuthenticatedUser manager);

    /** Mỗi vị trí đếm số Staff đang có Chứng chỉ nhân viên. */
    Map<Integer, Integer> countEligibleStaffBySkill(AuthenticatedUser manager);

    /** Số Staff đang có Chứng chỉ nhân viên. */
    int countEligibleStaff(AuthenticatedUser manager);

    /** Kỹ năng đang hoạt động, dùng làm vị trí trên ca. */
    List<WorkPositionOption> listActiveWorkPositions();

    /** Vị trí nhân viên được đăng ký: kỹ năng đã đạt và đang hoạt động. */
    List<WorkPositionOption> listMyWorkPositions(AuthenticatedUser staff);
}
