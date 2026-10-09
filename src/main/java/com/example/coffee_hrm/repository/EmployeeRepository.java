package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    long countByStore_IdAndStatusNot(Integer storeId, EmployeeStatus status);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = """
            SELECT COUNT(*)
            FROM dbo.Employees
            WHERE Phone IS NOT NULL
              AND REPLACE(REPLACE(REPLACE(Phone, ' ', ''), '-', ''), '.', '') = :phone
            """, nativeQuery = true)
    int countByNormalizedPhone(@Param("phone") String phone);

    List<Employee> findByStore_IdAndStatus(Integer storeId, EmployeeStatus status);

    @Query("""
            SELECT e FROM Employee e
            JOIN FETCH e.store s
            WHERE e.status = :status
            ORDER BY s.storeName ASC, e.fullName ASC
            """)
    List<Employee> findByStatusWithStore(@Param("status") EmployeeStatus status);

    @Query("""
            SELECT e FROM Employee e
            JOIN FETCH e.store s
            WHERE s.id IN :storeIds
              AND e.status = :status
            ORDER BY s.storeName ASC, e.fullName ASC
            """)
    List<Employee> findByStoreIdsAndStatus(@Param("storeIds") Collection<Integer> storeIds,
                                           @Param("status") EmployeeStatus status);

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.store WHERE e.id = :id")
    Optional<Employee> findByIdWithStore(@Param("id") Integer id);

    @Query("""
            SELECT e FROM Employee e
            LEFT JOIN FETCH e.user u
            LEFT JOIN FETCH u.role
            WHERE e.store.id = :storeId
            ORDER BY e.id
            """)
    List<Employee> findByStoreIdWithAccount(@Param("storeId") Integer storeId);

    @Query("""
            SELECT e FROM Employee e
            LEFT JOIN FETCH e.store s
            LEFT JOIN FETCH s.manager
            WHERE e.id = :id
            """)
    Optional<Employee> findByIdWithStoreAndManager(@Param("id") Integer id);
    @Query("""
        SELECT e FROM Employee e
        JOIN FETCH e.store s
        LEFT JOIN FETCH s.manager
        LEFT JOIN FETCH e.user u
        LEFT JOIN FETCH u.role
        ORDER BY
            CASE WHEN e.status = :terminatedStatus THEN 1 ELSE 0 END,
            e.fullName ASC
        """)
    List<Employee> findAllForAdmin(
            @Param("terminatedStatus") EmployeeStatus terminatedStatus
    );
    @Query("""
            SELECT e.store.id AS storeId, COUNT(e) AS headcount
            FROM Employee e
            WHERE e.status <> :excludedStatus
            GROUP BY e.store.id
            """)

    List<StoreHeadcount> countHeadcountByStore(@Param("excludedStatus") EmployeeStatus excludedStatus);

    interface StoreHeadcount {
        Integer getStoreId();

        Long getHeadcount();
    }

}
