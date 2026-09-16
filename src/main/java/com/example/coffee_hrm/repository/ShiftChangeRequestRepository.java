package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShiftChangeRequestRepository extends JpaRepository<ShiftChangeRequest, Integer> {

    long countByEmployee_Store_IdAndStatus(Integer storeId, ApprovalStatus status);

    List<ShiftChangeRequest> findByEmployee_IdOrderByRequestDateDesc(Integer employeeId, Pageable pageable);
}
