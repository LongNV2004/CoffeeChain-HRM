package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
