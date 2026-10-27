package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.time.VietnamTime;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ReliabilityScoreLogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"employee", "store"})
public class ReliabilityScoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LogId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId", nullable = false)
    private Store store;

    @Column(name = "ActionType", nullable = false, length = 50)
    private String actionType;

    @Column(name = "PointsDelta", nullable = false)
    private Integer pointsDelta;

    @Column(name = "ScoreAfter", nullable = false)
    private Integer scoreAfter;

    @Column(name = "Reason", nullable = false, length = 300)
    private String reason;

    @Column(name = "ReferenceType", length = 50)
    private String referenceType;

    @Column(name = "ReferenceId")
    private Integer referenceId;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void defaultCreatedAt() {
        if (createdAt == null) {
            createdAt = VietnamTime.now();
        }
    }
}
