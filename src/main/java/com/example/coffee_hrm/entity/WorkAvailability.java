package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "WorkAvailabilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"employee", "shift"})
public class WorkAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AvailabilityId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ShiftId", nullable = false)
    private Shift shift;

    /** Ngày cụ thể của đăng ký cũ. Bản định kỳ để trống và dùng thứ + thời hạn. */
    @Column(name = "WorkDate")
    private LocalDate workDate;

    /** 1 = thứ Hai … 7 = Chủ nhật. Null với bản ghi theo từng ngày trước đây. */
    @Column(name = "DayOfWeek")
    private Integer dayOfWeek;

    @Column(name = "ValidFrom")
    private LocalDate validFrom;

    @Column(name = "ValidTo")
    private LocalDate validTo;

    @Column(name = "DurationCode", length = 20)
    private String durationCode;

    @Column(name = "RegistrationKey", length = 36)
    private String registrationKey;

    @Column(name = "Note", length = 255)
    private String note;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
