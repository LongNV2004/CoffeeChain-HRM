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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AccountMailServiceImpl implements AccountMailService {

    static final String REJECTION_SUBJECT = "Thông báo kết quả tuyển dụng – CoffeeHRM";
    static final String RESIGNATION_DECISION_SUBJECT = "Thông báo quyết định nghỉ việc – CoffeeHRM";
    static final String MANAGER_SUBJECT = "Thông tin tài khoản quản lý CoffeeHRM.";
    private static final DateTimeFormatter DECISION_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String MANAGER_MAIL_FAILURE = "Không gửi được email thông tin tài khoản quản lý.";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

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

    @Override
    public void sendManagerCredentials(String toEmail,
                                       String managerName,
                                       String storeName,
                                       String loginEmail,
                                       String temporaryPassword) {
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (mailSender == null) {
            log.error("Không gửi được email tài khoản quản lý tới {}: chưa cấu hình spring.mail.host", toEmail);
            throw new BusinessException(MANAGER_MAIL_FAILURE);
        }
        String from = resolveFrom();
        if (from == null) {
            log.error("Không gửi được email tài khoản quản lý tới {}: thiếu địa chỉ người gửi", toEmail);
            throw new BusinessException(MANAGER_MAIL_FAILURE);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(MANAGER_SUBJECT);
            helper.setText(buildManagerBody(managerName, storeName, loginEmail, temporaryPassword));
            mailSender.send(message);
            log.info("Đã gửi email tài khoản quản lý tới {}", toEmail);
        } catch (MessagingException | RuntimeException ex) {
            log.error("Gửi email tài khoản quản lý thất bại tới {}", toEmail, ex);
            throw new BusinessException(MANAGER_MAIL_FAILURE);
        }
    }

    @Override
    public boolean sendRecruitmentRejection(String toEmail, String candidateName, String rejectReason) {
        String recipient = normalizeRecipient(toEmail);
        if (recipient == null) {
            log.error("Không gửi email thông báo không trúng tuyển cho {}: địa chỉ email thiếu hoặc không hợp lệ [{}]",
                    displayName(candidateName), toEmail);
            return false;
        }
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (mailSender == null) {
            log.error("Không gửi được email thông báo không trúng tuyển tới {}: chưa cấu hình spring.mail.host", recipient);
            return false;
        }
        String from = resolveFrom();
        if (from == null) {
            log.error("Không gửi được email thông báo không trúng tuyển tới {}: thiếu địa chỉ người gửi", recipient);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(REJECTION_SUBJECT);
            helper.setText(buildRejectionBody(candidateName, rejectReason));
            mailSender.send(message);
            log.info("Đã gửi email thông báo không trúng tuyển tới {}", recipient);
            return true;
        } catch (MessagingException | RuntimeException ex) {
            log.error("Gửi email thông báo không trúng tuyển thất bại tới {}", recipient, ex);
            return false;
        }
    }

    @Override
    public boolean sendResignationNotice(String toEmail, String recipientName, String subject, String body) {
        String recipient = normalizeRecipient(toEmail);
        if (recipient == null) {
            log.error("Không gửi email đề xuất nghỉ việc cho {}: địa chỉ email thiếu hoặc không hợp lệ [{}]",
                    displayName(recipientName), toEmail);
            return false;
        }
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (mailSender == null) {
            log.error("Không gửi được email đề xuất nghỉ việc tới {}: chưa cấu hình spring.mail.host", recipient);
            return false;
        }
        String from = resolveFrom();
        if (from == null) {
            log.error("Không gửi được email đề xuất nghỉ việc tới {}: thiếu địa chỉ người gửi", recipient);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject == null || subject.isBlank() ? "CoffeeHRM - Đề xuất nghỉ việc" : subject.trim());
            helper.setText(buildResignationBody(recipientName, body));
            mailSender.send(message);
            log.info("Đã gửi email đề xuất nghỉ việc tới {}", recipient);
            return true;
        } catch (MessagingException | RuntimeException ex) {
            log.error("Gửi email đề xuất nghỉ việc thất bại tới {}", recipient, ex);
            return false;
        }
    }

    @Override
    public boolean sendResignationDecision(String toEmail, String employeeName, String storeName, LocalDateTime decidedAt) {
        String recipient = normalizeRecipient(toEmail);
        if (recipient == null) {
            log.error("Không gửi email quyết định nghỉ việc cho {}: địa chỉ email thiếu hoặc không hợp lệ",
                    displayName(employeeName));
            return false;
        }
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (mailSender == null) {
            log.error("Không gửi được email quyết định nghỉ việc tới {}: chưa cấu hình spring.mail.host", recipient);
            return false;
        }
        String from = resolveFrom();
        if (from == null) {
            log.error("Không gửi được email quyết định nghỉ việc tới {}: thiếu địa chỉ người gửi", recipient);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(RESIGNATION_DECISION_SUBJECT);
            helper.setText(buildResignationDecisionBody(employeeName, storeName, decidedAt));
            mailSender.send(message);
            log.info("Đã gửi email quyết định nghỉ việc tới {}", recipient);
            return true;
        } catch (MessagingException | RuntimeException ex) {
            log.error("Gửi email quyết định nghỉ việc thất bại tới {}", recipient, ex);
            return false;
        }
    }

    private String buildResignationDecisionBody(String employeeName, String storeName, LocalDateTime decidedAt) {
        String name = employeeName == null || employeeName.isBlank() ? "bạn" : employeeName.trim();
        String store = storeName == null || storeName.isBlank() ? "cửa hàng" : storeName.trim();
        String when = decidedAt == null ? "không xác định" : decidedAt.format(DECISION_TIME);
        return """
                Kính gửi %s,

                Đề xuất nghỉ việc của bạn tại cửa hàng %s đã được Admin phê duyệt.

                Thời điểm ghi nhận quyết định: %s.

                Theo quyết định này, trạng thái làm việc của bạn được cập nhật thành đã nghỉ việc và tài khoản đăng nhập CoffeeHRM được vô hiệu hóa. Hồ sơ nhân sự cùng dữ liệu lịch sử vẫn được lưu trên hệ thống.

                Cảm ơn bạn đã đồng hành cùng CoffeeHRM. Nếu cần hỗ trợ thêm, vui lòng liên hệ Manager cửa hàng hoặc bộ phận quản lý.

                Trân trọng,
                CoffeeHRM
                """.formatted(name, store, when);
    }

    private String buildResignationBody(String recipientName, String body) {
        String name = recipientName == null || recipientName.isBlank() ? "bạn" : recipientName.trim();
        String content = body == null ? "" : body.trim();
        return """
                Xin chào %s,

                %s

                Trân trọng,
                CoffeeHRM
                """.formatted(name, content);
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
                Tài khoản email bên dưới được dùng để đăng nhập CoffeeHRM.
                Nếu bạn từng nghỉ việc, đây là mật khẩu mới: mật khẩu cũ không còn dùng được.

                Email đăng nhập:
                %s

                Mật khẩu mới:
                %s

                Hãy nhập đúng email và mật khẩu mới này trên trang đăng nhập.
                Đăng nhập tại: %s

                Trân trọng,
                CoffeeHRM
                """.formatted(employeeName, loginEmail, temporaryPassword, loginUrl);
    }

    private String buildManagerBody(String managerName, String storeName, String loginEmail, String temporaryPassword) {
        return """
                Xin chào %s,

                Tài khoản quản lý CoffeeHRM của bạn đã được tạo.

                Họ và tên: %s
                Cửa hàng được phân công: %s
                Email đăng nhập: %s

                Để truy cập hệ thống, mở trang đăng nhập:
                %s

                Dùng email đăng nhập và mật khẩu tạm thời bên dưới để vào hệ thống.
                Mật khẩu tạm thời: %s

                Sau khi đăng nhập, bạn vào thẳng trang quản lý cửa hàng và dùng các chức năng của Manager.
                Không chia sẻ mật khẩu tạm thời.

                Trân trọng,
                CoffeeHRM
                """.formatted(managerName, managerName, storeName, loginEmail, loginUrl, temporaryPassword);
    }

    private String normalizeRecipient(String toEmail) {
        if (toEmail == null || toEmail.isBlank()) {
            return null;
        }
        String recipient = toEmail.trim();
        if (recipient.length() > 100 || !EMAIL_PATTERN.matcher(recipient).matches()) {
            return null;
        }
        return recipient;
    }

    private String displayName(String candidateName) {
        if (candidateName == null || candidateName.isBlank()) {
            return "ứng viên";
        }
        return candidateName.trim();
    }

    private String buildRejectionBody(String candidateName, String rejectReason) {
        String name = candidateName == null || candidateName.isBlank() ? "bạn" : candidateName.trim();
        StringBuilder body = new StringBuilder();
        body.append("Kính gửi ").append(name).append(",\n\n");
        body.append("Cảm ơn bạn đã quan tâm và dành thời gian tham gia quy trình tuyển dụng tại CoffeeHRM.\n\n");
        body.append("Sau khi xem xét thông tin ứng tuyển, chúng tôi rất tiếc phải thông báo rằng bạn chưa phù hợp với yêu cầu tuyển dụng trong đợt này.\n");
        if (rejectReason != null && !rejectReason.isBlank()) {
            body.append('\n').append("Lý do: ").append(rejectReason.trim()).append('\n');
        }
        body.append("\nChúng tôi chân thành cảm ơn sự quan tâm của bạn và chúc bạn sớm tìm được cơ hội nghề nghiệp phù hợp.\n\n");
        body.append("Trân trọng,\n");
        body.append("Bộ phận Tuyển dụng – CoffeeHRM\n");
        return body.toString();
    }

    private BusinessException mailFailure() {
        return new BusinessException(
                "Không gửi được email mật khẩu tạm thời. Hệ thống chưa tạo tài khoản. Vui lòng kiểm tra cấu hình email rồi duyệt lại.");
    }
}
