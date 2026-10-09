package com.example.coffee_hrm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRecruitmentRequest {

    @NotBlank(message = "Vui lòng nhập tên đề xuất")
    @Size(max = 150, message = "Tên đề xuất tối đa 150 ký tự")
    private String title;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;

    @Builder.Default
    @NotEmpty(message = "Vui lòng thêm ít nhất một nhân viên vào danh sách")
    @Size(max = 30, message = "Mỗi đề xuất chứa tối đa 30 nhân viên")
    @Valid
    private List<RecruitmentCandidateRequest> candidates = new ArrayList<>();

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            this.title = null;
            return;
        }
        this.title = title.trim();
    }

    public void setNote(String note) {
        if (note == null || note.isBlank()) {
            this.note = null;
            return;
        }
        this.note = note.trim();
    }
}
