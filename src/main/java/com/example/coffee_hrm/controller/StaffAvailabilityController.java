package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/availability")
@PreAuthorize("hasRole('STAFF')")
public class StaffAvailabilityController {

    private final StaffAvailabilityService staffAvailabilityService;

    @GetMapping
    public String availabilityPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        populatePage(user, model);
        if (!model.containsAttribute("availabilityForm")) {
            model.addAttribute("availabilityForm", buildFormFromGrid(model.getAttribute("availabilityGrid")));
        }
        return "staff/StaffAvailability";
    }

    @PostMapping
    public String submitAvailability(@ModelAttribute("availabilityForm") SubmitWorkAvailabilityRequest availabilityForm,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        try {
            int count = staffAvailabilityService.submitNextWeekAvailability(availabilityForm, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã gửi " + count + " đề xuất lịch rảnh cho tuần kế tiếp. Manager cửa hàng đã được thông báo.");
            return "redirect:/availability";
        } catch (BusinessException ex) {
            populatePage(user, model);
            model.addAttribute("availabilityForm", availabilityForm);
            model.addAttribute("availabilityError", ex.getMessage());
            return "staff/StaffAvailability";
        }
    }

    private void populatePage(AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("storeName", user.getStoreName());
        try {
            model.addAttribute("availabilityGrid", staffAvailabilityService.getNextWeekAvailabilityGrid(user));
            model.addAttribute("availabilities", staffAvailabilityService.listMyNextWeekAvailabilities(user));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("availabilityGrid", null);
            model.addAttribute("availabilities", List.of());
            model.addAttribute("pageError", ex.getMessage());
        }
    }

    private SubmitWorkAvailabilityRequest buildFormFromGrid(Object gridAttr) {
        SubmitWorkAvailabilityRequest form = new SubmitWorkAvailabilityRequest();
        if (!(gridAttr instanceof WeeklyAvailabilityView grid) || grid.getShiftRows() == null) {
            return form;
        }
        List<String> selected = new ArrayList<>();
        for (WeeklyAvailabilityView.ShiftRow row : grid.getShiftRows()) {
            if (row.getDayCells() == null) {
                continue;
            }
            for (WeeklyAvailabilityView.DayCell cell : row.getDayCells()) {
                if (cell.isSelected() && cell.getSlotKey() != null) {
                    selected.add(cell.getSlotKey());
                }
            }
        }
        form.setSelectedSlots(selected);
        return form;
    }
}
