package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "TrainingSkills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"createdBy", "classes"})
public class TrainingSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SkillId")
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "SkillName", nullable = false, unique = true, length = 100)
    private String skillName;

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "Requirements", length = 500)
    private String requirements;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private TrainingSkillStatus status = TrainingSkillStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedBy")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "skill", fetch = FetchType.LAZY)
    private List<TrainingClass> classes = new ArrayList<>();
}
