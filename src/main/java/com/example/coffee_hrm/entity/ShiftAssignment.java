package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ShiftAssignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"EmployeeId", "ShiftId", "WorkDate"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"shift", "employee", "changeRequests"})
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
    private Boolean isPublished = false;

    @Column(name = "PublishedAt")
    private LocalDateTime publishedAt;

    @Builder.Default
    @OneToMany(mappedBy = "assignment", fetch = FetchType.LAZY)
    private List<ShiftChangeRequest> changeRequests = new ArrayList<>();
}

