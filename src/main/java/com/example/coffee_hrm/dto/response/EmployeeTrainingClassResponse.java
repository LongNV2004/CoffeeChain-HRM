package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.TrainingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeTrainingClassResponse {

    private Integer id;
    private String className;
    private String skillName;
    private String storeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String trainer;
    private String supervisorName;
    private String location;
    private String notes;
    /** Ngày đào tạo, một ngày hoặc khoảng ngày. */
    private String dateLabel;
    /** Địa điểm và cơ sở, gộp để hiển thị trên danh sách. */
    private String placeLabel;
    private String employeeName;
    /** Nhân viên đã có enrollment của chính lớp này. */
    private String enrollmentStatus;
    /** Đã hoàn thành khi lớp kết thúc, ngược lại chưa hoàn thành. */
    private String completionStatus;
    /** Sắp diễn ra, Đang diễn ra hoặc Đã kết thúc. */
    private String participationStatus;
    private boolean ended;
    private TrainingResult result;
    private String resultLabel;
    /** PASS hoặc NOT PASS. Null khi chưa có đánh giá. */
    private String resultCode;
    @Builder.Default
    private List<String> passedSkillNames = List.of();
    @Builder.Default
    private List<String> notPassedSkillNames = List.of();
    private String evaluationNote;
    private LocalDateTime evaluatedAt;
}
