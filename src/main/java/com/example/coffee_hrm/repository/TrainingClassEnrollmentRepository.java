package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrainingClassEnrollmentRepository extends JpaRepository<TrainingClassEnrollment, Integer> {

    @Query("""
            SELECT e FROM TrainingClassEnrollment e
            JOIN FETCH e.employee
            WHERE e.trainingClass.id = :classId
            ORDER BY e.employee.fullName ASC
            """)
    List<TrainingClassEnrollment> findByClassIdWithEmployee(@Param("classId") Integer classId);

    @Query("""
            SELECT e FROM TrainingClassEnrollment e
            JOIN FETCH e.employee emp
            JOIN FETCH emp.store
            WHERE e.trainingClass.id = :classId
              AND emp.id = :employeeId
            """)
    Optional<TrainingClassEnrollment> findByClassAndEmployee(@Param("classId") Integer classId,
                                                             @Param("employeeId") Integer employeeId);

    @Query("SELECT e.employee.id FROM TrainingClassEnrollment e WHERE e.trainingClass.id = :classId")
    List<Integer> findEmployeeIdsByClassId(@Param("classId") Integer classId);

    boolean existsByTrainingClass_IdAndEmployee_Id(Integer classId, Integer employeeId);

    long countByTrainingClass_Id(Integer classId);

    @Query("""
            SELECT e FROM TrainingClassEnrollment e
            JOIN FETCH e.trainingClass c
            LEFT JOIN FETCH c.store
            LEFT JOIN FETCH c.trainer trainerUser
            LEFT JOIN FETCH trainerUser.employee
            LEFT JOIN FETCH c.createdBy creator
            LEFT JOIN FETCH creator.employee
            WHERE e.employee.id = :employeeId
              AND c.status = :status
            ORDER BY c.startDate DESC, c.id DESC
            """)
    List<TrainingClassEnrollment> findByEmployeeAndStatusWithClass(@Param("employeeId") Integer employeeId,
                                                                    @Param("status") TrainingClassStatus status);

    @Query("""
            SELECT e FROM TrainingClassEnrollment e
            JOIN FETCH e.trainingClass c
            LEFT JOIN FETCH c.store
            LEFT JOIN FETCH c.trainer trainerUser
            LEFT JOIN FETCH trainerUser.employee
            LEFT JOIN FETCH c.createdBy creator
            LEFT JOIN FETCH creator.employee
            WHERE c.id = :classId
              AND e.employee.id = :employeeId
            """)
    Optional<TrainingClassEnrollment> findOwnedByClassAndEmployee(@Param("classId") Integer classId,
                                                                  @Param("employeeId") Integer employeeId);

    @Query("""
            SELECT c FROM TrainingClassEnrollment e
            JOIN e.trainingClass c
            WHERE e.employee.id = :employeeId
              AND c.status = :status
              AND c.endDate >= :today
            """)
    List<TrainingClass> findClassesEndingOnOrAfter(@Param("employeeId") Integer employeeId,
                                                   @Param("status") TrainingClassStatus status,
                                                   @Param("today") LocalDate today);
}
