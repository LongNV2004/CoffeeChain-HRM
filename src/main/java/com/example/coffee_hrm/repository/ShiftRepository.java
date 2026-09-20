package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Integer> {

    List<Shift> findByStore_IdOrderByStartTimeAsc(Integer storeId);

    Optional<Shift> findByIdAndStore_Id(Integer id, Integer storeId);
}
