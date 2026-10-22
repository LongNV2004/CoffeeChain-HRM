package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.MyProfileView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ProfileService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;
    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private RecruitmentRequestService recruitmentRequestService;
    @MockitoBean
    private ResignationProposalService resignationProposalService;
    @MockitoBean
    private TrainingService trainingService;

    @Test
    void managerCanViewProfileAndCertificates() throws Exception {
        AuthenticatedUser manager = actor(RoleName.MANAGER, 4);
        when(profileService.getProfile(manager)).thenReturn(profile(RoleName.MANAGER, List.of(
                skill(1, "Pha chế", CertificationStatus.CERTIFIED, "01/10/2026"),
                skill(2, "Phục vụ", CertificationStatus.NOTCERTIFIED, null))));

        mockMvc.perform(get("/profile").param("userId", "99").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("An Nguyễn")))
                .andExpect(content().string(containsString("Mã nhân viên")))
                .andExpect(content().string(containsString("CERTIFIED · Đã chứng nhận")))
                .andExpect(content().string(containsString("NOTCERTIFIED · Chưa chứng nhận")))
                .andExpect(content().string(containsString("Ngày cấp 01/10/2026")))
                .andExpect(content().string(containsString("Hồ sơ cá nhân")));

        verify(profileService).getProfile(manager);
    }

    @Test
    void staffWithoutCertificatesSeesTheEmptyStateAndCanOpenPasswordChange() throws Exception {
        AuthenticatedUser staff = actor(RoleName.STAFF, 11);
        when(profileService.getProfile(staff)).thenReturn(profile(RoleName.STAFF, List.of()));

        mockMvc.perform(get("/profile").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn chưa có chứng chỉ kỹ năng.")))
                .andExpect(content().string(containsString("Đổi mật khẩu")))
                .andExpect(content().string(containsString("/profile/password")));
    }

    @Test
    void adminAndAnonymousUsersCannotOpenTheProfile() throws Exception {
        mockMvc.perform(get("/profile").with(user(actor(RoleName.ADMIN, 1))))
                .andExpect(redirectedUrl("/dashboard/admin"));
        mockMvc.perform(get("/profile"))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/profile/password")
                        .with(csrf())
                        .with(user(actor(RoleName.ADMIN, 1)))
                        .param("currentPassword", "current1")
                        .param("newPassword", "newpass1")
                        .param("confirmPassword", "newpass1"))
                .andExpect(redirectedUrl("/dashboard/admin"));
        verify(profileService, never()).getProfile(any());
        verify(profileService, never()).changePassword(any(), any(), any(), any());
    }

    @Test
    void passwordChangeRequiresCsrfAndIgnoresASubmittedUserId() throws Exception {
        AuthenticatedUser staff = actor(RoleName.STAFF, 11);
        mockMvc.perform(post("/profile/password")
                        .with(user(staff))
                        .param("currentPassword", "current1")
                        .param("newPassword", "newpass1")
                        .param("confirmPassword", "newpass1")
                        .param("userId", "99"))
                .andExpect(redirectedUrl("/dashboard/staff"));
        verify(profileService, never()).changePassword(any(), any(), any(), any());

        mockMvc.perform(post("/profile/password")
                        .with(csrf())
                        .with(user(staff))
                        .param("currentPassword", "current1")
                        .param("newPassword", "newpass1")
                        .param("confirmPassword", "newpass1")
                        .param("userId", "99"))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("successMessage", "Đã đổi mật khẩu."));

        verify(profileService).changePassword(org.mockito.ArgumentMatchers.eq(staff), any(), any(), any());
    }

    @Test
    void passwordChangeShowsValidationAndAuthenticationFailuresWithoutThePassword() throws Exception {
        AuthenticatedUser staff = actor(RoleName.STAFF, 11);
        when(profileService.getProfile(staff)).thenReturn(profile(RoleName.STAFF, List.of()));

        mockMvc.perform(post("/profile/password")
                        .with(csrf())
                        .with(user(staff))
                        .param("currentPassword", "current1")
                        .param("newPassword", "123")
                        .param("confirmPassword", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu mới phải từ 6 đến 100 ký tự")))
                .andExpect(content().string(not(containsString("current1"))));

        mockMvc.perform(post("/profile/password")
                        .with(csrf())
                        .with(user(staff))
                        .param("currentPassword", "current1")
                        .param("newPassword", "newpass1")
                        .param("confirmPassword", "newpass2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu mới và xác nhận mật khẩu không trùng nhau.")))
                .andExpect(content().string(not(containsString("newpass1"))));

        doThrow(new BusinessException("Mật khẩu hiện tại không chính xác."))
                .when(profileService).changePassword(org.mockito.ArgumentMatchers.eq(staff), any(), any(), any());
        mockMvc.perform(post("/profile/password")
                        .with(csrf())
                        .with(user(staff))
                        .param("currentPassword", "wrongpass")
                        .param("newPassword", "newpass1")
                        .param("confirmPassword", "newpass1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu hiện tại không chính xác.")))
                .andExpect(content().string(not(containsString("wrongpass"))));
    }

    private AuthenticatedUser actor(RoleName roleName, int userId) {
        Employee employee = roleName == RoleName.ADMIN
                ? null
                : Employee.builder().id(userId * 10).fullName("An Nguyễn").status(EmployeeStatus.ACTIVE).build();
        User user = User.builder()
                .id(userId)
                .username("an.nguyen")
                .passwordHash("hash")
                .role(Role.builder().id(1).roleName(roleName).build())
                .employee(employee)
                .isActive(true)
                .build();
        return AuthenticatedUser.from(user);
    }

    private MyProfileView profile(RoleName roleName, List<EmployeeSkillStatusLine> skills) {
        return MyProfileView.builder()
                .employeeId(roleName == RoleName.MANAGER ? 40 : 20)
                .fullName("An Nguyễn")
                .initials("AN")
                .username("an.nguyen")
                .email("an@shop.vn")
                .phone("0901234567")
                .roleName(roleName)
                .roleLabel(roleName == RoleName.MANAGER ? "Quản lý cửa hàng" : "Nhân viên")
                .sidebarRole(roleName == RoleName.MANAGER ? "manager" : "staff")
                .storeName("Coffee Quận 1")
                .employeeStatus(EmployeeStatus.ACTIVE)
                .employeeStatusLabel("Đang làm việc")
                .accountActive(true)
                .accountStatusLabel("Đang hoạt động")
                .hireDate(LocalDate.of(2024, 1, 15))
                .skillCertificates(skills)
                .build();
    }

    private EmployeeSkillStatusLine skill(int id, String name, CertificationStatus status, String certifiedOn) {
        return EmployeeSkillStatusLine.builder()
                .skillId(id)
                .skillName(name)
                .certificationStatus(status)
                .certificationLabel(status.name() + " - " + name)
                .certifiedDateLabel(certifiedOn)
                .build();
    }
}
