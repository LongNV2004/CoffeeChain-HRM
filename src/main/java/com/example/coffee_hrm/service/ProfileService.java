package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.ChangePasswordRequest;
import com.example.coffee_hrm.dto.response.MyProfileView;
import com.example.coffee_hrm.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ProfileService {

    MyProfileView getProfile(AuthenticatedUser actor);

    void changePassword(AuthenticatedUser actor,
                        ChangePasswordRequest request,
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse);
}
