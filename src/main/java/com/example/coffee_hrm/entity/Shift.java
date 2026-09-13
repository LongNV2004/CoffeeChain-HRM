package com.example.coffee_hrm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Shifts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"StoreId", "ShiftName"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"store", "assignments"})
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ShiftId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId", nullable = false)
    private Store store;

    @Column(name = "ShiftName", nullable = false, length = 50)
    private String shiftName;

    @Column(name = "StartTime", nullable = false)
    private LocalTime startTime;

    @Column(name = "EndTime", nullable = false)
    private LocalTime endTime;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "shift", fetch = FetchType.LAZY)
    private List<ShiftAssignment> assignments = new ArrayList<>();
}