package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountMailServiceImplTest {

    @Test
    void missingMailSenderDoesNotClaimSuccess() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AccountMailServiceImpl service = new AccountMailServiceImpl(provider, "", "", "http://localhost:8081/login");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendTemporaryPassword("staff@shop.vn", "An", "staff@shop.vn", "Abcd123!"));

        assertTrue(ex.getMessage().contains("chưa tạo tài khoản"));
        assertFalse(ex.getMessage().toLowerCase().contains("thành công"));
    }

    @Test
    void sentMailContainsLoginCredentials() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        AccountMailServiceImpl service = new AccountMailServiceImpl(
                provider, "noreply@coffeehrm.local", "", "http://localhost:8081/login");

        service.sendTemporaryPassword("staff@shop.vn", "An Nguyễn", "staff@shop.vn", "Abcd123!");

        verify(sender).send(message);
        message.saveChanges();
        String body = message.getContent().toString();
        assertTrue(body.contains("Xin chào An Nguyễn"));
        assertTrue(body.contains("Chúc mừng bạn đã trúng tuyển !"));
        assertTrue(body.contains("Bạn đã được tạo tài khoản CoffeeHRM."));
        assertTrue(body.contains("Email:"));
        assertTrue(body.contains("staff@shop.vn"));
        assertTrue(body.contains("Mật khẩu:"));
        assertTrue(body.contains("Abcd123!"));
        assertTrue(body.contains("Vui lòng sử dụng thông tin trên để đăng nhập vào hệ thống."));
        assertTrue(body.contains("http://localhost:8081/login"));
    }
}
