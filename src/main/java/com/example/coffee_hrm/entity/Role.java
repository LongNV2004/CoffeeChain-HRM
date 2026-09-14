package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.RoleName;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "users")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoleId")
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "RoleName", nullable = false, unique = true, length = 20)
    private RoleName roleName;

    @Builder.Default
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();
}

