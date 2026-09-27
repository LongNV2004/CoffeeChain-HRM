package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.NotificationResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final RoleBasedRedirector roleBasedRedirector;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user) {
        return "redirect:" + home(user);
    }

    @GetMapping(value = "/feed", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<NotificationResponse> feed(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.listMine(user);
    }

    @PostMapping(value = "/{id}/read", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markReadAjax(@PathVariable Integer id,
                                                            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            notificationService.markAsRead(id, user);
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "unreadCount", notificationService.countUnread(user)));
        } catch (BusinessException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
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
        return "redirect:" + home(user);
    }

    @PostMapping(value = "/read-all", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllReadAjax(@AuthenticationPrincipal AuthenticatedUser user) {
        try {
            notificationService.markAllAsRead(user);
            return ResponseEntity.ok(Map.of("ok", true, "unreadCount", 0));
        } catch (BusinessException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
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
        return "redirect:" + home(user);
    }

    private String home(AuthenticatedUser user) {
        String target = user == null ? null : roleBasedRedirector.resolve(user.getRoleName());
        return target != null ? target : "/login";
    }
}
