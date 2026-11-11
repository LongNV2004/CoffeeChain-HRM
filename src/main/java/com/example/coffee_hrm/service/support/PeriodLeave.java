package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.LeaveType;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.ShiftAssignment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ca nghỉ phép đã duyệt trong một kỳ, dùng chung cho báo cáo chấm công, danh sách chấm công tháng và bảng lương
 * để ba nơi luôn cùng số liệu.
 * <p>
 * Khi Manager duyệt đơn, ca được chuyển CANCELLED và gắn đơn nghỉ, nên ca nghỉ không còn nằm trong ca được
 * phân công. Để chắc chắn không tính trùng, ca nghỉ bị bỏ qua nếu cùng nhân viên, cùng ca, cùng ngày vẫn còn
 * ca được phân công hoặc đã có bản ghi chấm công.
 * <p>
 * Chính sách lương giữ nguyên nghiệp vụ nghỉ phép hiện có: chỉ loại nghỉ trừ quỹ phép năm
 * ({@link LeaveType#isConsumesAnnualLeave()}) được hưởng lương theo giờ của ca; nghỉ không lương, nghỉ ốm và
 * nghỉ việc riêng được ghi nhận nhưng không phát sinh lương.
 */
public final class PeriodLeave {

    private PeriodLeave() {
    }

    public record Slot(
            Integer assignmentId,
            Integer employeeId,
            String employeeName,
            Integer shiftId,
            String shiftName,
            LocalDate workDate,
            LocalTime start,
            LocalTime end,
            Integer leaveRequestId,
            LeaveType leaveType) {

        public boolean paid() {
            return leaveType.isConsumesAnnualLeave();
        }

        public String label() {
            return leaveType.getLabel();
        }
    }

    /** Tổng nghỉ phép của một nhân viên trong kỳ. */
    public record Summary(int slots, int days, int paidSlots) {
        public static final Summary NONE = new Summary(0, 0, 0);

        public int unpaidSlots() {
            return slots - paidSlots;
        }

        public String label() {
            if (slots <= 0) {
                return "0";
            }
            return slots + " ca / " + days + " ngày";
        }
    }

    public static List<Slot> collect(List<ShiftAssignment> leaveCancelled,
                                     Collection<ShiftAssignment> assigned,
                                     Collection<Attendance> records) {
        if (leaveCancelled == null || leaveCancelled.isEmpty()) {
            return List.of();
        }
        Set<String> worked = new HashSet<>();
        if (assigned != null) {
            for (ShiftAssignment assignment : assigned) {
                if (assignment.getEmployee() != null && assignment.getShift() != null) {
                    worked.add(key(assignment.getEmployee().getId(), assignment.getShift().getId(), assignment.getWorkDate()));
                }
            }
        }
        if (records != null) {
            for (Attendance attendance : records) {
                if (attendance.getEmployee() != null && attendance.getShift() != null) {
                    worked.add(key(attendance.getEmployee().getId(), attendance.getShift().getId(), attendance.getWorkDate()));
                }
            }
        }
        Set<String> seen = new HashSet<>();
        List<Slot> slots = new ArrayList<>();
        for (ShiftAssignment assignment : leaveCancelled) {
            if (assignment.getEmployee() == null || assignment.getEmployee().getId() == null
                    || assignment.getShift() == null || assignment.getShift().getId() == null
                    || assignment.getWorkDate() == null || assignment.getCancelledByLeaveRequest() == null) {
                continue;
            }
            String key = key(assignment.getEmployee().getId(), assignment.getShift().getId(), assignment.getWorkDate());
            if (worked.contains(key) || !seen.add(key)) {
                continue;
            }
            LeaveType type = assignment.getCancelledByLeaveRequest().getLeaveType() == null
                    ? LeaveType.ANNUAL
                    : assignment.getCancelledByLeaveRequest().getLeaveType();
            slots.add(new Slot(
                    assignment.getId(),
                    assignment.getEmployee().getId(),
                    assignment.getEmployee().getFullName(),
                    assignment.getShift().getId(),
                    assignment.getShift().getShiftName(),
                    assignment.getWorkDate(),
                    assignment.getShift().getStartTime(),
                    assignment.getShift().getEndTime(),
                    assignment.getCancelledByLeaveRequest().getId(),
                    type));
        }
        return List.copyOf(slots);
    }

    public static Map<Integer, Summary> byEmployee(List<Slot> slots) {
        Map<Integer, int[]> counts = new LinkedHashMap<>();
        Map<Integer, Set<LocalDate>> days = new LinkedHashMap<>();
        if (slots != null) {
            for (Slot slot : slots) {
                int[] count = counts.computeIfAbsent(slot.employeeId(), ignored -> new int[2]);
                count[0]++;
                if (slot.paid()) {
                    count[1]++;
                }
                days.computeIfAbsent(slot.employeeId(), ignored -> new HashSet<>()).add(slot.workDate());
            }
        }
        Map<Integer, Summary> summaries = new LinkedHashMap<>();
        for (Map.Entry<Integer, int[]> entry : counts.entrySet()) {
            summaries.put(entry.getKey(), new Summary(
                    entry.getValue()[0], days.get(entry.getKey()).size(), entry.getValue()[1]));
        }
        return summaries;
    }

    /** Nhãn trạng thái ca nghỉ trên bảng lương; dùng để nhận lại ca nghỉ trong bảng lương đã lưu. */
    public static boolean isLeaveLabel(String label) {
        if (label == null) {
            return false;
        }
        for (LeaveType type : LeaveType.values()) {
            if (type.getLabel().equals(label)) {
                return true;
            }
        }
        return false;
    }

    private static String key(Integer employeeId, Integer shiftId, LocalDate date) {
        return employeeId + ":" + shiftId + ":" + date;
    }
}
