package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.entity.TrainingAttendance;
import com.example.coffee_hrm.entity.TrainingClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, Integer> {

    Optional<TrainingAttendance> findByEmployee_IdAndTrainingClass_IdAndWorkDate(
            Integer employeeId, Integer classId, LocalDate workDate);

    @Query("""
            SELECT t FROM TrainingAttendance t
            WHERE t.employee.id = :employeeId
              AND t.trainingClass.id = :classId
              AND t.checkInTime IS NOT NULL
              AND t.checkOutTime IS NULL
            ORDER BY t.workDate DESC, t.id DESC
            """)
    List<TrainingAttendance> findOpenByEmployeeAndClass(@Param("employeeId") Integer employeeId,
                                                        @Param("classId") Integer classId);

    @Query("""
            SELECT t FROM TrainingAttendance t
            JOIN FETCH t.trainingClass c
            WHERE t.employee.id = :employeeId
              AND t.checkInTime IS NOT NULL
              AND t.checkOutTime IS NULL
            ORDER BY t.workDate DESC, t.id DESC
            """)
    List<TrainingAttendance> findOpenByEmployee(@Param("employeeId") Integer employeeId);

    @Query("""
            SELECT DISTINCT c FROM TrainingClass c
            LEFT JOIN FETCH c.store
            LEFT JOIN FETCH c.participatingStores
            LEFT JOIN FETCH c.trainer trainerUser
            LEFT JOIN FETCH trainerUser.employee
            WHERE c.id = :id
            """)
    Optional<TrainingClass> findClassForAttendance(@Param("id") Integer id);

    @Query("""
            SELECT DISTINCT c FROM TrainingClass c
            LEFT JOIN FETCH c.store
            LEFT JOIN FETCH c.trainer trainerUser
            WHERE trainerUser.id = :trainerUserId
              AND c.status = :status
            ORDER BY c.startDate DESC, c.id DESC
            """)
    List<TrainingClass> findApprovedClassesForTrainer(@Param("trainerUserId") Integer trainerUserId,
                                                      @Param("status") TrainingClassStatus status);

    @Query("""
            SELECT t FROM TrainingAttendance t
            JOIN FETCH t.employee e
            LEFT JOIN FETCH e.store
            LEFT JOIN FETCH t.store
            LEFT JOIN FETCH t.trainingClass
            WHERE e.id = :employeeId
              AND t.workDate >= :fromDate
              AND t.workDate <= :toDate
            ORDER BY t.workDate DESC, t.id DESC
            """)
    List<TrainingAttendance> findHistoryByEmployee(@Param("employeeId") Integer employeeId,
                                                   @Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT t FROM TrainingAttendance t
            JOIN FETCH t.employee e
            LEFT JOIN FETCH e.store
            LEFT JOIN FETCH t.store
            LEFT JOIN FETCH t.trainingClass
            WHERE t.workDate >= :fromDate
              AND t.workDate <= :toDate
              AND (e.store.id = :storeId OR e.id = :managerEmployeeId)
            ORDER BY t.workDate DESC, e.fullName ASC, t.id DESC
            """)
    List<TrainingAttendance> findHistoryForManager(@Param("storeId") Integer storeId,
                                                   @Param("managerEmployeeId") Integer managerEmployeeId,
                                                   @Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT t FROM TrainingAttendance t
            JOIN FETCH t.employee e
            LEFT JOIN FETCH e.store
            LEFT JOIN FETCH t.store
            LEFT JOIN FETCH t.trainingClass
            WHERE t.workDate >= :fromDate
              AND t.workDate <= :toDate
            ORDER BY t.workDate DESC, e.fullName ASC, t.id DESC
            """)
    List<TrainingAttendance> findHistoryBetween(@Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT t FROM TrainingAttendance t
            JOIN FETCH t.employee e
            JOIN FETCH e.store
            LEFT JOIN FETCH e.user u
            LEFT JOIN FETCH u.role
            LEFT JOIN FETCH t.store
            LEFT JOIN FETCH t.trainingClass c
            LEFT JOIN FETCH c.trainer trainer
            LEFT JOIN FETCH trainer.employee
            WHERE t.store.id = :storeId
              AND e.store.id = :storeId
              AND t.workDate >= :fromDate
              AND t.workDate <= :toDate
            ORDER BY t.workDate ASC, e.fullName ASC, t.id ASC
            """)
    List<TrainingAttendance> findForMonthlyReport(@Param("storeId") Integer storeId,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT t.store.id AS storeId,
                   MAX(COALESCE(t.updatedAt, t.createdAt)) AS changedAt
            FROM TrainingAttendance t
            WHERE t.store.id = t.employee.store.id
              AND t.workDate >= :fromDate
              AND t.workDate <= :toDate
            GROUP BY t.store.id
            """)
    List<StoreReportChange> findMonthlyReportChanges(@Param("fromDate") LocalDate fromDate,
                                                     @Param("toDate") LocalDate toDate);

    interface StoreReportChange {
        Integer getStoreId();

        java.time.LocalDateTime getChangedAt();
    }
}
