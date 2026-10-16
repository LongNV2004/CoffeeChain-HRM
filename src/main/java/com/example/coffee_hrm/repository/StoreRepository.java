package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.Store;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Integer> {

    long countByIsActiveTrue();

    Optional<Store> findByManager_Id(Integer managerEmployeeId);

    List<Store> findByIsActiveTrueOrderByStoreNameAsc();

    boolean existsByStoreNameIgnoreCase(String storeName);

    boolean existsByStoreNameIgnoreCaseAndIdNot(String storeName, Integer storeId);

    @Query("""
            SELECT s FROM Store s
            LEFT JOIN FETCH s.manager m
            LEFT JOIN FETCH m.user u
            LEFT JOIN FETCH u.role
            ORDER BY s.id
            """)
    List<Store> findAllWithManager();

    @Query("""
            SELECT s FROM Store s
            LEFT JOIN FETCH s.manager m
            LEFT JOIN FETCH m.user u
            LEFT JOIN FETCH u.role
            WHERE s.id = :id
            """)
    Optional<Store> findByIdWithManager(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Store s WHERE s.id = :id")
    Optional<Store> findByIdForUpdate(@Param("id") Integer id);

    List<Store> findAllByManager_Id(Integer managerEmployeeId);

    /**
     * Chỉ cửa hàng PRE_OPENING đã đến ngày khai trương. INACTIVE không nằm trong điều kiện.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE Stores
            SET Status = 'ACTIVE', IsActive = 1
            WHERE Status = 'PRE_OPENING'
              AND OpeningDate IS NOT NULL
              AND OpeningDate <= :today
            """, nativeQuery = true)
    int promoteDuePreOpening(@Param("today") LocalDate today);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE Stores
            SET Status = 'ACTIVE', IsActive = 1
            WHERE StoreId = :id
              AND Status = 'PRE_OPENING'
              AND OpeningDate IS NOT NULL
              AND OpeningDate <= :today
            """, nativeQuery = true)
    int promoteDuePreOpeningById(@Param("id") Integer id, @Param("today") LocalDate today);

    @Query(value = "SELECT Status FROM Stores WHERE StoreId = :id", nativeQuery = true)
    String findStatusCodeById(@Param("id") Integer id);
}
