package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.ShiftSlotPositionNeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShiftSlotPositionNeedRepository extends JpaRepository<ShiftSlotPositionNeed, Integer> {

    @Query("""
            SELECT n FROM ShiftSlotPositionNeed n
            JOIN FETCH n.skill
            JOIN FETCH n.slotLimit l
            JOIN FETCH l.shift s
            WHERE s.store.id = :storeId
            """)
    List<ShiftSlotPositionNeed> findByStoreId(@Param("storeId") Integer storeId);

    @Query("""
            SELECT n FROM ShiftSlotPositionNeed n
            JOIN FETCH n.skill
            JOIN FETCH n.slotLimit l
            WHERE l.shift.id = :shiftId AND l.dayOfWeek = :dayOfWeek
            """)
    List<ShiftSlotPositionNeed> findByShiftAndDay(@Param("shiftId") Integer shiftId,
                                                   @Param("dayOfWeek") Integer dayOfWeek);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ShiftSlotPositionNeed n WHERE n.slotLimit.id = :limitId")
    void deleteByLimitId(@Param("limitId") Integer limitId);
}
