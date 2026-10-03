package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingType;
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

    @Query(value = """
            SELECT c FROM TrainingClass c
            LEFT JOIN c.store store
            LEFT JOIN c.trainer trainerUser
            LEFT JOIN trainerUser.employee trainerEmployee
            WHERE c.status = :status
              AND c.endDate >= :today
              AND (
                    (c.trainingType = :storeType AND store.id = :storeId)
                    OR (c.trainingType = com.example.coffee_hrm.common.enums.TrainingType.CENTRALIZED_TRAINING
                        AND trainerUser.id = :trainerUserId)
                  )
              AND (:skillId IS NULL OR EXISTS (
                    SELECT skill FROM TrainingSkill skill
                    WHERE skill MEMBER OF c.skills AND skill.id = :skillId))
              AND (:date IS NULL OR (c.startDate <= :date AND c.endDate >= :date))
              AND (
                    :keyword IS NULL
                    OR LOWER(c.className) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(c.notes) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(trainerUser.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(trainerEmployee.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT skill FROM TrainingSkill skill
                        WHERE skill MEMBER OF c.skills
                          AND LOWER(skill.skillName) LIKE LOWER(CONCAT('%', :keyword, '%')))
                  )
            """,
            countQuery = """
            SELECT COUNT(c) FROM TrainingClass c
            LEFT JOIN c.store store
            LEFT JOIN c.trainer trainerUser
            LEFT JOIN trainerUser.employee trainerEmployee
            WHERE c.status = :status
              AND c.endDate >= :today
              AND (
                    (c.trainingType = :storeType AND store.id = :storeId)
                    OR (c.trainingType = com.example.coffee_hrm.common.enums.TrainingType.CENTRALIZED_TRAINING
                        AND trainerUser.id = :trainerUserId)
                  )
              AND (:skillId IS NULL OR EXISTS (
                    SELECT skill FROM TrainingSkill skill
                    WHERE skill MEMBER OF c.skills AND skill.id = :skillId))
              AND (:date IS NULL OR (c.startDate <= :date AND c.endDate >= :date))
              AND (
                    :keyword IS NULL
                    OR LOWER(c.className) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(c.notes) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(trainerUser.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(trainerEmployee.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT skill FROM TrainingSkill skill
                        WHERE skill MEMBER OF c.skills
                          AND LOWER(skill.skillName) LIKE LOWER(CONCAT('%', :keyword, '%')))
                  )
            """)
    Page<TrainingClass> searchApprovedActiveForManager(@Param("storeId") Integer storeId,
                                                       @Param("trainerUserId") Integer trainerUserId,
                                                       @Param("storeType") TrainingType storeType,
                                                       @Param("status") TrainingClassStatus status,
                                                       @Param("today") LocalDate today,
                                                       @Param("skillId") Integer skillId,
                                                       @Param("date") LocalDate date,
                                                       @Param("keyword") String keyword,
                                                       Pageable pageable);

    @Query("""
            SELECT DISTINCT c FROM TrainingClass c
            LEFT JOIN c.participatingStores participating
            WHERE c.status <> :rejectedStatus
              AND (c.store.id = :storeId OR participating.id = :storeId)
            """)
    List<TrainingClass> findActiveByStoreId(@Param("storeId") Integer storeId,
                                            @Param("rejectedStatus") TrainingClassStatus rejectedStatus);

    @Query("""
            SELECT c FROM TrainingClass c
            LEFT JOIN FETCH c.store
            LEFT JOIN FETCH c.trainer trainerUser
            LEFT JOIN FETCH trainerUser.employee
            JOIN FETCH c.createdBy cb
            LEFT JOIN FETCH cb.employee
            LEFT JOIN FETCH c.approvedBy
            WHERE c.status = :status
            ORDER BY c.createdAt ASC
            """)
    List<TrainingClass> findByStatusWithDetails(@Param("status") TrainingClassStatus status);

    @Query("""
            SELECT c FROM TrainingClass c
            LEFT JOIN FETCH c.store
            LEFT JOIN FETCH c.trainer trainerUser
            LEFT JOIN FETCH trainerUser.employee
            JOIN FETCH c.createdBy cb
            LEFT JOIN FETCH cb.employee
            LEFT JOIN FETCH c.approvedBy
            ORDER BY c.createdAt DESC
            """)
    List<TrainingClass> findAllWithDetails();

    @Query("""
            SELECT c FROM TrainingClass c
            LEFT JOIN FETCH c.store
            LEFT JOIN FETCH c.trainer trainerUser
            LEFT JOIN FETCH trainerUser.employee
            JOIN FETCH c.createdBy cb
            LEFT JOIN FETCH cb.employee
            LEFT JOIN FETCH c.approvedBy
            WHERE c.id = :id
            """)
    Optional<TrainingClass> findByIdWithDetails(@Param("id") Integer id);

    @Query("""
            SELECT c FROM TrainingClass c
            LEFT JOIN c.store store
            LEFT JOIN c.trainer trainerUser
            WHERE c.endDate >= :today
              AND (
                    (c.trainingType = :storeType AND store.id = :storeId)
                    OR (c.trainingType = com.example.coffee_hrm.common.enums.TrainingType.CENTRALIZED_TRAINING
                        AND trainerUser.id = :trainerUserId)
                  )
            ORDER BY c.createdAt DESC
            """)
    List<TrainingClass> findOpenRequestsForManager(@Param("storeId") Integer storeId,
                                                   @Param("trainerUserId") Integer trainerUserId,
                                                   @Param("storeType") TrainingType storeType,
                                                   @Param("today") LocalDate today);

    @Query("""
            SELECT c FROM TrainingClass c
            LEFT JOIN c.store store
            LEFT JOIN c.trainer trainerUser
            WHERE c.status = :status
              AND c.endDate < :today
              AND (
                    (c.trainingType = :storeType AND store.id = :storeId)
                    OR (c.trainingType = com.example.coffee_hrm.common.enums.TrainingType.CENTRALIZED_TRAINING
                        AND trainerUser.id = :trainerUserId)
                  )
            ORDER BY c.endDate DESC, c.className ASC
            """)
    List<TrainingClass> findEndedApprovedForManager(@Param("storeId") Integer storeId,
                                                    @Param("trainerUserId") Integer trainerUserId,
                                                    @Param("storeType") TrainingType storeType,
                                                    @Param("status") TrainingClassStatus status,
                                                    @Param("today") LocalDate today);

    long countByStatus(TrainingClassStatus status);

    boolean existsByClassNameIgnoreCaseAndStatusNotAndEndDateGreaterThanEqual(
            String className,
            TrainingClassStatus status,
            LocalDate endDate);
}
