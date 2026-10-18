package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ResignationItemStatus;
import com.example.coffee_hrm.entity.ResignationProposalItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResignationProposalItemRepository extends JpaRepository<ResignationProposalItem, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM ResignationProposalItem i
            WHERE i.proposal.id = :proposalId
            """)
    List<ResignationProposalItem> lockByProposalId(@Param("proposalId") Integer proposalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ResignationProposalItem i WHERE i.id = :id")
    Optional<ResignationProposalItem> lockById(@Param("id") Integer id);

    @Query("""
            SELECT i.employee.id FROM ResignationProposalItem i
            WHERE i.employee.id IN :employeeIds
              AND (
                    i.status = :pending
                    OR (i.status = :approved AND i.appliedAt IS NULL)
              )
            """)
    List<Integer> findOpenEmployeeIds(@Param("employeeIds") Collection<Integer> employeeIds,
                                      @Param("pending") ResignationItemStatus pending,
                                      @Param("approved") ResignationItemStatus approved);

    @Query("""
            SELECT i.id FROM ResignationProposalItem i
            WHERE i.status = :approved
              AND i.appliedAt IS NULL
              AND i.approvedTerminationDate IS NOT NULL
              AND i.approvedTerminationDate <= :today
            ORDER BY i.id ASC
            """)
    List<Integer> findDueIds(@Param("approved") ResignationItemStatus approved, @Param("today") LocalDate today);
}
