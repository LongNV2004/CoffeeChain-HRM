package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.MonthlyEmployeeAward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MonthlyEmployeeAwardRepository extends JpaRepository<MonthlyEmployeeAward, Integer> {

    @Query("""
            SELECT a
            FROM MonthlyEmployeeAward a
            JOIN FETCH a.employee e
            WHERE a.store.id = :storeId
              AND a.awardMonth IN :months
            ORDER BY a.awardMonth DESC, a.awardRank ASC
            """)
    List<MonthlyEmployeeAward> findForStoreAndMonths(@Param("storeId") Integer storeId,
                                                     @Param("months") Collection<String> months);

    boolean existsByStore_IdAndAwardMonth(Integer storeId, String awardMonth);
}

