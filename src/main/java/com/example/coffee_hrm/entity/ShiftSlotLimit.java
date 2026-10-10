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

import java.time.LocalDateTime;

/**
 * Số nhân viên tối đa của một ca tại một thứ trong tuần.
 * Giới hạn gắn với ca của cửa hàng và thứ (1 = thứ Hai … 7 = Chủ nhật),
 * không gắn với một tuần hay một tháng cụ thể.
 */
@Entity
@Table(name = "ShiftSlotLimits",
        uniqueConstraints = @UniqueConstraint(name = "UQ_ShiftSlotLimits_Shift_Day", columnNames = {"ShiftId", "DayOfWeek"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "shift")
public class ShiftSlotLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LimitId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ShiftId", nullable = false)
    private Shift shift;

    /** 1 = thứ Hai … 7 = Chủ nhật. Cùng quy ước với {@code WorkAvailabilities.DayOfWeek}. */
    @Column(name = "DayOfWeek", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "MaxEmployees", nullable = false)
    private Integer maxEmployees;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;
}
