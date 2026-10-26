package com.example.coffee_hrm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"employee", "role"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserId")
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "Username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "PasswordHash", nullable = false, length = 255)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RoleId", nullable = false)
    private Role role;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", referencedColumnName = "EmployeeId", unique = true, nullable = true)
    private Employee employee;

    @Column(name = "IsActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Cột giữ lại cho dữ liệu cũ. Tài khoản Manager mới không bị buộc đổi mật khẩu. */
    @Column(name = "MustChangePassword", nullable = false)
    @Builder.Default
    private Boolean mustChangePassword = false;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "LastLogin")
    private LocalDateTime lastLogin;

    @PrePersist
    void defaultMustChangePassword() {
        if (mustChangePassword == null) {
            mustChangePassword = false;
        }
    }
}

