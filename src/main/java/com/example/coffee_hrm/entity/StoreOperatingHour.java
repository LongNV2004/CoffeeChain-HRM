package com.example.coffee_hrm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "StoreOperatingHours",
        uniqueConstraints = @UniqueConstraint(columnNames = {"StoreId", "DayOfWeek"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "store")
public class StoreOperatingHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId", nullable = false)
    private Store store;

    @Column(name = "DayOfWeek", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "OpenTime")
    private LocalTime openTime;

    @Column(name = "CloseTime")
    private LocalTime closeTime;

    @Column(name = "IsOpen", nullable = false)
    @Builder.Default
    private Boolean isOpen = true;
}

