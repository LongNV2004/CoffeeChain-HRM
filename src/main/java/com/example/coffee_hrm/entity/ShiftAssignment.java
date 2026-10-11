package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.time.VietnamTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ShiftAssignments", uniqueConstraints =
        @UniqueConstraint(columnNames = {"EmployeeId", "ShiftId", "WorkDate"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"shift", "employee", "changeRequests", "cancelledByLeaveRequest",
        "replacesAssignment", "replacementConfirmedBy"})
public class ShiftAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AssignmentId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ShiftId", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "WorkDate", nullable = false)
    private LocalDate workDate;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "IsPublished", nullable = false)
    @Builder.Default
    private Boolean isPublished = true;

    @Column(name = "PublishedAt")
    private LocalDateTime publishedAt;

    /** Set only when an APPROVED leave cancels this assignment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CancelledByLeaveRequestId")
    private LeaveRequest cancelledByLeaveRequest;

    /** Original CANCELLED assignment replaced by this assignment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReplacesAssignmentId")
    private ShiftAssignment replacesAssignment;

    @Column(name = "ReplacementDeferred", nullable = false)
    @Builder.Default
    private Boolean replacementDeferred = false;

    /** Assignment creation timestamp serves as replacement confirmation timestamp. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReplacementConfirmedBy")
    private User replacementConfirmedBy;

    @PrePersist
    public void prePersist() {
        if (isPublished == null) isPublished = true;
        if (publishedAt == null && Boolean.TRUE.equals(isPublished)) {
            publishedAt = VietnamTime.now();
        }
    }

    @Builder.Default
    @OneToMany(mappedBy = "assignment", fetch = FetchType.LAZY)
    private List<ShiftChangeRequest> changeRequests = new ArrayList<>();
}
