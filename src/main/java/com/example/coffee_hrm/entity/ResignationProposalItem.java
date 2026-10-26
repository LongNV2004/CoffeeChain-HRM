package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.ResignationItemStatus;
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
@Table(name = "ResignationProposalItems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"proposal", "employee", "reviewedBy"})
public class ResignationProposalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ItemId")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ProposalId", nullable = false)
    private ResignationProposal proposal;

    @Column(name = "SortOrder", nullable = false)
    private Integer sortOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "Position", nullable = false, length = 50)
    private String position;

    @Column(name = "ProposedTerminationDate", nullable = false)
    private LocalDate proposedTerminationDate;

    @Column(name = "Reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "Note", length = 500)
    private String note;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private ResignationItemStatus status = ResignationItemStatus.PENDING;

    @Column(name = "ApprovedTerminationDate")
    private LocalDate approvedTerminationDate;

    @Column(name = "RejectReason", length = 500)
    private String rejectReason;

    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReviewedByUserId")
    private User reviewedBy;

    @Column(name = "AppliedAt")
    private LocalDateTime appliedAt;

    @Column(name = "DecisionNoticeSentAt")
    private LocalDateTime decisionNoticeSentAt;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
