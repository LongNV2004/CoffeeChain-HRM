package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTrainingSkillRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Vui lòng nhập tên kỹ năng")
    @Size(max = 100, message = "Tên kỹ năng tối đa 100 ký tự")
    @Pattern(regexp = "^[^0-9]*$", message = "Tên kỹ năng không được chứa số")
    private String skillName;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    @Size(max = 500, message = "Yêu cầu tối đa 500 ký tự")
    private String requirements;

    public void setSkillName(String skillName) {
        this.skillName = skillName == null ? null : skillName.trim();
    }
}
