package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    @Query("""
            SELECT u FROM User u
            JOIN FETCH u.role
            LEFT JOIN FETCH u.employee e
            LEFT JOIN FETCH e.store
            WHERE u.username = :username
            """)
    Optional<User> findByUsername(@Param("username") String username);

    Optional<User> findByEmployee_Id(Integer employeeId);

    @Query("""
            SELECT u FROM User u
            JOIN FETCH u.role
            LEFT JOIN FETCH u.employee e
            LEFT JOIN FETCH e.store
            WHERE u.id = :id
            """)
    Optional<User> findByIdWithRole(@Param("id") Integer id);

    @Query("""
            SELECT u FROM User u
            JOIN FETCH u.role r
            LEFT JOIN FETCH u.employee e
            LEFT JOIN FETCH e.store
            WHERE r.roleName = :roleName
              AND u.isActive = true
            """)
    List<User> findActiveByRoleName(@Param("roleName") RoleName roleName);

    @Query("""
            SELECT e.id FROM User u
            JOIN u.role r
            JOIN u.employee e
            WHERE r.roleName = :roleName
            """)
    List<Integer> findEmployeeIdsByRoleName(@Param("roleName") RoleName roleName);
}
