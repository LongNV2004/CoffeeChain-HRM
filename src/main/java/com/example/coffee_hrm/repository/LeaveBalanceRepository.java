package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Integer> {
    @Query(value = """
            SELECT UsedDays FROM dbo.LeaveBalances
            WHERE EmployeeId = :employeeId AND [Year] = :year
            """, nativeQuery = true)
    Optional<Integer> findUsedDays(@Param("employeeId") Integer employeeId,
                                   @Param("year") int year);

    @Query(value = """
            SELECT UsedSlots FROM dbo.LeaveBalances
            WHERE EmployeeId = :employeeId AND [Year] = :year
            """, nativeQuery = true)
    Optional<Integer> findUsedSlots(@Param("employeeId") Integer employeeId,
                                    @Param("year") int year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM LeaveBalance b WHERE b.employee.id = :employeeId AND b.year = :year")
    Optional<LeaveBalance> lockByEmployeeIdAndYear(@Param("employeeId") Integer employeeId,
                                                   @Param("year") Integer year);
}
