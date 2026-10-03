package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.TrainingResult;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "TrainingClassEnrollments",
        uniqueConstraints = @UniqueConstraint(name = "UQ_TrainingClassEnrollments_ClassEmployee",
                columnNames = {"ClassId", "EmployeeId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"trainingClass", "employee", "evaluatedBy"})
public class TrainingClassEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EnrollmentId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ClassId", nullable = false)
    private TrainingClass trainingClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "Result", length = 10)
    private TrainingResult result;

    @Column(name = "EvaluationNote", length = 500)
    private String evaluationNote;

    @Column(name = "EvaluatedAt")
    private LocalDateTime evaluatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EvaluatedBy")
    private User evaluatedBy;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
