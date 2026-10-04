package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.entity.RecruitmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecruitmentRequestRepository extends JpaRepository<RecruitmentRequest, Integer> {

    boolean existsByEmailIgnoreCaseAndStatus(String email, RecruitmentStatus status);

    int countByStatus(RecruitmentStatus status);

    @Query("""
            SELECT r FROM RecruitmentRequest r
            JOIN FETCH r.store s
            JOIN FETCH r.requestedBy manager
            WHERE manager.id = :managerId
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<RecruitmentRequest> findMine(@Param("managerId") Integer managerId);

    @Query("""
            SELECT r FROM RecruitmentRequest r
            JOIN FETCH r.store s
            JOIN FETCH r.requestedBy manager
            LEFT JOIN FETCH r.reviewedBy
            LEFT JOIN FETCH r.createdEmployee
            WHERE r.id = :id
            """)
    Optional<RecruitmentRequest> findDetailById(@Param("id") Integer id);

    @Query("""
            SELECT r FROM RecruitmentRequest r
            JOIN FETCH r.store s
            JOIN FETCH r.requestedBy manager
            WHERE (:storeId IS NULL OR s.id = :storeId)
              AND (:managerId IS NULL OR manager.id = :managerId)
              AND (:status IS NULL OR r.status = :status)
              AND (:createdFrom IS NULL OR r.createdAt >= :createdFrom)
              AND (:createdToExclusive IS NULL OR r.createdAt < :createdToExclusive)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<RecruitmentRequest> search(@Param("storeId") Integer storeId,
                                    @Param("managerId") Integer managerId,
                                    @Param("status") RecruitmentStatus status,
                                    @Param("createdFrom") LocalDateTime createdFrom,
                                    @Param("createdToExclusive") LocalDateTime createdToExclusive);
}
