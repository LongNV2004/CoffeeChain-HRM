package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByRoleName(RoleName roleName);
}
