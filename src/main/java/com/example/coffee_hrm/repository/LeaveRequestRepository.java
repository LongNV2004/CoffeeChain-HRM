package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.entity.LeaveRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {

    long countByEmployee_Store_IdAndStatus(Integer storeId, ApprovalStatus status);

    List<LeaveRequest> findByEmployee_IdOrderByCreatedAtDesc(Integer employeeId, Pageable pageable);
}
