package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.Gender;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "RecruitmentCandidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"request", "reviewedBy", "createdEmployee"})
public class RecruitmentCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CandidateId")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RequestId", nullable = false)
    private RecruitmentRequest request;

    @Column(name = "SortOrder", nullable = false)
    private Integer sortOrder;

    @Column(name = "FullName", nullable = false, length = 100)
    private String fullName;

    @Column(name = "DateOfBirth")
    private LocalDate dateOfBirth;

    @Column(name = "Gender", length = 20)
    private Gender gender;

    @Column(name = "Email", nullable = false, length = 100)
    private String email;

    @Column(name = "Phone", nullable = false, length = 15)
    private String phone;

    @Column(name = "Address", length = 255)
    private String address;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private RecruitmentStatus status = RecruitmentStatus.PENDING;

    @Column(name = "RejectReason", length = 500)
    private String rejectReason;

    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReviewedByUserId")
    private User reviewedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedEmployeeId")
    private Employee createdEmployee;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
