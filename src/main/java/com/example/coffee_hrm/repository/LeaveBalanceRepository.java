package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.LeaveBalance;
import com.example.coffee_hrm.entity.LeaveBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, LeaveBalanceId> {
    // Native scalar read avoids relying on the currently mismatched LeaveBalance entity primary key.
    @Query(value = """
            SELECT UsedDays
            FROM dbo.LeaveBalances
            WHERE EmployeeId = :employeeId AND [Year] = :year
            """, nativeQuery = true)
    Optional<Integer> findUsedDays(@Param("employeeId") Integer employeeId,
                                   @Param("year") int year);
}
