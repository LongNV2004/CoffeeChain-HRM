package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.Gender;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitmentCandidateResponse {

    private Integer id;
    private String fullName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String genderLabel;
    private String email;
    private String phone;
    private String address;
    private RecruitmentStatus status;
    private String statusLabel;
    private String rejectReason;
    private LocalDateTime reviewedAt;
    private Integer createdEmployeeId;
}
