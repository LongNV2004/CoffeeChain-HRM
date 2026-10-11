package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.MonthlyAttendanceReportVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyAttendanceReportVersionRepository
        extends JpaRepository<MonthlyAttendanceReportVersion, Integer> {

    List<MonthlyAttendanceReportVersion> findByReport_IdOrderByVersionNumberDesc(Integer reportId);

    Optional<MonthlyAttendanceReportVersion> findByReport_IdAndVersionNumber(Integer reportId, Integer versionNumber);
}
