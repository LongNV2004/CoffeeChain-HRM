package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Busy;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Candidate;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Group;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Pick;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Result;
import com.example.coffee_hrm.service.support.StaffAllocationPlanner.Slot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffAllocationPlannerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private static final Set<LocalDate> WHOLE_WEEK = week();

    @Test
    void unregisteredStaffAreProposedBeforeStaffWhoAreShortOfShifts() {
        Slot morning = slot(1, "Ca sáng", 7, 12, MONDAY, 1, 0, 0);
        Candidate shortOne = candidate(11, "Bình", Group.SHORT, 3, List.of());
        Candidate fresh = candidate(12, "An", Group.UNREGISTERED, 1, List.of());

        Result result = StaffAllocationPlanner.plan(List.of(morning), List.of(shortOne, fresh), null);

        assertEquals(1, result.picks().size());
        assertEquals(12, result.picks().getFirst().candidate().employeeId());
        assertEquals(0, result.openAfter(morning));
    }

    @Test
    void neverExceedsTheShiftsStillMissingFromTheContract() {
        Slot mon = slot(1, "Ca sáng", 7, 12, MONDAY, 3, 0, 0);
        Slot tue = slot(1, "Ca sáng", 7, 12, MONDAY.plusDays(1), 3, 0, 0);
        Slot wed = slot(1, "Ca sáng", 7, 12, MONDAY.plusDays(2), 3, 0, 0);
        Candidate twoMissing = candidate(11, "Bình", Group.SHORT, 2, List.of());

        Result result = StaffAllocationPlanner.plan(List.of(mon, tue, wed), List.of(twoMissing), null);

        assertEquals(2, result.picks().size());
        assertEquals(0, result.remainingAfterByEmployee().get(11));
        // Không đủ người: còn 7 chỗ trống sau đề xuất.
        assertEquals(7, result.openAfter(mon) + result.openAfter(tue) + result.openAfter(wed));
    }

    @Test
    void doesNotFillAShiftThatIsAlreadyFullIncludingPendingRegistrations() {
        Slot full = slot(1, "Ca sáng", 7, 12, MONDAY, 2, 1, 1);
        Candidate fresh = candidate(12, "An", Group.UNREGISTERED, 5, List.of());

        Result result = StaffAllocationPlanner.plan(List.of(full), List.of(fresh), null);

        assertTrue(result.picks().isEmpty());
        assertEquals(0, full.openSeats());
    }

    @Test
    void skipsShiftsThatOverlapTheEmployeesExistingSchedule() {
        Slot evening = slot(1, "Ca tối", 17, 22, MONDAY, 1, 0, 0);
        Slot lateAfternoon = slot(2, "Ca chiều muộn", 15, 19, MONDAY, 1, 0, 0);
        Candidate busy = candidate(11, "Bình", Group.SHORT, 3,
                List.of(new Busy(9, MONDAY, LocalTime.of(16, 0), LocalTime.of(18, 0))));

        Result result = StaffAllocationPlanner.plan(List.of(evening, lateAfternoon), List.of(busy), null);

        assertTrue(result.picks().isEmpty(), "Cả hai ca đều trùng giờ 16:00–18:00 đã có");
        assertEquals(2, result.openAfter(evening) + result.openAfter(lateAfternoon));
    }

    @Test
    void keepsAtMostTwoShiftsPerDayAndRespectsApprovedLeave() {
        Slot morning = slot(1, "Ca sáng", 6, 10, MONDAY, 1, 0, 0);
        Slot noon = slot(2, "Ca trưa", 10, 14, MONDAY, 1, 0, 0);
        Slot evening = slot(3, "Ca tối", 18, 22, MONDAY, 1, 0, 0);
        Slot tuesday = slot(1, "Ca sáng", 6, 10, MONDAY.plusDays(1), 1, 0, 0);
        Candidate staff = candidate(11, "Bình", Group.UNREGISTERED, 5, List.of());

        Result result = StaffAllocationPlanner.plan(List.of(morning, noon, evening, tuesday), List.of(staff),
                (employeeId, shiftId, date) -> date.equals(MONDAY.plusDays(1)));

        Map<LocalDate, Long> perDay = result.picks().stream()
                .collect(Collectors.groupingBy(pick -> pick.slot().date(), Collectors.counting()));
        assertEquals(2L, perDay.get(MONDAY));
        assertNull(perDay.get(MONDAY.plusDays(1)), "Đã được duyệt nghỉ thứ Ba");
    }

    @Test
    void trainingSessionsBlockOverlappingShiftsButDoNotCountAsShifts() {
        Slot morning = slot(1, "Ca sáng", 7, 12, MONDAY, 1, 0, 0);
        Slot noon = slot(2, "Ca trưa", 12, 16, MONDAY, 1, 0, 0);
        Slot evening = slot(3, "Ca tối", 17, 22, MONDAY, 1, 0, 0);
        // Học 08:00–11:00 thứ Hai: chặn ca sáng, vẫn nhận được 2 ca khác trong ngày.
        Candidate learner = candidate(11, "An", Group.UNREGISTERED, 3,
                List.of(new Busy(null, MONDAY, LocalTime.of(8, 0), LocalTime.of(11, 0))));

        Result result = StaffAllocationPlanner.plan(List.of(morning, noon, evening), List.of(learner), null);

        assertEquals(2, result.picks().size());
        assertTrue(result.picks().stream().noneMatch(pick -> pick.slot().shiftId() == 1));
        assertEquals("trùng giờ lớp đào tạo", StaffAllocationPlanner.validate(
                List.of(new Pick(morning, learner)), List.of(morning), List.of(learner), null).reason());
    }

    @Test
    void doesNotAllocateOnDaysOutsideTheContract() {
        Slot friday = slot(1, "Ca sáng", 7, 12, MONDAY.plusDays(4), 1, 0, 0);
        Candidate partial = new Candidate(11, "Bình", Group.UNREGISTERED, 5,
                Set.of(MONDAY, MONDAY.plusDays(1)), List.of());

        assertTrue(StaffAllocationPlanner.plan(List.of(friday), List.of(partial), null).picks().isEmpty());
    }

    @Test
    void givesEveryShortShiftSomeoneBeforeStackingOneShift() {
        Slot busyShift = slot(1, "Ca sáng", 7, 12, MONDAY, 3, 0, 0);
        Slot quietShift = slot(2, "Ca tối", 17, 22, MONDAY.plusDays(1), 1, 0, 0);
        Candidate a = candidate(11, "An", Group.UNREGISTERED, 1, List.of());
        Candidate b = candidate(12, "Bình", Group.UNREGISTERED, 1, List.of());

        Result result = StaffAllocationPlanner.plan(List.of(busyShift, quietShift), List.of(a, b), null);

        assertEquals(2, result.picks().size());
        assertEquals(2, result.openAfter(busyShift));
        assertEquals(0, result.openAfter(quietShift));
        // Manager có thể đổi sang bất kỳ nhân viên phù hợp nào của ca.
        assertEquals(List.of(11, 12), result.alternatives(busyShift).stream().map(Candidate::employeeId).toList());
    }

    @Test
    void validationRejectsSelectionsThatNoLongerFitTheLatestSchedule() {
        Slot morning = slot(1, "Ca sáng", 7, 12, MONDAY, 1, 0, 0);
        Candidate a = candidate(11, "An", Group.UNREGISTERED, 2, List.of());
        Candidate b = candidate(12, "Bình", Group.UNREGISTERED, 2, List.of());

        assertNull(StaffAllocationPlanner.validate(List.of(new Pick(morning, a)), List.of(morning), List.of(a, b), null));

        // Hai lựa chọn cùng lấy chỗ cuối của một ca.
        StaffAllocationPlanner.Rejection full = StaffAllocationPlanner.validate(
                List.of(new Pick(morning, a), new Pick(morning, b)), List.of(morning), List.of(a, b), null);
        assertNotNull(full);
        assertEquals("ca đã đủ nhân sự", full.reason());

        // Cùng nhân viên bị chọn hai lần vào một ca.
        Slot wide = slot(1, "Ca sáng", 7, 12, MONDAY, 3, 0, 0);
        StaffAllocationPlanner.Rejection twice = StaffAllocationPlanner.validate(
                List.of(new Pick(wide, a), new Pick(wide, a)), List.of(wide), List.of(a), null);
        assertNotNull(twice);
        assertEquals("đã có ca này trong ngày", twice.reason());
    }

    private static Slot slot(int shiftId, String name, int from, int to, LocalDate date, int max, int approved, int pending) {
        return new Slot(shiftId, name, LocalTime.of(from, 0), LocalTime.of(to, 0), date,
                date.getDayOfWeek().getValue(), max, approved, pending);
    }

    private static Candidate candidate(int id, String name, Group group, int remaining, List<Busy> busy) {
        return new Candidate(id, name, group, remaining, WHOLE_WEEK, busy);
    }

    private static Set<LocalDate> week() {
        Set<LocalDate> days = new LinkedHashSet<>();
        for (int i = 0; i < 7; i++) {
            days.add(MONDAY.plusDays(i));
        }
        return days;
    }
}
