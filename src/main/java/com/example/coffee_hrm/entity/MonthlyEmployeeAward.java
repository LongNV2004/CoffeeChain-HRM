package com.example.coffee_hrm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Danh hiệu tháng đã được Quản lý chốt: Hạng 1 = "Nhân viên xuất sắc tháng", Hạng 2–3 = "Top tháng".
 * Danh hiệu là kết quả của tháng, không cộng thêm điểm chuyên cần.
 */
@Entity
@Table(name = "MonthlyEmployeeAwards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"store", "employee", "decidedBy"})
public class MonthlyEmployeeAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AwardId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId", nullable = false)
    private Store store;

    /** Định dạng yyyy-MM, ví dụ 2026-10. */
    @Column(name = "AwardMonth", nullable = false, length = 7)
    private String awardMonth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "AwardRank", nullable = false)
    private Integer awardRank;

    @Column(name = "MonthScore", nullable = false)
    private Integer monthScore;

    @Column(name = "OverrideReason", length = 255)
    private String overrideReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DecidedBy", nullable = false)
    private User decidedBy;

    @Column(name = "DecidedAt", nullable = false)
    private LocalDateTime decidedAt;
}

