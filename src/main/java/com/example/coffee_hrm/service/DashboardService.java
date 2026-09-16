package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.AdminDashboardView;
import com.example.coffee_hrm.dto.response.ManagerDashboardView;
import com.example.coffee_hrm.dto.response.StaffDashboardView;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface DashboardService {

    AdminDashboardView buildAdminDashboard(AuthenticatedUser user);

    ManagerDashboardView buildManagerDashboard(AuthenticatedUser user);

    StaffDashboardView buildStaffDashboard(AuthenticatedUser user);
}
