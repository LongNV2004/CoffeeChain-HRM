package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewLeaveRequest {
    @NotNull(message = "Vui lòng chọn hành động xử lý đơn.")
    private ApprovalStatus status;

    @Size(max = 300, message = "Lý do từ chối không được vượt quá 300 ký tự.")
    private String rejectReason;
}
