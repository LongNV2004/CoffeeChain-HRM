package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"store", "user", "shiftAssignments", "shiftChangeRequests",
        "attendances", "leaveRequests", "leaveBalances"})
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EmployeeId")
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "FullName", nullable = false, length = 100)
    private String fullName;

    @Column(name = "Phone", length = 15)
    private String phone;

    @Column(name = "Email", unique = true, length = 100)
    private String email;

    @Column(name = "Address", length = 255)
    private String address;

    @Column(name = "AvatarUrl", length = 255)
    private String avatarUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "HireDate", nullable = false)
    private LocalDate hireDate;

    @Column(name = "TerminationDate")
    private LocalDate terminationDate;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "employee", fetch = FetchType.LAZY)
    private User user;

    @Builder.Default
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<ShiftAssignment> shiftAssignments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<ShiftChangeRequest> shiftChangeRequests = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<Attendance> attendances = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<LeaveRequest> leaveRequests = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<LeaveBalance> leaveBalances = new ArrayList<>();
}
