package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        assertTrue(body.contains("mật khẩu cũ không còn dùng được"));
        assertTrue(body.contains("Email đăng nhập:"));
        assertTrue(body.contains("staff@shop.vn"));
        assertTrue(body.contains("Mật khẩu mới:"));
        assertTrue(body.contains("Abcd123!"));
        assertTrue(body.contains("Hãy nhập đúng email và mật khẩu mới này trên trang đăng nhập."));
        assertTrue(body.contains("http://localhost:8081/login"));
    }

    @Test
    void managerMailUsesConfiguredLoginUrlAndDoesNotClaimSuccessWhenSendingFails() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        AccountMailServiceImpl service = mailService(sender);

        service.sendManagerCredentials("lan@coffee.vn", "Lan Nguyen", "Coffee Nguyễn Trãi", "lan@coffee.vn", "Temp#a12");

        verify(sender).send(message);
        message.saveChanges();
        assertEquals(AccountMailServiceImpl.MANAGER_SUBJECT, message.getSubject());
        String body = message.getContent().toString();
        assertTrue(body.contains("Lan Nguyen"));
        assertTrue(body.contains("Coffee Nguyễn Trãi"));
        assertTrue(body.contains("lan@coffee.vn"));
        assertTrue(body.contains("http://localhost:8081/login"));
        assertTrue(body.contains("Temp#a12"));
        assertTrue(body.contains("vào thẳng trang quản lý cửa hàng"));
        assertFalse(body.contains("đổi mật khẩu ngay trong lần đăng nhập đầu tiên"));
        assertFalse(body.contains("trúng tuyển"));

        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> missing = mock(ObjectProvider.class);
        when(missing.getIfAvailable()).thenReturn(null);
        AccountMailServiceImpl unconfigured = new AccountMailServiceImpl(missing, "", "", "http://localhost:8081/login");
        BusinessException ex = assertThrows(BusinessException.class, () -> unconfigured.sendManagerCredentials(
                "lan@coffee.vn", "Lan Nguyen", "Coffee Nguyễn Trãi", "lan@coffee.vn", "Temp#a12"));
        assertFalse(ex.getMessage().contains("Temp#a12"));
        assertFalse(ex.getMessage().toLowerCase().contains("thành công"));
        assertFalse(ex.getMessage().contains("chưa tạo tài khoản"));
    }

    @Test
    void rejectionMailUsesTheCandidateNameAndReason() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        AccountMailServiceImpl service = mailService(sender);

        boolean sent = service.sendRecruitmentRejection("an@shop.vn", "An Nguyễn", "Chưa đủ hồ sơ");

        assertTrue(sent);
        verify(sender).send(message);
        message.saveChanges();
        assertEquals(AccountMailServiceImpl.REJECTION_SUBJECT, message.getSubject());
        String body = message.getContent().toString();
        assertTrue(body.contains("Kính gửi An Nguyễn,"));
        assertTrue(body.contains("Cảm ơn bạn đã quan tâm và dành thời gian tham gia quy trình tuyển dụng tại CoffeeHRM."));
        assertTrue(body.contains("bạn chưa phù hợp với yêu cầu tuyển dụng trong đợt này."));
        assertTrue(body.contains("Lý do: Chưa đủ hồ sơ"));
        assertTrue(body.contains("Bộ phận Tuyển dụng – CoffeeHRM"));
        assertFalse(body.contains("Mật khẩu"));
    }

    @Test
    void rejectionMailOmitsReasonWhenAbsent() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        AccountMailServiceImpl service = mailService(sender);

        assertTrue(service.sendRecruitmentRejection("an@shop.vn", "An Nguyễn", "   "));

        message.saveChanges();
        String body = message.getContent().toString();
        assertTrue(body.contains("Kính gửi An Nguyễn,"));
        assertFalse(body.contains("Lý do:"));
    }

    @Test
    void invalidRejectionAddressIsNotSent() {
        JavaMailSender sender = mock(JavaMailSender.class);
        AccountMailServiceImpl service = mailService(sender);

        assertFalse(service.sendRecruitmentRejection(null, "An Nguyễn", null));
        assertFalse(service.sendRecruitmentRejection("   ", "An Nguyễn", null));
        assertFalse(service.sendRecruitmentRejection("khong-hop-le", "An Nguyễn", null));

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void smtpFailureDoesNotThrowForRejectionMail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("SMTP down")).when(sender).send(message);
        AccountMailServiceImpl service = mailService(sender);

        assertFalse(service.sendRecruitmentRejection("an@shop.vn", "An Nguyễn", null));
    }

    @Test
    void missingMailSenderDoesNotThrowForRejectionMail() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AccountMailServiceImpl service = new AccountMailServiceImpl(provider, "noreply@coffeehrm.local", "", "http://localhost:8081/login");

        assertFalse(service.sendRecruitmentRejection("an@shop.vn", "An Nguyễn", null));
    }

    private AccountMailServiceImpl mailService(JavaMailSender sender) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new AccountMailServiceImpl(provider, "noreply@coffeehrm.local", "", "http://localhost:8081/login");
    }
}
