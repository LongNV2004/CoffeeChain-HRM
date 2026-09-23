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

    /** Ghi đè đề xuất tuần kế tiếp và thông báo Manager cửa hàng. */
    int submitNextWeekAvailability(SubmitWorkAvailabilityRequest request, AuthenticatedUser staff);

    /** Manager xem đề xuất của STAFF thuộc cửa hàng mình cho tuần kế tiếp. */
    WeeklyAvailabilityView getStoreNextWeekAvailabilityGrid(AuthenticatedUser manager);

    List<WorkAvailabilityResponse> listStoreNextWeekAvailabilities(AuthenticatedUser manager);
}
