package com.example.coffee_hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Số người cần cho một kỹ năng (vị trí làm việc) trong một ca của một thứ.
 * Kỹ năng lấy từ danh mục đào tạo, không có danh mục vị trí riêng.
 */
@Entity
@Table(name = "ShiftSlotPositionNeeds",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_ShiftSlotPositionNeeds_Limit_Skill",
                columnNames = {"LimitId", "SkillId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"slotLimit", "skill"})
public class ShiftSlotPositionNeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NeedId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LimitId", nullable = false)
    private ShiftSlotLimit slotLimit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SkillId", nullable = false)
    private TrainingSkill skill;

    @Column(name = "RequiredCount", nullable = false)
    private Integer requiredCount;
}
