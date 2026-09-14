package com.example.coffee_hrm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"manager", "employees", "operatingHours", "shifts", "trainingCourses"})
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "StoreId")
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "StoreName", nullable = false, length = 100)
    private String storeName;

    @Column(name = "Address", nullable = false, length = 255)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ManagerId", referencedColumnName = "EmployeeId")
    private Employee manager;

    @Column(name = "TotalLeaveDays", nullable = false)
    @Builder.Default
    private Integer totalLeaveDays = 12;

    @Column(name = "IsActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "store", fetch = FetchType.LAZY)
    private List<Employee> employees = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "store", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoreOperatingHour> operatingHours = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "store", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Shift> shifts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "store", fetch = FetchType.LAZY)
    private List<TrainingCourse> trainingCourses = new ArrayList<>();
}