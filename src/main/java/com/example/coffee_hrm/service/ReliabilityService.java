package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.ReliabilityLeaderboardView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

public interface ReliabilityService {

    int INITIAL_SCORE = 100;
    int RESCUE_SHIFT_BONUS = 5;
    int COMPLETED_SWAP_BONUS = 2;
    int LATE_CANCEL_PENALTY = -10;
    int EXCESSIVE_EMERGENCY_DECLINE_PENALTY = -5;

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
     * Ghi nhận từ chối ca khẩn cấp (<24h): lần 1 trong tháng nhắc nhở (0đ), từ lần 2 trở đi trừ điểm (-5đ).
     */
    void recordEmergencyDeclineIfApplicable(Employee targetEmployee, ShiftChangeRequest request);

    /**
     * Cộng điểm (+5đ) cho nhân viên nhận ca "hỗ trợ ca gấp" đồng nghiệp trong vòng 24h trước giờ làm khi đơn được duyệt.
     */
    void recordRescueShiftOnApproval(ShiftChangeRequest approvedRequest, Employee rescuer);

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
     * Quản lý điều chỉnh (thưởng/phạt) điểm chuyên cần cho nhân viên trong cửa hàng.
     */
    void adjustScoreByManager(Integer employeeId, int pointsDelta, String reason, AuthenticatedUser manager);

    /**
     * Dữ liệu Bảng xếp hạng "Nhân viên xuất sắc" cho Quản lý cửa hàng.
     */
    ReliabilityLeaderboardView getLeaderboardForManager(AuthenticatedUser manager, YearMonth month);

    /**
     * Dữ liệu Bảng xếp hạng "Nhân viên xuất sắc" & Hồ sơ Điểm chuyên cần cá nhân cho Nhân viên.
     */
    ReliabilityLeaderboardView getLeaderboardForStaff(AuthenticatedUser staff, YearMonth month);
}
