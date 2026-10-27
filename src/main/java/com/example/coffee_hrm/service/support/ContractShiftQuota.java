package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.EmploymentContract;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Số ca/tuần lấy từ hợp đồng đang bao trùm tuần đăng ký.
 * Tuần là thứ Hai đến Chủ nhật, cùng quy ước lưới đăng ký ca.
 * Tuần hợp đồng không trọn vẹn không bị quy đổi số ca.
 */
public final class ContractShiftQuota {

    public static final int MAX_PER_WEEK = 21;

    public static final String NO_CONTRACT =
            "Bạn chưa có hợp đồng hiệu lực cho tuần đăng ký. Vui lòng liên hệ Manager.";
    public static final String NO_QUOTA =
            "Hợp đồng hiện tại chưa có số ca làm việc quy định. Vui lòng liên hệ Manager.";
    public static final String PARTIAL_WEEK =
            "Hợp đồng không bao trùm trọn tuần đăng ký. Hệ thống chưa có quy tắc tính số ca cho tuần không trọn vẹn, nên chưa thể gửi đăng ký. Vui lòng liên hệ Manager.";
    public static final String AMBIGUOUS =
            "Tuần đăng ký đang nằm trong nhiều hợp đồng có số ca khác nhau. Vui lòng liên hệ Manager.";

    private ContractShiftQuota() {
    }

    public record WeekQuota(Integer required, String blockMessage) {
        public boolean blocked() {
            return blockMessage != null;
        }
    }

    public static WeekQuota describe(List<EmploymentContract> contracts, LocalDate dayInWeek) {
        if (dayInWeek == null) {
            return new WeekQuota(null, NO_CONTRACT);
        }
        LocalDate weekStart = dayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        EmploymentContract contract = covering(contracts, weekStart, weekEnd);
        if (contract == null) {
            return new WeekQuota(null, cutsWeek(contracts, weekStart, weekEnd) ? PARTIAL_WEEK : NO_CONTRACT);
        }
        if (contract.getWeeklyShiftCount() == null || contract.getWeeklyShiftCount() < 1) {
            return new WeekQuota(null, NO_QUOTA);
        }
        return new WeekQuota(contract.getWeeklyShiftCount(), null);
    }

    public static void requireExact(List<EmploymentContract> contracts,
                                    LocalDate from,
                                    LocalDate to,
                                    Map<LocalDate, Integer> shiftsByWeek) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException("Thời hạn đăng ký không hợp lệ.");
        }
        LocalDate week = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!week.isAfter(last)) {
            LocalDate weekEnd = week.plusDays(6);
            boolean registrationCoversWeek = !from.isAfter(week) && !to.isBefore(weekEnd);
            WeekQuota quota = describe(contracts, week);
            if (quota.blocked()) {
                throw new BusinessException(quota.blockMessage());
            }
            if (registrationCoversWeek) {
                int selected = shiftsByWeek == null ? 0 : shiftsByWeek.getOrDefault(week, 0);
                String mismatch = mismatch(quota.required(), selected);
                if (mismatch != null) {
                    throw new BusinessException(mismatch);
                }
            }
            week = week.plusWeeks(1);
        }
    }

    public static String mismatch(int required, int selected) {
        if (selected < required) {
            int missing = required - selected;
            return "Bạn cần đăng ký đủ " + required
                    + " ca/tuần theo hợp đồng. Hiện tại bạn mới chọn "
                    + selected + " ca, còn thiếu " + missing + " ca.";
        }
        if (selected > required) {
            return "Số ca đăng ký vượt quy định hợp đồng. Bạn chỉ được đăng ký tối đa "
                    + required + " ca/tuần.";
        }
        return null;
    }

    public static String readyLabel(int required) {
        return "Đã đăng ký đủ " + required + "/" + required + " ca.";
    }

    private static EmploymentContract covering(List<EmploymentContract> contracts, LocalDate start, LocalDate end) {
        EmploymentContract found = null;
        if (contracts == null) {
            return null;
        }
        for (EmploymentContract contract : contracts) {
            if (!fullyCovers(contract, start, end)) {
                continue;
            }
            if (found != null && !Objects.equals(found.getWeeklyShiftCount(), contract.getWeeklyShiftCount())) {
                throw new BusinessException(AMBIGUOUS);
            }
            found = contract;
        }
        return found;
    }

    private static boolean fullyCovers(EmploymentContract contract, LocalDate start, LocalDate end) {
        if (contract == null || contract.getStartDate() == null || contract.getStartDate().isAfter(start)) {
            return false;
        }
        if (contract.getEndDate() != null && contract.getEndDate().isBefore(end)) {
            return false;
        }
        return contract.getTerminatedDate() == null || contract.getTerminatedDate().isAfter(start);
    }

    private static boolean cutsWeek(List<EmploymentContract> contracts, LocalDate weekStart, LocalDate weekEnd) {
        if (contracts == null) {
            return false;
        }
        for (EmploymentContract contract : contracts) {
            if (contract == null || contract.getStartDate() == null) {
                continue;
            }
            if (contract.getTerminatedDate() != null && !contract.getTerminatedDate().isAfter(weekStart)) {
                continue;
            }
            LocalDate contractEnd = contract.getEndDate() == null ? LocalDate.MAX : contract.getEndDate();
            boolean overlaps = !contract.getStartDate().isAfter(weekEnd) && !contractEnd.isBefore(weekStart);
            boolean full = !contract.getStartDate().isAfter(weekStart) && !contractEnd.isBefore(weekEnd);
            if (overlaps && !full) {
                return true;
            }
        }
        return false;
    }
}
