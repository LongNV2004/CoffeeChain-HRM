package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.TrainingParticipantStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TrainingParticipants",
        uniqueConstraints = @UniqueConstraint(name = "UQ_TrainingParticipants_SessionEmployee",
                columnNames = {"SessionId", "EmployeeId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"session", "employee"})
public class TrainingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ParticipantId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SessionId", nullable = false)
    private TrainingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private TrainingParticipantStatus status = TrainingParticipantStatus.REGISTERED;

    @Column(name = "Score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "Result", length = 10)
    private TrainingResult result;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
