package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResignationItemRequest {

    @NotNull(message = "Vui lòng chọn nhân viên")
    private Integer employeeId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate proposedTerminationDate;

    @NotBlank(message = "Vui lòng nhập lý do đề xuất nghỉ việc")
    @Size(max = 500, message = "Lý do đề xuất nghỉ việc tối đa 500 ký tự")
    private String reason;

    @Size(max = 500, message = "Ghi chú riêng tối đa 500 ký tự")
    private String note;

    public void setReason(String reason) {
        this.reason = blankToNull(reason);
    }

    public void setNote(String note) {
        this.note = blankToNull(note);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
