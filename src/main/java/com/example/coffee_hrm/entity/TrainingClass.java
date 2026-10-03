package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "TrainingClasses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"skills", "store", "participatingStores", "createdBy", "trainer", "approvedBy", "enrollments"})
public class TrainingClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ClassId")
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "TrainingType", nullable = false, length = 30)
    @Builder.Default
    private TrainingType trainingType = TrainingType.STORE_TRAINING;

    @BatchSize(size = 32)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "TrainingClassSkills",
            joinColumns = @JoinColumn(name = "ClassId"),
            inverseJoinColumns = @JoinColumn(name = "SkillId"))
    @Builder.Default
    private Set<TrainingSkill> skills = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId")
    private Store store;

    @BatchSize(size = 32)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "TrainingClassStores",
            joinColumns = @JoinColumn(name = "ClassId"),
            inverseJoinColumns = @JoinColumn(name = "StoreId"))
    @Builder.Default
    private Set<Store> participatingStores = new LinkedHashSet<>();

    @Column(name = "ClassName", nullable = false, length = 150)
    private String className;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TrainerId")
    private User trainer;

    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;

    @Column(name = "EndDate", nullable = false)
    private LocalDate endDate;

    @Column(name = "StartTime")
    private LocalTime startTime;

    @Column(name = "EndTime")
    private LocalTime endTime;

    @Column(name = "Location", length = 255)
    private String location;

    @Column(name = "MaxParticipants")
    private Integer maxParticipants;

    @Column(name = "Notes", length = 500)
    private String notes;

    @Column(name = "Status", nullable = false, length = 30)
    @Builder.Default
    private TrainingClassStatus status = TrainingClassStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CreatedBy", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    @Column(name = "ApprovedAt")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "trainingClass", cascade = CascadeType.REMOVE)
    private List<TrainingClassEnrollment> enrollments = new ArrayList<>();
}
