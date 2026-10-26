package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.ReliabilityScoreLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReliabilityScoreLogRepository extends JpaRepository<ReliabilityScoreLog, Integer> {

    @Query("""
            SELECT l
            FROM ReliabilityScoreLog l
            JOIN FETCH l.employee e
            WHERE l.store.id = :storeId
              AND l.createdAt >= :start
              AND l.createdAt < :endExclusive
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<ReliabilityScoreLog> findForStoreInRange(
            @Param("storeId") Integer storeId,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
            SELECT l
            FROM ReliabilityScoreLog l
            JOIN FETCH l.employee e
            WHERE l.store.id = :storeId
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<ReliabilityScoreLog> findRecentForStore(
            @Param("storeId") Integer storeId,
            Pageable pageable);

    @Query("""
            SELECT l
            FROM ReliabilityScoreLog l
            JOIN FETCH l.employee e
            WHERE l.employee.id = :employeeId
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<ReliabilityScoreLog> findRecentForEmployee(
            @Param("employeeId") Integer employeeId,
            Pageable pageable);

    boolean existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
            Integer employeeId,
            String actionType,
            String referenceType,
            Integer referenceId);

    long countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
            Integer employeeId,
            Collection<String> actionTypes,
            LocalDateTime start,
            LocalDateTime end);
}
