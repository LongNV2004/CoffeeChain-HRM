package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.UpdateSlotStaffingBatchForm;
import com.example.coffee_hrm.dto.request.UpdateSlotStaffingForm;
import com.example.coffee_hrm.dto.response.SlotConfigTotals;
import com.example.coffee_hrm.dto.response.SlotLimitUpdateResult;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.dto.response.WorkPositionOption;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/availability/manager")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerAvailabilityController {

    private final StaffAvailabilityService staffAvailabilityService;

    @GetMapping
    public String managerAvailabilityPage(@RequestParam(required = false) String week,
                                          @AuthenticationPrincipal AuthenticatedUser user,
                                          Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        try {
            LocalDate weekStart = resolveConfigWeek(week);
            WeeklyAvailabilityView grid = staffAvailabilityService.getStoreWeekStaffingGrid(user, weekStart);
            List<WeeklyAvailabilityView.RegistrationGroup> registrations =
                    grid.getRegistrations() != null ? grid.getRegistrations() : List.of();
            model.addAttribute("availabilityGrid", grid);
            model.addAttribute("registrations", registrations);
            model.addAttribute("activePositions", staffAvailabilityService.listActiveWorkPositions());
            model.addAttribute("reviewTotals", countRegistrations(registrations));
            addWeekNav(model, weekStart);
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            emptyReview(model, ex.getMessage());
        } catch (DateTimeParseException ex) {
            emptyReview(model, "Tuần không hợp lệ.");
        }
        return "manager/ManagerAvailability";
    }

    @GetMapping("/limits")
    public String slotLimitsPage(@RequestParam(required = false) String week,
                                 @AuthenticationPrincipal AuthenticatedUser user,
                                 Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        try {
            LocalDate weekStart = resolveConfigWeek(week);
            WeeklyAvailabilityView grid = staffAvailabilityService.getStoreWeekStaffingGrid(user, weekStart);
            List<WorkPositionOption> positions = staffAvailabilityService.listActiveWorkPositions();
            model.addAttribute("availabilityGrid", grid);
            model.addAttribute("staffSlotCapacity", staffAvailabilityService.countStaffForSlotLimit(user));
            model.addAttribute("activePositions", positions);
            model.addAttribute("eligibleStaff", staffAvailabilityService.countEligibleStaff(user));
            model.addAttribute("eligibleBySkill", staffAvailabilityService.countEligibleStaffBySkill(user));
            model.addAttribute("configTotals", summarize(grid));
            model.addAttribute("weekStart", weekStart);
            model.addAttribute("previousWeek", weekStart.minusWeeks(1));
            model.addAttribute("nextWeek", weekStart.plusWeeks(1));
            model.addAttribute("currentWeek", VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            emptyLimits(model, ex.getMessage());
        } catch (DateTimeParseException ex) {
            emptyLimits(model, "Tuần không hợp lệ.");
        }
        return "manager/ManagerSlotLimits";
    }

    @PostMapping("/limits")
    public String updateLimit(@ModelAttribute UpdateSlotStaffingForm form,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirectAttributes) {
        try {
            boolean withPositions = form.getRequiredCounts() != null && !form.getRequiredCounts().isEmpty();
            SlotLimitUpdateResult result = withPositions
                    ? staffAvailabilityService.updateSlotStaffing(
                    form.getShiftId(), form.getDayOfWeek(), form.getMaxEmployees(), form.getRequiredCounts(), user)
                    : staffAvailabilityService.updateSlotLimit(
                    form.getShiftId(), form.getDayOfWeek(), form.getMaxEmployees(), user);
            if (result.isWarning()) {
                redirectAttributes.addFlashAttribute("warningMessage", result.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("successMessage", result.getMessage());
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/availability/manager/limits";
    }

    @PostMapping("/limits/batch")
    public String updateLimits(@ModelAttribute UpdateSlotStaffingBatchForm form,
                               @AuthenticationPrincipal AuthenticatedUser user,
                               RedirectAttributes redirectAttributes) {
        String week = form.getWeekStart();
        try {
            SlotLimitUpdateResult result = staffAvailabilityService.updateSlotStaffingBatch(form.getSlots(), user);
            redirectAttributes.addFlashAttribute("successMessage", result.getMessage());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        if (week != null && !week.isBlank()) {
            redirectAttributes.addAttribute("week", week);
        }
        return "redirect:/availability/manager/limits";
    }

    @PostMapping("/review")
    public String review(@RequestParam(required = false) String registrationKey,
                         @RequestParam(required = false) Integer availabilityId,
                         @RequestParam boolean approved,
                         @RequestParam(required = false) String week,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirectAttributes) {
        try {
            if (registrationKey != null && !registrationKey.isBlank()) {
                staffAvailabilityService.reviewRegistration(registrationKey, approved, user);
            } else if (availabilityId != null) {
                staffAvailabilityService.reviewAvailability(availabilityId, approved, user);
            } else {
                throw new BusinessException("Không tìm thấy đăng ký cần duyệt.");
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    approved
                            ? "Đã duyệt đơn đăng ký ca của nhân viên này. Các ca trong đơn đã được xếp vào lịch làm việc. Nhân viên đã được thông báo."
                            : "Đã từ chối đơn đăng ký ca của nhân viên này. Nhân viên đã được thông báo và các ca trong đơn không được xếp vào lịch.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        keepWeek(redirectAttributes, week);
        return "redirect:/availability/manager";
    }

    @PostMapping("/review-all")
    public String reviewAll(@RequestParam boolean approved,
                            @RequestParam(required = false) String week,
                            @AuthenticationPrincipal AuthenticatedUser user,
                            RedirectAttributes redirectAttributes) {
        try {
            int reviewed = staffAvailabilityService.reviewAllPendingRegistrations(approved, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    approved
                            ? "Đã duyệt tất cả " + reviewed
                            + " đơn đang chờ. Mỗi nhân viên được duyệt theo đơn của chính họ; các ca đã được xếp vào lịch làm việc và nhân viên đã được thông báo."
                            : "Đã từ chối tất cả " + reviewed
                            + " đơn đang chờ. Đơn đã duyệt được giữ nguyên. Nhân viên của các đơn vừa từ chối đã được thông báo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        keepWeek(redirectAttributes, week);
        return "redirect:/availability/manager";
    }

    private void keepWeek(RedirectAttributes redirectAttributes, String week) {
        if (week != null && !week.isBlank()) {
            redirectAttributes.addAttribute("week", week);
        }
    }

    private void addWeekNav(Model model, LocalDate weekStart) {
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("previousWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("currentWeek", VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    private void emptyReview(Model model, String message) {
        LocalDate current = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        model.addAttribute("availabilityGrid", null);
        model.addAttribute("registrations", List.of());
        model.addAttribute("activePositions", List.of());
        model.addAttribute("reviewTotals", new ReviewTotals(0, 0, 0, 0));
        addWeekNav(model, current.plusWeeks(1));
        model.addAttribute("pageError", message);
    }

    private ReviewTotals countRegistrations(List<WeeklyAvailabilityView.RegistrationGroup> registrations) {
        int approved = 0;
        int pending = 0;
        int rejected = 0;
        if (registrations != null) {
            for (WeeklyAvailabilityView.RegistrationGroup item : registrations) {
                String status = item.getStatusKey();
                if ("approved".equals(status)) {
                    approved++;
                } else if ("pending".equals(status)) {
                    pending++;
                } else if ("rejected".equals(status)) {
                    rejected++;
                }
            }
        }
        int total = registrations == null ? 0 : registrations.size();
        return new ReviewTotals(total, approved, pending, rejected);
    }

    public record ReviewTotals(int total, int approved, int pending, int rejected) {
    }

    private void emptyLimits(Model model, String message) {
        LocalDate current = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        model.addAttribute("availabilityGrid", null);
        model.addAttribute("staffSlotCapacity", null);
        model.addAttribute("activePositions", List.of());
        model.addAttribute("eligibleStaff", 0);
        model.addAttribute("eligibleBySkill", Map.of());
        model.addAttribute("configTotals", new SlotConfigTotals(0, 0, 0, 0, ""));
        model.addAttribute("weekStart", current.plusWeeks(1));
        model.addAttribute("previousWeek", current);
        model.addAttribute("nextWeek", current.plusWeeks(2));
        model.addAttribute("currentWeek", current);
        model.addAttribute("pageError", message);
    }

    private LocalDate resolveConfigWeek(String week) {
        LocalDate current = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate selected = week == null || week.isBlank()
                ? current.plusWeeks(1)
                : LocalDate.parse(week).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate earliest = current.minusWeeks(8);
        LocalDate latest = current.plusWeeks(16);
        if (selected.isBefore(earliest)) {
            return earliest;
        }
        if (selected.isAfter(latest)) {
            return latest;
        }
        return selected;
    }

    private SlotConfigTotals summarize(WeeklyAvailabilityView grid) {
        int demand = 0;
        int registrations = 0;
        int positionTarget = 0;
        int shortfall = 0;
        Map<String, Integer> missing = new LinkedHashMap<>();
        if (grid.getShiftRows() != null) {
            for (WeeklyAvailabilityView.ShiftRow row : grid.getShiftRows()) {
                if (row.getDayCells() == null) {
                    continue;
                }
                for (WeeklyAvailabilityView.DayCell cell : row.getDayCells()) {
                    if (cell.isConfigured() && cell.getMaxEmployees() != null) {
                        demand += cell.getMaxEmployees();
                    }
                    registrations += cell.getRegisteredCount();
                    if (cell.getPositionLines() == null) {
                        continue;
                    }
                    for (WeeklyAvailabilityView.PositionLine line : cell.getPositionLines()) {
                        if (line.getRequired() > 0) {
                            positionTarget += line.getRequired();
                        }
                        if (line.getRequired() > 0 && line.getShortfall() > 0) {
                            shortfall += line.getShortfall();
                            missing.merge(line.getSkillName(), line.getShortfall(), Integer::sum);
                        }
                    }
                }
            }
        }
        String text = missing.entrySet().stream()
                .map(entry -> "Thiếu " + entry.getValue() + " " + entry.getKey())
                .reduce((left, right) -> left + ", " + right)
                .orElse(shortfall == 0 ? "Không có vị trí thiếu" : "");
        return new SlotConfigTotals(demand, registrations, positionTarget, shortfall, text);
    }
}
