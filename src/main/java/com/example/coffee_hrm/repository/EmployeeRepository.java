package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.entity.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> lockByIdForLeaveRequest(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> lockById(@Param("id") Integer id);

    long countByStore_IdAndStatusNot(Integer storeId, EmployeeStatus status);

    long countByStatusNot(EmployeeStatus status);

    /**
     * Nhân sự chưa nghỉ việc theo đúng một vai trò. Không đếm hồ sơ chưa có tài khoản.
     */
    @Query("""
            SELECT COUNT(e)
            FROM Employee e
            JOIN e.user u
            JOIN u.role r
            WHERE e.status <> :terminated
              AND r.roleName = :role
            """)
    long countWorkingByRole(@Param("terminated") EmployeeStatus terminated,
                            @Param("role") RoleName role);

    /**
     * Hồ sơ chưa nghỉ việc và chưa có tài khoản. Mỗi nhân viên được đếm một lần.
     */
    @Query("""
            SELECT COUNT(e)
            FROM Employee e
            WHERE e.status <> :terminated
              AND NOT EXISTS (
                  SELECT u FROM User u WHERE u.employee = e
              )
            """)
    long countWorkingWithoutAccount(@Param("terminated") EmployeeStatus terminated);

    /**
     * Tuyển mới theo ngày vào làm. Không gồm vai trò Admin.
     */
    @Query("""
            SELECT year(e.hireDate), month(e.hireDate), COUNT(e)
            FROM Employee e
            WHERE e.hireDate >= :from
              AND e.hireDate < :toExclusive
              AND NOT EXISTS (
                  SELECT u FROM User u
                  JOIN u.role r
                  WHERE u.employee = e
                    AND r.roleName = :adminRole
              )
            GROUP BY year(e.hireDate), month(e.hireDate)
            """)
    List<Object[]> countHiresByMonth(@Param("from") LocalDate from,
                                     @Param("toExclusive") LocalDate toExclusive,
                                     @Param("adminRole") RoleName adminRole);

    /**
     * Nghỉ việc đã áp dụng: trạng thái Terminated và có ngày nghỉ việc trong kỳ. Không gồm Admin.
     */
    @Query("""
            SELECT year(e.terminationDate), month(e.terminationDate), COUNT(e)
            FROM Employee e
            WHERE e.status = :terminated
              AND e.terminationDate >= :from
              AND e.terminationDate < :toExclusive
              AND NOT EXISTS (
                  SELECT u FROM User u
                  JOIN u.role r
                  WHERE u.employee = e
                    AND r.roleName = :adminRole
              )
            GROUP BY year(e.terminationDate), month(e.terminationDate)
            """)
    List<Object[]> countTerminationsByMonth(@Param("from") LocalDate from,
                                            @Param("toExclusive") LocalDate toExclusive,
                                            @Param("terminated") EmployeeStatus terminated,
                                            @Param("adminRole") RoleName adminRole);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndStatusIn(String email, Collection<EmployeeStatus> statuses);

    @Query(
            value =
                    """
                    SELECT COUNT(*)
                    FROM dbo.Employees
                    WHERE Phone IS NOT NULL
                      AND REPLACE(REPLACE(REPLACE(Phone, ' ', ''), '-', ''), '.', '') = :phone
                    """,
            nativeQuery = true)
    int countByNormalizedPhone(@Param("phone") String phone);

    @Query(
            value =
                    """
                    SELECT COUNT(*)
                    FROM dbo.Employees
                    WHERE Status = :status
                      AND Phone IS NOT NULL
                      AND REPLACE(REPLACE(REPLACE(Phone, ' ', ''), '-', ''), '.', '') = :phone
                    """,
            nativeQuery = true)
    int countByNormalizedPhoneAndStatus(@Param("phone") String phone, @Param("status") String status);

    List<Employee> findByStore_IdAndStatus(Integer storeId, EmployeeStatus status);

    @Query(
            """
            SELECT e
            FROM Employee e
            JOIN FETCH e.store s
            WHERE e.status = :status
            ORDER BY s.storeName ASC, e.fullName ASC
            """)
    List<Employee> findByStatusWithStore(@Param("status") EmployeeStatus status);

    @Query(
            """
            SELECT e
            FROM Employee e
            JOIN FETCH e.store s
            WHERE s.id IN :storeIds AND e.status = :status
            ORDER BY s.storeName ASC, e.fullName ASC
            """)
    List<Employee> findByStoreIdsAndStatus(
            @Param("storeIds") Collection<Integer> storeIds,
            @Param("status") EmployeeStatus status);

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.store WHERE e.id = :id")
    Optional<Employee> findByIdWithStore(@Param("id") Integer id);

    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.user u
            LEFT JOIN FETCH u.role
            LEFT JOIN FETCH e.store
            WHERE LOWER(e.email) = LOWER(:email)
            """)
    Optional<Employee> findByEmailIgnoreCaseWithAccount(@Param("email") String email);

    @Query(
            """
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.user u
            LEFT JOIN FETCH u.role
            WHERE e.store.id = :storeId
            ORDER BY e.id
            """)
    List<Employee> findByStoreIdWithAccount(@Param("storeId") Integer storeId);

    /**
     * Nhân viên Staff đang làm việc tại đúng một cửa hàng.
     * Không tính Manager, Admin, nhân viên nghỉ phép, đã nghỉ việc, hoặc cửa hàng khác.
     */
    @Query("""
            SELECT COUNT(e)
            FROM Employee e
            JOIN e.user u
            JOIN u.role r
            WHERE e.store.id = :storeId
              AND e.status = :status
              AND r.roleName = :staffRole
              AND e.id <> :managerEmployeeId
            """)
    long countSchedulableStaff(@Param("storeId") Integer storeId,
                                @Param("status") EmployeeStatus status,
                                @Param("staffRole") RoleName staffRole,
                                @Param("managerEmployeeId") Integer managerEmployeeId);

    @Query("""
            SELECT e.id
            FROM Employee e
            JOIN e.user u
            JOIN u.role r
            WHERE e.store.id = :storeId
              AND e.status = :status
              AND r.roleName = :staffRole
              AND e.certificationStatus = :certified
              AND e.id <> :managerEmployeeId
            """)
    List<Integer> findSchedulableStaffIds(@Param("storeId") Integer storeId,
                                           @Param("status") EmployeeStatus status,
                                           @Param("staffRole") RoleName staffRole,
                                           @Param("certified") CertificationStatus certified,
                                           @Param("managerEmployeeId") Integer managerEmployeeId);

    /**
     * Nhân viên đang làm việc tại đúng cửa hàng: trạng thái Active.
     * Không tính tài khoản Manager và không tính nhân viên nghỉ phép hoặc đã nghỉ việc.
     */
    @Query("""
            SELECT COUNT(e)
            FROM Employee e
            WHERE e.store.id = :storeId
              AND e.status = :active
              AND e.id <> :managerEmployeeId
              AND NOT EXISTS (
                  SELECT u FROM User u
                  JOIN u.role r
                  WHERE u.employee = e
                    AND r.roleName = :managerRole
              )
            """)
    long countActiveStaffAtStore(@Param("storeId") Integer storeId,
                                  @Param("active") EmployeeStatus active,
                                  @Param("managerEmployeeId") Integer managerEmployeeId,
                                  @Param("managerRole") RoleName managerRole);

    @Query(
            """
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.store s
            LEFT JOIN FETCH s.manager
            WHERE e.id = :id
            """)
    Optional<Employee> findByIdWithStoreAndManager(@Param("id") Integer id);

    @Query(
            """
            SELECT e
            FROM Employee e
            JOIN FETCH e.store s
            LEFT JOIN FETCH e.user u
            LEFT JOIN FETCH u.role
            WHERE e.id = :id
            """)
    Optional<Employee> findByIdWithStoreAccount(@Param("id") Integer id);

    @Query(
            """
            SELECT e
            FROM Employee e
            JOIN FETCH e.store s
            LEFT JOIN FETCH s.manager
            LEFT JOIN FETCH e.user u
            LEFT JOIN FETCH u.role
            ORDER BY CASE WHEN e.status = :terminatedStatus THEN 1 ELSE 0 END, e.fullName ASC
            """)
    List<Employee> findAllForAdmin(@Param("terminatedStatus") EmployeeStatus terminatedStatus);

    @Query(
            """
            SELECT e.store.id AS storeId, COUNT(e) AS headcount
            FROM Employee e
            WHERE e.status <> :excludedStatus
            GROUP BY e.store.id
            """)
    List<StoreHeadcount> countHeadcountByStore(
            @Param("excludedStatus") EmployeeStatus excludedStatus);

    interface StoreHeadcount {
        Integer getStoreId();
        Long getHeadcount();
    }
}
