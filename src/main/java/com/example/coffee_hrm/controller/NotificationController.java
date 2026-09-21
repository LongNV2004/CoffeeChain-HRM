package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final RoleBasedRedirector roleBasedRedirector;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("dashboardHome", roleBasedRedirector.resolve(user.getRoleName()));
        model.addAttribute("currentRole", user.getRoleName().name());
        model.addAttribute("notifications", notificationService.listMine(user));
        return "Notifications";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Integer id,
                           @AuthenticationPrincipal AuthenticatedUser user,
                           RedirectAttributes redirectAttributes) {
        try {
            notificationService.markAsRead(id, user);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    public String markAllRead(@AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirectAttributes) {
        try {
            notificationService.markAllAsRead(user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đánh dấu tất cả thông báo là đã đọc.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/notifications";
    }
}
