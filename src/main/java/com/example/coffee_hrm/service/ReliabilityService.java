package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.ReliabilityLeaderboardView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.entity.ShiftCoverInvitation;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Map;

public interface ReliabilityService {

    int INITIAL_SCORE = 100;
    int RESCUE_SHIFT_BONUS = 5;
    int COMPLETED_SWAP_BONUS = 2;
    int LATE_CANCEL_PENALTY = -10;

    /** Từ chối / để hết hạn lời mời hỗ trợ ca gấp của Quản lý: 2 lần đầu mỗi tháng miễn trừ, từ lần 3 trừ 3đ. */
    int FREE_URGENT_DECLINES_PER_MONTH = 2;
    int URGENT_DECLINE_PENALTY = -3;

    /** Nhận hỗ trợ ca gấp rồi vắng mặt: thu hồi +5 và trừ thêm 15đ. */
    int ABANDONED_ACCEPTED_SHIFT_PENALTY = -15;

    /** Điều kiện xét danh hiệu Nhân viên xuất sắc tháng. */
    int MIN_SHIFTS_FOR_AWARD = 8;
    int MIN_SCORE_FOR_AWARD = 105;

    /** Hạn mức điều chỉnh thủ công mỗi nhân viên mỗi tháng (tổng cộng tối đa +15, tổng trừ tối đa -15). */
    int MANUAL_ADJUST_MONTHLY_CAP = 15;

    /** Đi muộn / về sớm: ≤30 phút -2đ, 31–60 phút -4đ, trên 60 phút -6đ. */
    int PUNCTUALITY_MINOR_PENALTY = -2;
    int PUNCTUALITY_MODERATE_PENALTY = -4;
    int PUNCTUALITY_SEVERE_PENALTY = -6;
    /** Về sớm dưới số phút này không bị trừ điểm (đi muộn đã có ngưỡng riêng của chấm công). */
    int EARLY_LEAVE_GRACE_MINUTES = 5;

    /**
     * Kiểm tra ca làm việc có nằm trong vòng 24 giờ trước giờ bắt đầu hay không.
     */
    boolean isWithin24Hours(LocalDate workDate, LocalTime startTime, LocalDateTime referenceTime);

    /**
     * Trừ điểm (-10đ) khi nhân viên xin hủy/đổi ca sát giờ (trong vòng 24h trước giờ làm).
     */
    void recordLateShiftChangeIfUrgent(Employee requester, ShiftAssignment assignment, Integer requestId);

    /**
     * Ghi nhận nhân viên từ chối hoặc để hết hạn <b>lời mời hỗ trợ ca gấp của Quản lý</b>.
     * Chỉ áp dụng cho lời mời gấp (&lt;24h): 2 lần đầu trong tháng miễn trừ (0đ), từ lần 3 trừ 3đ.
     * Lời nhờ đổi ca/chuyển ca giữa hai nhân viên KHÔNG đi qua hàm này và không bao giờ bị trừ điểm.
     *
     * @return số lần từ chối gấp trong tháng sau khi ghi nhận (dùng để hiển thị cảnh báo)
     */
    int recordInvitationDecline(ShiftCoverInvitation invitation, boolean expired);

    /** Số lần đã từ chối/hết hạn lời mời gấp của Quản lý trong tháng hiện tại. */
    int countUrgentDeclinesThisMonth(Integer employeeId);

    /**
     * Cộng điểm (+5đ) cho nhân viên <b>tự nguyện</b> nhận ca gấp (xung phong, đồng ý lời mời, hoặc đồng ý lời nhờ chuyển ca)
     * trong vòng 24h trước giờ làm khi đơn được duyệt. Không áp dụng cho đổi chéo hoặc người do Quản lý tự chỉ định.
     */
    void recordRescueShiftOnApproval(ShiftChangeRequest approvedRequest, Employee rescuer);

    /**
     * Khi nhân viên bị ghi vắng mặt ở ca mà họ đã nhận hỗ trợ gấp (+5đ): thu hồi +5 và trừ thêm 15đ.
     */
    void revokeRescueOnAbsence(Attendance absentAttendance);

    /**
     * Tự động cộng điểm (+2đ) khi nhân viên hoàn thành tốt (check-out đúng giờ, không đi muộn/về sớm)
     * một ca nhận đổi hoặc ca thay thế.
     */
    void recordCompletedSwappedShiftOnCheckout(Attendance attendance);

    /**
     * Trừ điểm chuyên cần khi check-out nếu ca đó đi muộn và/hoặc về sớm (theo bậc số phút).
     * Mỗi bản ghi chấm công chỉ bị trừ tối đa 1 lần cho mỗi loại vi phạm.
     */
    void recordPunctualityPenaltyOnCheckout(Attendance attendance);

    /**
     * Quản lý xác nhận nhân viên hoàn thành tốt ca đổi (+2đ).
     */
    void confirmSwappedShiftCompletion(Integer requestId, AuthenticatedUser manager);

    /**
     * Quản lý điều chỉnh điểm chuyên cần theo danh mục ({@link com.example.coffee_hrm.common.enums.ManualAdjustCategory}),
     * bắt buộc lý do, tối đa +15 / -15 điểm thủ công mỗi nhân viên mỗi tháng.
     */
    void adjustScoreByManager(Integer employeeId, String category, String reason, AuthenticatedUser manager);

    /**
     * Chốt danh hiệu tháng đã kết thúc. Hệ thống đề xuất Top 3 đủ điều kiện; Quản lý có thể đổi người Hạng 1
     * (bắt buộc ghi lý do). Sau khi chốt không chốt lại được.
     */
    void closeMonth(AuthenticatedUser manager, YearMonth month, Integer firstPlaceEmployeeId, String overrideReason);

    /**
     * Dữ liệu Bảng xếp hạng "Nhân viên xuất sắc" cho Quản lý cửa hàng.
     */
    ReliabilityLeaderboardView getLeaderboardForManager(AuthenticatedUser manager, YearMonth month);

    /**
     * Dữ liệu Bảng xếp hạng "Nhân viên xuất sắc" & Hồ sơ Điểm chuyên cần cá nhân cho Nhân viên.
     */
    ReliabilityLeaderboardView getLeaderboardForStaff(AuthenticatedUser staff, YearMonth month);

    /**
     * Điểm chuyên cần tháng hiện tại của nhân viên trong cửa hàng (100đ + biến động trong tháng).
     * Nhân viên chưa có biến động nào trong tháng không có trong map (mặc định 100đ).
     */
    Map<Integer, Integer> getCurrentMonthScores(Integer storeId);
}
