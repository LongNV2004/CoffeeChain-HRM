package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {

    Optional<Attendance> findByEmployee_IdAndWorkDate(Integer employeeId, LocalDate workDate);
}
