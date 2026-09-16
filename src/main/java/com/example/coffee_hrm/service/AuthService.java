package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.LoginRequest;
import com.example.coffee_hrm.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    LoginResponse authenticate(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    void logout(HttpServletRequest request, HttpServletResponse response);
}
