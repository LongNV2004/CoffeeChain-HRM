package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.dto.response.PayrollDetailView;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface PayrollService {

    PayrollBoardView board(AuthenticatedUser user,
                           Integer year,
                           Integer month,
                           String period,
                           Integer storeId,
                           Integer employeeId,
                           String status);

    PayrollDetailView detail(AuthenticatedUser user, int year, int month, Integer employeeId);

    void calculate(AuthenticatedUser user, int year, int month);

    void lock(AuthenticatedUser user, int year, int month);

    byte[] export(AuthenticatedUser user,
                  Integer year,
                  Integer month,
                  String period,
                  Integer storeId,
                  Integer employeeId,
                  String status);

    String exportFileName(Integer year, Integer month, String period);

    PayrollBoardView empty(AuthenticatedUser user);
}
