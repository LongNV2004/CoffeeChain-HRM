package com.example.coffee_hrm.service;

public interface AccountMailService {

    void sendTemporaryPassword(String toEmail, String employeeName, String loginEmail, String temporaryPassword);

    /**
     * Gửi email thông báo không trúng tuyển.
     * Không ném exception: địa chỉ không hợp lệ hoặc lỗi SMTP được ghi log và trả về false
     * để trạng thái từ chối đã lưu không bị hoàn tác.
     */
    boolean sendRecruitmentRejection(String toEmail, String candidateName, String rejectReason);
}
