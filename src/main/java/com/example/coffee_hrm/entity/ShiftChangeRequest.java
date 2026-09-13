package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ShiftChangeRequests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"assignment", "employee", "resolvedBy"})
public class ShiftChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RequestId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AssignmentId", nullable = false)
    private ShiftAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "Reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "RequestDate", nullable = false)
    @Builder.Default
    private LocalDateTime requestDate = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedBy")
    private User resolvedBy;

    @Column(name = "ResolvedDate")
    private LocalDateTime resolvedDate;
}

