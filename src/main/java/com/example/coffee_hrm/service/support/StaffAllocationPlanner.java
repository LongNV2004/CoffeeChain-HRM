package com.example.coffee_hrm.service.support;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Đề xuất lấp ca thiếu người trong một tuần. Không đọc hay ghi dữ liệu.
 * <p>
 * Quy tắc cho từng lượt phân bổ:
 * <ul>
 *     <li>ca còn chỗ trống theo số nhân viên tối đa (đã trừ đơn chờ duyệt và đã duyệt);</li>
 *     <li>nhân viên còn thiếu ca so với hợp đồng và hợp đồng bao trùm ngày làm;</li>
 *     <li>không trùng ca hoặc trùng giờ với lịch đã có hay đề xuất khác;</li>
 *     <li>không quá {@link #MAX_SHIFTS_PER_DAY} ca/ngày, cùng quy tắc đăng ký ca;</li>
 *     <li>không rơi vào ca đã được duyệt nghỉ.</li>
 * </ul>
 * Ưu tiên nhân viên chưa đăng ký ca nào, sau đó nhân viên còn thiếu ca.
 * Ca có tỉ lệ đã có người thấp nhất được lấp trước để chia đều nhân sự giữa các ca.
 */
public final class StaffAllocationPlanner {

    public static final int MAX_SHIFTS_PER_DAY = 2;

    public enum Group {
        /** Có hợp đồng hợp lệ, chưa đăng ký ca nào trong tuần. */
        UNREGISTERED,
        /** Có hợp đồng hợp lệ, đã đăng ký ít hơn số ca theo hợp đồng. */
        SHORT
    }

    /** Một ô ca – ngày có cấu hình số nhân viên tối đa và chưa bắt đầu. */
    public record Slot(Integer shiftId, String shiftName, LocalTime start, LocalTime end,
                       LocalDate date, int dayOfWeek, int maxEmployees, int approved, int pending) {
        public int openSeats() {
            return Math.max(0, maxEmployees - approved - pending);
        }

        public String key() {
            return shiftId + ":" + date;
        }
    }

    /**
     * Khoảng thời gian nhân viên đã bận trong tuần: ca (đơn chờ duyệt, đã duyệt, ca đã phân)
     * hoặc buổi học đào tạo ({@code shiftId = null}). Buổi học chỉ chặn trùng giờ, không tính vào số ca/ngày.
     */
    public record Busy(Integer shiftId, LocalDate date, LocalTime start, LocalTime end) {
        public boolean isShift() {
            return shiftId != null;
        }
    }

    public record Candidate(Integer employeeId, String fullName, Group group, int remaining,
                            Set<LocalDate> contractDays, List<Busy> busy) {
    }

    /** Kiểm tra nghỉ đã duyệt; triển khai thật đọc dữ liệu nghỉ phép. */
    @FunctionalInterface
    public interface LeaveCheck {
        boolean blocks(Integer employeeId, Integer shiftId, LocalDate date);
    }

    public record Pick(Slot slot, Candidate candidate) {
    }

    public record Result(List<Pick> picks,
                         Map<String, List<Candidate>> alternativesBySlot,
                         Map<String, Integer> openAfterBySlot,
                         Map<Integer, Integer> remainingAfterByEmployee) {
        public List<Candidate> alternatives(Slot slot) {
            return alternativesBySlot.getOrDefault(slot.key(), List.of());
        }

        public int openAfter(Slot slot) {
            return openAfterBySlot.getOrDefault(slot.key(), slot.openSeats());
        }
    }

    private StaffAllocationPlanner() {
    }

    public static Result plan(List<Slot> slots, List<Candidate> candidates, LeaveCheck leaveCheck) {
        State state = new State(slots, candidates, leaveCheck);
        Map<String, List<Candidate>> alternatives = new LinkedHashMap<>();
        for (Slot slot : slots) {
            if (slot.openSeats() == 0) {
                continue;
            }
            List<Candidate> feasible = new ArrayList<>();
            for (Candidate candidate : candidates) {
                if (state.blocker(candidate, slot) == null) {
                    feasible.add(candidate);
                }
            }
            feasible.sort(state.ranking(slot));
            alternatives.put(slot.key(), List.copyOf(feasible));
        }

        List<Pick> picks = new ArrayList<>();
        Set<String> exhausted = new LinkedHashSet<>();
        while (true) {
            Slot next = null;
            for (Slot slot : slots) {
                if (exhausted.contains(slot.key()) || state.open(slot) == 0) {
                    continue;
                }
                if (next == null || state.morePressing(slot, next, candidates)) {
                    next = slot;
                }
            }
            if (next == null) {
                break;
            }
            Slot target = next;
            Candidate best = candidates.stream()
                    .filter(candidate -> state.blocker(candidate, target) == null)
                    .min(state.ranking(target))
                    .orElse(null);
            if (best == null) {
                exhausted.add(target.key());
                continue;
            }
            state.apply(best, target);
            picks.add(new Pick(target, best));
        }
        picks.sort(Comparator.comparing((Pick pick) -> pick.slot().date())
                .thenComparing(pick -> pick.slot().start())
                .thenComparing(pick -> pick.candidate().fullName(), Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, Integer> openAfter = new HashMap<>();
        for (Slot slot : slots) {
            openAfter.put(slot.key(), state.open(slot));
        }
        Map<Integer, Integer> remainingAfter = new HashMap<>();
        for (Candidate candidate : candidates) {
            remainingAfter.put(candidate.employeeId(), state.remaining(candidate));
        }
        return new Result(picks, alternatives, openAfter, remainingAfter);
    }

    /**
     * Áp lần lượt các lựa chọn Manager đã xác nhận lên trạng thái mới nhất.
     * Lựa chọn đầu tiên vi phạm quy tắc được trả về kèm lý do; {@code null} khi tất cả hợp lệ.
     */
    public static Rejection validate(List<Pick> requested, List<Slot> slots, List<Candidate> candidates,
                                     LeaveCheck leaveCheck) {
        State state = new State(slots, candidates, leaveCheck);
        for (Pick pick : requested) {
            String blocker = state.blocker(pick.candidate(), pick.slot());
            if (blocker != null) {
                return new Rejection(pick, blocker);
            }
            state.apply(pick.candidate(), pick.slot());
        }
        return null;
    }

    public record Rejection(Pick pick, String reason) {
    }

    private static boolean earlier(Slot a, Slot b) {
        int byDate = a.date().compareTo(b.date());
        if (byDate != 0) {
            return byDate < 0;
        }
        return a.start().isBefore(b.start());
    }

    private static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private static final class State {
        private final Map<String, Integer> open = new HashMap<>();
        private final Map<Integer, Integer> remaining = new HashMap<>();
        private final Map<Integer, List<Busy>> busy = new HashMap<>();
        private final Map<Integer, Integer> proposed = new HashMap<>();
        private final Map<String, Boolean> leaveCache = new HashMap<>();
        private final LeaveCheck leaveCheck;

        State(List<Slot> slots, List<Candidate> candidates, LeaveCheck leaveCheck) {
            this.leaveCheck = leaveCheck;
            for (Slot slot : slots) {
                open.put(slot.key(), slot.openSeats());
            }
            for (Candidate candidate : candidates) {
                remaining.put(candidate.employeeId(), Math.max(0, candidate.remaining()));
                busy.put(candidate.employeeId(), new ArrayList<>(
                        candidate.busy() == null ? List.of() : candidate.busy()));
            }
        }

        int open(Slot slot) {
            return open.getOrDefault(slot.key(), 0);
        }

        /**
         * Ca có tỉ lệ đã có người thấp hơn được lấp trước, để mỗi ca thiếu đều có người trước khi dồn thêm vào một ca.
         * Bằng nhau thì ca có ít nhân viên phù hợp hơn đi trước, rồi tới ca sớm hơn.
         */
        boolean morePressing(Slot a, Slot b, List<Candidate> candidates) {
            long staffedA = (long) (a.maxEmployees() - open(a)) * b.maxEmployees();
            long staffedB = (long) (b.maxEmployees() - open(b)) * a.maxEmployees();
            if (staffedA != staffedB) {
                return staffedA < staffedB;
            }
            long feasibleA = candidates.stream().filter(candidate -> blocker(candidate, a) == null).count();
            long feasibleB = candidates.stream().filter(candidate -> blocker(candidate, b) == null).count();
            if (feasibleA != feasibleB) {
                return feasibleA < feasibleB;
            }
            return earlier(a, b);
        }

        int remaining(Candidate candidate) {
            return remaining.getOrDefault(candidate.employeeId(), 0);
        }

        /** Lý do không xếp được nhân viên vào ca; {@code null} khi hợp lệ. */
        String blocker(Candidate candidate, Slot slot) {
            if (open(slot) <= 0) {
                return "ca đã đủ nhân sự";
            }
            if (remaining(candidate) <= 0) {
                return "đã đủ số ca theo hợp đồng";
            }
            if (candidate.contractDays() == null || !candidate.contractDays().contains(slot.date())) {
                return "hợp đồng không có hiệu lực vào ngày này";
            }
            List<Busy> day = busy.getOrDefault(candidate.employeeId(), List.of()).stream()
                    .filter(item -> slot.date().equals(item.date()))
                    .toList();
            for (Busy item : day) {
                if (Objects.equals(item.shiftId(), slot.shiftId())) {
                    return "đã có ca này trong ngày";
                }
                if (item.start() != null && item.end() != null
                        && overlaps(slot.start(), slot.end(), item.start(), item.end())) {
                    return item.isShift() ? "trùng giờ với ca khác trong ngày" : "trùng giờ lớp đào tạo";
                }
            }
            if (day.stream().filter(Busy::isShift).count() >= MAX_SHIFTS_PER_DAY) {
                return "đã có " + MAX_SHIFTS_PER_DAY + " ca trong ngày";
            }
            if (leaveCheck != null) {
                String key = candidate.employeeId() + ":" + slot.key();
                boolean onLeave = leaveCache.computeIfAbsent(key,
                        ignored -> leaveCheck.blocks(candidate.employeeId(), slot.shiftId(), slot.date()));
                if (onLeave) {
                    return "đã được duyệt nghỉ ca này";
                }
            }
            return null;
        }

        void apply(Candidate candidate, Slot slot) {
            open.merge(slot.key(), -1, Integer::sum);
            remaining.merge(candidate.employeeId(), -1, Integer::sum);
            proposed.merge(candidate.employeeId(), 1, Integer::sum);
            busy.computeIfAbsent(candidate.employeeId(), ignored -> new ArrayList<>())
                    .add(new Busy(slot.shiftId(), slot.date(), slot.start(), slot.end()));
        }

        /**
         * Chưa đăng ký ca trước, rồi người còn thiếu nhiều ca hơn,
         * rồi người có ít ca trong ngày đó hơn để không dồn ca vào một ngày.
         */
        Comparator<Candidate> ranking(Slot slot) {
            return Comparator
                    .comparing((Candidate candidate) -> candidate.group() == Group.UNREGISTERED ? 0 : 1)
                    .thenComparing(candidate -> -remaining(candidate))
                    .thenComparing(candidate -> busy.getOrDefault(candidate.employeeId(), List.of()).stream()
                            .filter(item -> item.isShift() && slot.date().equals(item.date())).count())
                    .thenComparing(candidate -> proposed.getOrDefault(candidate.employeeId(), 0))
                    .thenComparing(Candidate::employeeId);
        }
    }
}
