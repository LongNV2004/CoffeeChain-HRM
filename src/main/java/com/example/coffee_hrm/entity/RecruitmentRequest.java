package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.RecruitmentStatus;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "RecruitmentRequests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RequestId")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId", nullable = false)
    private Store store;

    @Column(name = "RequestedNumber", nullable = false)
    private Integer requestedNumber;

    @Column(name = "Reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "Status", nullable = false)
    @Builder.Default
    private RecruitmentStatus status = RecruitmentStatus.PENDING;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}