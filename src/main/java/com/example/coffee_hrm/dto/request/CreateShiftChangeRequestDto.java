package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShiftChangeRequestDto {

    @NotNull(message = "Vui lòng chọn ca làm việc cần đổi.")
    private Integer assignmentId;

    @Builder.Default
    private String changeType = "SWAP"; // "SWAP", "TRANSFER", "MANAGER_ASSIGN"

    private Integer targetAssignmentId;

    private Integer targetEmployeeId;

    @NotBlank(message = "Vui lòng nhập lý do đổi ca.")
    @Size(max = 255, message = "Lý do tối đa 255 ký tự.")
    private String reason;
}
