package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.entity.TrainingClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrainingClassRepository extends JpaRepository<TrainingClass, Integer> {

    @Query("""
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill
            JOIN FETCH c.store
            JOIN FETCH c.createdBy
            LEFT JOIN FETCH c.approvedBy
            WHERE c.store.id = :storeId
            ORDER BY c.createdAt DESC
            """)
    List<TrainingClass> findByStoreIdWithDetails(@Param("storeId") Integer storeId);

    @Query("""
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill
            JOIN FETCH c.store
            JOIN FETCH c.createdBy
            LEFT JOIN FETCH c.approvedBy
            WHERE c.status = :status
            ORDER BY c.createdAt ASC
            """)
    List<TrainingClass> findByStatusWithDetails(@Param("status") TrainingClassStatus status);

    @Query("""
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill
            JOIN FETCH c.store
            JOIN FETCH c.createdBy
            LEFT JOIN FETCH c.approvedBy
            ORDER BY c.createdAt DESC
            """)
    List<TrainingClass> findAllWithDetails();

    @Query("""
            SELECT c FROM TrainingClass c
            JOIN FETCH c.skill
            JOIN FETCH c.store
            JOIN FETCH c.createdBy
            LEFT JOIN FETCH c.approvedBy
            WHERE c.id = :id
            """)
    Optional<TrainingClass> findByIdWithDetails(@Param("id") Integer id);

    long countByStatus(TrainingClassStatus status);
}
