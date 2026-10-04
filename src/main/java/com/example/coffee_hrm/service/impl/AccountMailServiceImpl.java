package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.service.AccountMailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AccountMailServiceImpl implements AccountMailService {

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String fromAddress;
    private final String mailUsername;
    private final String loginUrl;

    public AccountMailServiceImpl(ObjectProvider<JavaMailSender> mailSenders,
                                  @Value("${coffee-hrm.mail.from:}") String fromAddress,
                                  @Value("${spring.mail.username:}") String mailUsername,
                                  @Value("${coffee-hrm.app.login-url:http://localhost:8081/login}") String loginUrl) {
        this.mailSenders = mailSenders;
        this.fromAddress = fromAddress;
        this.mailUsername = mailUsername;
        this.loginUrl = loginUrl;
    }

    @Override
    public void sendTemporaryPassword(String toEmail, String employeeName, String loginEmail, String temporaryPassword) {
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (mailSender == null) {
            log.error("Không gửi được email mật khẩu tạm tới {}: chưa cấu hình spring.mail.host", toEmail);
            throw mailFailure();
        }
        String from = resolveFrom();
        if (from == null) {
            log.error("Không gửi được email mật khẩu tạm tới {}: thiếu địa chỉ người gửi", toEmail);
            throw mailFailure();
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("CoffeeHRM - Thông tin đăng nhập");
            helper.setText(buildBody(employeeName, loginEmail, temporaryPassword));
            mailSender.send(message);
            log.info("Đã gửi email mật khẩu tạm tới {}", toEmail);
        } catch (MessagingException | RuntimeException ex) {
            log.error("Gửi email mật khẩu tạm thất bại tới {}", toEmail, ex);
            throw mailFailure();
        }
    }

    private String resolveFrom() {
        if (fromAddress != null && !fromAddress.isBlank()) {
            return fromAddress.trim();
        }
        if (mailUsername != null && !mailUsername.isBlank()) {
            return mailUsername.trim();
        }
        return null;
    }

    private String buildBody(String employeeName, String loginEmail, String temporaryPassword) {
        return """
                Xin chào %s,

                Chúc mừng bạn đã trúng tuyển !
                Bạn đã được tạo tài khoản CoffeeHRM.

                Thông tin đăng nhập:

                Email:
                %s

                Mật khẩu:
                %s

                Vui lòng sử dụng thông tin trên để đăng nhập vào hệ thống.
                Đăng nhập tại: %s

                Trân trọng,
                CoffeeHRM
                """.formatted(employeeName, loginEmail, temporaryPassword, loginUrl);
    }

    private BusinessException mailFailure() {
        return new BusinessException(
                "Không gửi được email mật khẩu tạm thời. Hệ thống chưa tạo tài khoản. Vui lòng kiểm tra cấu hình email rồi duyệt lại.");
    }
}
