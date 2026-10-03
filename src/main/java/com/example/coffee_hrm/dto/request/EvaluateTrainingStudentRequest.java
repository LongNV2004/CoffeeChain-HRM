package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.TrainingResult;
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
public class EvaluateTrainingStudentRequest {

    @NotNull(message = "Vui lòng chọn kết quả đánh giá.")
    private TrainingResult result;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
    private String note;

    private boolean updating;
}
