package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.entity.LeaveRequest;
import com.example.coffee_hrm.entity.LeaveRequestSlot;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.LeaveRequestSlotRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A slot leave blocks only the selected shift. A legacy request with no slot rows still covers the whole date range.
 */
@Component
@RequiredArgsConstructor
public class ApprovedLeaveCoverage {
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestSlotRepository leaveRequestSlotRepository;

    public boolean blocksShift(Integer employeeId, Integer shiftId, LocalDate workDate) {
        if (employeeId == null || shiftId == null || workDate == null) {
            return false;
        }
        if (leaveRequestSlotRepository.existsForShift(employeeId, shiftId, workDate, ApprovalStatus.APPROVED)) {
            return true;
        }
        return leaveRequestRepository.existsApprovedWithoutSlots(employeeId, ApprovalStatus.APPROVED, workDate);
    }

    public WeekIndex index(Integer storeId, LocalDate fromDate, LocalDate toDate) {
        List<LeaveRequestSlot> slots = leaveRequestSlotRepository.findApprovedInStore(
                storeId, ApprovalStatus.APPROVED, fromDate, toDate);
        Set<Integer> requests = new HashSet<>();
        Set<String> keys = new HashSet<>();
        if (slots != null) {
            for (LeaveRequestSlot slot : slots) {
                if (slot.getLeaveRequest() != null && slot.getLeaveRequest().getId() != null) {
                    requests.add(slot.getLeaveRequest().getId());
                }
                if (slot.getAssignment() != null && slot.getAssignment().getEmployee() != null
                        && slot.getAssignment().getShift() != null && slot.getAssignment().getWorkDate() != null) {
                    keys.add(key(slot.getAssignment().getEmployee().getId(),
                            slot.getAssignment().getShift().getId(), slot.getAssignment().getWorkDate()));
                }
            }
        }
        return new WeekIndex(requests, keys);
    }

    public record WeekIndex(Set<Integer> requestIdsWithSlots, Set<String> shiftKeys) {
        public boolean blocks(LeaveRequest leave, Integer employeeId, Integer shiftId, LocalDate date) {
            if (leave == null || date == null || leave.getStartDate() == null || leave.getEndDate() == null) {
                return false;
            }
            if (date.isBefore(leave.getStartDate()) || date.isAfter(leave.getEndDate())) {
                return false;
            }
            if (leave.getId() == null || !requestIdsWithSlots.contains(leave.getId())) {
                return true;
            }
            return shiftKeys.contains(key(employeeId, shiftId, date));
        }
    }

    private static String key(Integer employeeId, Integer shiftId, LocalDate date) {
        return employeeId + "_" + shiftId + "_" + date;
    }
}
