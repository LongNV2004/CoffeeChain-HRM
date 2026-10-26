package com.example.coffee_hrm.service;

import java.time.LocalDateTime;

public interface AccountMailService {

    void sendTemporaryPassword(String toEmail, String employeeName, String loginEmail, String temporaryPassword);

    void sendManagerCredentials(String toEmail,
                                String managerName,
                                String storeName,
                                String loginEmail,
                                String temporaryPassword);

    /**
     * Gửi email thông báo không trúng tuyển.
     * Không ném exception: địa chỉ không hợp lệ hoặc lỗi SMTP được ghi log và trả về false
     * để trạng thái từ chối đã lưu không bị hoàn tác.
     */
    boolean sendRecruitmentRejection(String toEmail, String candidateName, String rejectReason);

    /**
     * Gửi email đề xuất nghỉ việc.
     * Không ném exception: lỗi SMTP được ghi log và trả về false để dữ liệu đã lưu không bị hoàn tác.
     */
    boolean sendResignationNotice(String toEmail, String recipientName, String subject, String body);

    /**
     * Gửi email quyết định nghỉ việc cho nhân viên sau khi Admin đã duyệt.
     * Không ném exception và không ghi mật khẩu hay dữ liệu xác thực vào log.
     */
    boolean sendResignationDecision(String toEmail, String employeeName, String storeName, LocalDateTime decidedAt);
}
