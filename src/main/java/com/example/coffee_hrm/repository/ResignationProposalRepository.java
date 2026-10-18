package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import com.example.coffee_hrm.entity.ResignationProposal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResignationProposalRepository extends JpaRepository<ResignationProposal, Integer> {

    int countByStatusIn(Collection<ResignationProposalStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ResignationProposal p WHERE p.id = :id")
    Optional<ResignationProposal> lockById(@Param("id") Integer id);

    @Query("""
            SELECT p FROM ResignationProposal p
            JOIN FETCH p.store s
            JOIN FETCH p.requestedBy manager
            WHERE manager.id = :managerId
              AND s.id = :storeId
            ORDER BY p.createdAt DESC
            """)
    List<ResignationProposal> findMine(@Param("managerId") Integer managerId, @Param("storeId") Integer storeId);

    @Query("""
            SELECT p FROM ResignationProposal p
            JOIN FETCH p.store s
            JOIN FETCH p.requestedBy manager
            WHERE p.id = :id
            """)
    Optional<ResignationProposal> findDetailById(@Param("id") Integer id);

    @Query("""
            SELECT p FROM ResignationProposal p
            JOIN FETCH p.store s
            JOIN FETCH p.requestedBy manager
            WHERE (:storeId IS NULL OR s.id = :storeId)
              AND (:status IS NULL OR p.status = :status)
              AND (
                    :keyword IS NULL
                    OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.storeName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(manager.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR (:proposalId IS NOT NULL AND p.id = :proposalId)
              )
            ORDER BY p.createdAt DESC
            """)
    List<ResignationProposal> search(@Param("storeId") Integer storeId,
                                     @Param("status") ResignationProposalStatus status,
                                     @Param("keyword") String keyword,
                                     @Param("proposalId") Integer proposalId);
}
