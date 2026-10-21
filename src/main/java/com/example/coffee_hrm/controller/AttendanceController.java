package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public String staffClock(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        return clock(user, model, "staff/StaffCheckIn");
    }

    @PostMapping("/staff/check-in/preview")
    @PreAuthorize("hasRole('STAFF')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> staffCheckInPreview(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @RequestParam(required = false) String latitude,
                                                                   @RequestParam(required = false) String longitude,
                                                                   @RequestParam(required = false) String accuracy) {
        return preview(() -> attendanceService.previewCheckIn(
                user, AttendanceLocation.parse(latitude, longitude, accuracy)));
    }

    @PostMapping("/staff/check-in")
    @PreAuthorize("hasRole('STAFF')")
    public String staffCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false) String latitude,
                               @RequestParam(required = false) String longitude,
                               @RequestParam(required = false) String accuracy,
                               @RequestParam(required = false) MultipartFile photo,
                               RedirectAttributes redirectAttributes) {
        return submitCheckIn(user, latitude, longitude, accuracy, photo, redirectAttributes, "redirect:/attendance/staff");
    }

    @PostMapping("/staff/check-out/preview")
    @PreAuthorize("hasRole('STAFF')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> staffCheckOutPreview(@AuthenticationPrincipal AuthenticatedUser user,
                                                                    @RequestParam(required = false) String latitude,
                                                                    @RequestParam(required = false) String longitude,
                                                                    @RequestParam(required = false) String accuracy) {
        return preview(() -> attendanceService.previewCheckOut(
                user, AttendanceLocation.parse(latitude, longitude, accuracy)));
    }

    @PostMapping("/staff/check-out")
    @PreAuthorize("hasRole('STAFF')")
    public String staffCheckOut(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(required = false) String latitude,
                                @RequestParam(required = false) String longitude,
                                @RequestParam(required = false) String accuracy,
                                @RequestParam(required = false) MultipartFile photo,
                                RedirectAttributes redirectAttributes) {
        return submitCheckOut(user, latitude, longitude, accuracy, photo, redirectAttributes, "redirect:/attendance/staff");
    }

    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerClock(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        return clock(user, model, "manager/ManagerCheckIn");
    }

    @PostMapping("/manager/check-in/preview")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> managerCheckInPreview(@AuthenticationPrincipal AuthenticatedUser user,
                                                                     @RequestParam(required = false) String latitude,
                                                                     @RequestParam(required = false) String longitude,
                                                                     @RequestParam(required = false) String accuracy) {
        return preview(() -> attendanceService.previewCheckIn(
                user, AttendanceLocation.parse(latitude, longitude, accuracy)));
    }

    @PostMapping("/manager/check-in")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) String latitude,
                                 @RequestParam(required = false) String longitude,
                                 @RequestParam(required = false) String accuracy,
                                 @RequestParam(required = false) MultipartFile photo,
                                 RedirectAttributes redirectAttributes) {
        return submitCheckIn(user, latitude, longitude, accuracy, photo, redirectAttributes, "redirect:/attendance/manager");
    }

    @PostMapping("/manager/check-out/preview")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> managerCheckOutPreview(@AuthenticationPrincipal AuthenticatedUser user,
                                                                      @RequestParam(required = false) String latitude,
                                                                      @RequestParam(required = false) String longitude,
                                                                      @RequestParam(required = false) String accuracy) {
        return preview(() -> attendanceService.previewCheckOut(
                user, AttendanceLocation.parse(latitude, longitude, accuracy)));
    }

    @PostMapping("/manager/check-out")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerCheckOut(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required = false) String latitude,
                                  @RequestParam(required = false) String longitude,
                                  @RequestParam(required = false) String accuracy,
                                  @RequestParam(required = false) MultipartFile photo,
                                  RedirectAttributes redirectAttributes) {
        return submitCheckOut(user, latitude, longitude, accuracy, photo, redirectAttributes, "redirect:/attendance/manager");
    }

    @GetMapping("/photos/{attendanceId}/{kind}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable Integer attendanceId,
                                        @PathVariable String kind) {
        if (!"in".equals(kind) && !"out".equals(kind)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = attendanceService.readShiftPhoto(user, attendanceId, "in".equals(kind));
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(bytes);
        } catch (BusinessException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private String clock(AuthenticatedUser user, Model model, String view) {
        try {
            model.addAttribute("clock", attendanceService.getClock(user));
            if (!model.containsAttribute("pageError")) {
                model.addAttribute("pageError", null);
            }
        } catch (BusinessException ex) {
            model.addAttribute("clock", null);
            model.addAttribute("pageError", ex.getMessage());
        }
        return view;
    }

    private String submitCheckIn(AuthenticatedUser user,
                                 String latitude,
                                 String longitude,
                                 String accuracy,
                                 MultipartFile photo,
                                 RedirectAttributes redirectAttributes,
                                 String redirect) {
        try {
            attendanceService.checkInWithPhoto(
                    user, AttendanceLocation.parse(latitude, longitude, accuracy), photoBytes(photo));
            redirectAttributes.addFlashAttribute("pageMessage", "Check-in thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }

    private String submitCheckOut(AuthenticatedUser user,
                                  String latitude,
                                  String longitude,
                                  String accuracy,
                                  MultipartFile photo,
                                  RedirectAttributes redirectAttributes,
                                  String redirect) {
        try {
            attendanceService.checkOutWithPhoto(
                    user, AttendanceLocation.parse(latitude, longitude, accuracy), photoBytes(photo));
            redirectAttributes.addFlashAttribute("pageMessage", "Check-out thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }

    private ResponseEntity<Map<String, String>> preview(Runnable action) {
        try {
            action.run();
            return ResponseEntity.ok(Map.of("ok", "true"));
        } catch (BusinessException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    private byte[] photoBytes(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        try {
            return photo.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được ảnh. Hãy chụp lại.");
        }
    }
}
