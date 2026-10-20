package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLeaveRequest {
    @NotNull(message = "Vui lòng chọn loại nghỉ.")
    private LeaveType leaveType;

    @NotEmpty(message = "Vui lòng chọn ít nhất một ca làm việc.")
    @Size(max = 60, message = "Một đơn chỉ nhận tối đa 60 ca.")
    private List<Integer> assignmentIds;

    @NotBlank(message = "Vui lòng nhập lý do xin nghỉ.")
    @Size(max = 255, message = "Lý do không được vượt quá 255 ký tự.")
    private String reason;

    private Integer handoverEmployeeId;

    @Pattern(regexp = "^[0-9]{9,15}$", message = "Số điện thoại gồm 9 đến 15 chữ số.")
    private String contactPhone;

    public void setContactPhone(String contactPhone) {
        if (contactPhone == null || contactPhone.isBlank()) {
            this.contactPhone = null;
            return;
        }
        this.contactPhone = contactPhone.replaceAll("[\\s.\\-]", "");
    }

    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.trim();
    }
}
