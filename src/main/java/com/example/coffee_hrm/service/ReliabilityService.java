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
     * Cộng điểm (+5đ) cho nhân viên nhận ca "cứu bồ" đồng nghiệp trong vòng 24h trước giờ làm khi đơn được duyệt.
     */
    void recordRescueShiftOnApproval(ShiftChangeRequest approvedRequest, Employee rescuer);

    /**
     * Tự động cộng điểm (+2đ) khi nhân viên hoàn thành tốt (check-out đúng giờ, không đi muộn/về sớm)
     * một ca nhận đổi hoặc ca thay thế.
     */
    void recordCompletedSwappedShiftOnCheckout(Attendance attendance);

    /**
     * Quản lý xác nhận nhân viên hoàn thành tốt ca đổi (+2đ).
     */
    void confirmSwappedShiftCompletion(Integer requestId, AuthenticatedUser manager);

    /**
     * Quản lý điều chỉnh (thưởng/phạt) điểm tín nhiệm cho nhân viên trong cửa hàng.
     */
    void adjustScoreByManager(Integer employeeId, int pointsDelta, String reason, AuthenticatedUser manager);

    /**
     * Dữ liệu Bảng xếp hạng "Chiến thần cứu ca" cho Quản lý cửa hàng.
     */
    ReliabilityLeaderboardView getLeaderboardForManager(AuthenticatedUser manager, YearMonth month);

    /**
     * Dữ liệu Bảng xếp hạng "Chiến thần cứu ca" & Hồ sơ Điểm uy tín cá nhân cho Nhân viên.
     */
    ReliabilityLeaderboardView getLeaderboardForStaff(AuthenticatedUser staff, YearMonth month);
}
