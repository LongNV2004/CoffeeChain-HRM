package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.Store;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Integer> {

    long countByIsActiveTrue();

    Optional<Store> findByManager_Id(Integer managerEmployeeId);

    List<Store> findByIsActiveTrueOrderByStoreNameAsc();

    boolean existsByStoreNameIgnoreCase(String storeName);

    boolean existsByStoreNameIgnoreCaseAndIdNot(String storeName, Integer storeId);

    @Query("SELECT s FROM Store s LEFT JOIN FETCH s.manager ORDER BY s.id")
    List<Store> findAllWithManager();

    @Query("SELECT s FROM Store s LEFT JOIN FETCH s.manager WHERE s.id = :id")
    Optional<Store> findByIdWithManager(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Store s WHERE s.id = :id")
    Optional<Store> findByIdForUpdate(@Param("id") Integer id);

    List<Store> findAllByManager_Id(Integer managerEmployeeId);
}
