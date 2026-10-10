package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.ShiftSlotLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShiftSlotLimitRepository extends JpaRepository<ShiftSlotLimit, Integer> {

    /**
     * Khóa hàng giới hạn của đúng ca và đúng thứ.
     * Giao dịch đăng ký giữ khóa đến khi commit để hai Staff không cùng lấy chỗ cuối.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT l FROM ShiftSlotLimit l
            WHERE l.shift.id = :shiftId AND l.dayOfWeek = :dayOfWeek
            """)
    Optional<ShiftSlotLimit> lockByShiftAndDay(@Param("shiftId") Integer shiftId,
                                                @Param("dayOfWeek") Integer dayOfWeek);

    Optional<ShiftSlotLimit> findByShift_IdAndDayOfWeek(Integer shiftId, Integer dayOfWeek);

    @Query("""
            SELECT l FROM ShiftSlotLimit l
            JOIN FETCH l.shift s
            WHERE s.store.id = :storeId
            """)
    List<ShiftSlotLimit> findByStoreId(@Param("storeId") Integer storeId);
}
