package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.entity.TrainingClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrainingClassRepository extends JpaRepository<TrainingClass, Integer> {

    @Query("""
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill
            JOIN FETCH c.store
            JOIN FETCH c.createdBy cb
            LEFT JOIN FETCH cb.employee
            LEFT JOIN FETCH c.approvedBy
            WHERE c.store.id = :storeId
            ORDER BY c.createdAt DESC
            """)
    List<TrainingClass> findByStoreIdWithDetails(@Param("storeId") Integer storeId);

    @Query(value = """
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill s
            JOIN FETCH c.store
            JOIN FETCH c.createdBy cb
            LEFT JOIN FETCH cb.employee
            LEFT JOIN FETCH c.approvedBy
            WHERE c.store.id = :storeId
              AND c.status = :status
              AND c.endDate >= :today
              AND (:skillId IS NULL OR s.id = :skillId)
              AND (:date IS NULL OR (c.startDate <= :date AND c.endDate >= :date))
              AND (
                    :keyword IS NULL
                    OR LOWER(c.className) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.skillName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(c.trainer) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(c.notes) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
            """,
            countQuery = """
            SELECT COUNT(c) FROM TrainingClass c
            JOIN c.skill s
            WHERE c.store.id = :storeId
              AND c.status = :status
              AND c.endDate >= :today
              AND (:skillId IS NULL OR s.id = :skillId)
              AND (:date IS NULL OR (c.startDate <= :date AND c.endDate >= :date))
              AND (
                    :keyword IS NULL
                    OR LOWER(c.className) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.skillName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(c.trainer) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(c.notes) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
            """)
    Page<TrainingClass> searchApprovedActiveForStore(@Param("storeId") Integer storeId,
                                                     @Param("status") TrainingClassStatus status,
                                                     @Param("today") LocalDate today,
                                                     @Param("skillId") Integer skillId,
                                                     @Param("date") LocalDate date,
                                                     @Param("keyword") String keyword,
                                                     Pageable pageable);

    @Query("""
            SELECT c FROM TrainingClass c
            WHERE c.store.id = :storeId
              AND c.status <> :rejectedStatus
            """)
    List<TrainingClass> findActiveByStoreId(@Param("storeId") Integer storeId,
                                            @Param("rejectedStatus") TrainingClassStatus rejectedStatus);

    @Query("""
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill
            JOIN FETCH c.store
            JOIN FETCH c.createdBy cb
            LEFT JOIN FETCH cb.employee
            LEFT JOIN FETCH c.approvedBy
            WHERE c.status = :status
            ORDER BY c.createdAt ASC
            """)
    List<TrainingClass> findByStatusWithDetails(@Param("status") TrainingClassStatus status);

    @Query("""
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill
            JOIN FETCH c.store
            JOIN FETCH c.createdBy cb
            LEFT JOIN FETCH cb.employee
            LEFT JOIN FETCH c.approvedBy
            ORDER BY c.createdAt DESC
            """)
    List<TrainingClass> findAllWithDetails();

    @Query("""
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill
            JOIN FETCH c.store
            JOIN FETCH c.createdBy cb
            LEFT JOIN FETCH cb.employee
            LEFT JOIN FETCH c.approvedBy
            WHERE c.id = :id
            """)
    Optional<TrainingClass> findByIdWithDetails(@Param("id") Integer id);

    long countByStatus(TrainingClassStatus status);
}
