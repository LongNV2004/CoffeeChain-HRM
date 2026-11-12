package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.entity.OvertimeRequest;
import com.example.coffee_hrm.entity.ShiftAssignment;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nhận diện ca tăng ca trong kỳ. Khi đơn tăng ca được duyệt, hệ thống tạo một ca phân công đã công bố
 * như ca thường; ca đó vì vậy đã có sẵn trong chấm công và bảng lương. Lớp này chỉ đánh dấu ca nào là
 * tăng ca — không tạo thêm ca hay tiền lương — nên tổng lương không bị tính trùng.
 *
 * <p>Dùng chung cho màn chấm công tháng và bảng lương để hai nơi luôn ra cùng số ca tăng ca.</p>
 */
public final class OvertimeShifts {

    private OvertimeShifts() {
    }

    /**
     * Id các ca phân công sinh từ đơn tăng ca đã duyệt.
     *
     * @param approved    đơn tăng ca đã duyệt trong kỳ
     * @param assignments ca phân công đã công bố trong kỳ; dùng để khớp đơn cũ chưa lưu {@code CreatedAssignmentId}
     *                    theo nhân viên + ca + ngày. Có thể {@code null} nếu mọi đơn đều đã có id ca.
     */
    public static Set<Integer> assignmentIds(List<OvertimeRequest> approved, List<ShiftAssignment> assignments) {
        Set<Integer> ids = new HashSet<>();
        if (approved == null || approved.isEmpty()) {
            return ids;
        }
        Set<String> keys = new HashSet<>();
        for (OvertimeRequest request : approved) {
            if (request.getCreatedAssignmentId() != null) {
                ids.add(request.getCreatedAssignmentId());
            } else if (request.getEmployee() != null && request.getShift() != null) {
                String key = key(request.getEmployee().getId(), request.getShift().getId(), request.getWorkDate());
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        if (!keys.isEmpty() && assignments != null) {
            for (ShiftAssignment assignment : assignments) {
                if (assignment.getId() == null || assignment.getEmployee() == null || assignment.getShift() == null) {
                    continue;
                }
                String key = key(assignment.getEmployee().getId(), assignment.getShift().getId(), assignment.getWorkDate());
                if (key != null && keys.contains(key)) {
                    ids.add(assignment.getId());
                }
            }
        }
        return ids;
    }

    /** Có đơn tăng ca đã duyệt nào chưa lưu id ca phân công (cần danh sách ca để khớp) hay không. */
    public static boolean needsAssignmentLookup(List<OvertimeRequest> approved) {
        return approved != null && approved.stream().anyMatch(request -> request.getCreatedAssignmentId() == null);
    }

    public static String key(Integer employeeId, Integer shiftId, LocalDate workDate) {
        if (employeeId == null || shiftId == null || workDate == null) {
            return null;
        }
        return employeeId + ":" + shiftId + ":" + workDate;
    }
}
