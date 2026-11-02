package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.InvitationStatus;
import com.example.coffee_hrm.entity.ShiftCoverInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShiftCoverInvitationRepository extends JpaRepository<ShiftCoverInvitation, Integer> {

    @Query("""
            SELECT i
            FROM ShiftCoverInvitation i
            JOIN FETCH i.employee e
            WHERE i.request.id IN :requestIds
            ORDER BY i.sentAt ASC, i.id ASC
            """)
    List<ShiftCoverInvitation> findForRequests(@Param("requestIds") Collection<Integer> requestIds);

    @Query("""
            SELECT i
            FROM ShiftCoverInvitation i
            JOIN FETCH i.employee e
            JOIN FETCH i.request r
            JOIN FETCH r.employee re
            JOIN FETCH r.assignment a
            JOIN FETCH a.shift s
            WHERE e.id = :employeeId
              AND i.status = :status
            ORDER BY i.expiresAt ASC
            """)
    List<ShiftCoverInvitation> findForEmployeeByStatus(@Param("employeeId") Integer employeeId,
                                                       @Param("status") InvitationStatus status);

    @Query("""
            SELECT i
            FROM ShiftCoverInvitation i
            JOIN FETCH i.employee e
            JOIN FETCH e.store
            JOIN FETCH i.request r
            JOIN FETCH r.employee re
            JOIN FETCH r.assignment a
            JOIN FETCH a.shift s
            WHERE i.id = :id
            """)
    Optional<ShiftCoverInvitation> findByIdWithDetails(@Param("id") Integer id);

    @Query("""
            SELECT i
            FROM ShiftCoverInvitation i
            JOIN FETCH i.employee e
            JOIN FETCH e.store
            JOIN FETCH i.request r
            JOIN FETCH r.assignment a
            JOIN FETCH a.shift s
            WHERE i.status = com.example.coffee_hrm.common.enums.InvitationStatus.PENDING
              AND i.expiresAt <= :now
            """)
    List<ShiftCoverInvitation> findOverduePending(@Param("now") LocalDateTime now);

    List<ShiftCoverInvitation> findByRequest_IdAndStatus(Integer requestId, InvitationStatus status);

    boolean existsByRequest_IdAndEmployee_Id(Integer requestId, Integer employeeId);
}
