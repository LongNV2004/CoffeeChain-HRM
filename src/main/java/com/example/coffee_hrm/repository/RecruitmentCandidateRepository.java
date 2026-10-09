package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.entity.RecruitmentCandidate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecruitmentCandidateRepository extends JpaRepository<RecruitmentCandidate, Integer> {

    boolean existsByEmailIgnoreCaseAndStatus(String email, RecruitmentStatus status);

    boolean existsByPhoneAndStatus(String phone, RecruitmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM RecruitmentCandidate c
            WHERE c.request.id = :requestId
            """)
    List<RecruitmentCandidate> lockByRequestId(@Param("requestId") Integer requestId);
}
