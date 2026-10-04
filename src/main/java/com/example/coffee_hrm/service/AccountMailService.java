package com.example.coffee_hrm.service;

public interface AccountMailService {

    void sendTemporaryPassword(String toEmail, String employeeName, String loginEmail, String temporaryPassword);
}
