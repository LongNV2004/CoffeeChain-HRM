package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "LeaveRequests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"employee", "approvedBy"})
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LeaveRequestId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;

    @Column(name = "EndDate", nullable = false)
    private LocalDate endDate;

    @Column(name = "Reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /** Manager đã duyệt/từ chối đơn (nullable cho tới khi được xử lý). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ResolvedDate")
    private LocalDateTime resolvedDate;
}

