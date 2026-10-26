package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.service.impl.StoreStatusTransition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Xác định cửa hàng của Manager/Staff từ hồ sơ trên database.
 * Không dùng mã cửa hàng do request gửi lên và không dùng storeId lưu trong session.
 */
@Component
@RequiredArgsConstructor
public class StoreAccessGuard {

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreStatusTransition storeStatusTransition;

    public boolean deniesBusinessAccess(AuthenticatedUser user) {
        if (user == null || user.getRoleName() == null || user.getRoleName() == RoleName.ADMIN) {
            return false;
        }
        if (user.getRoleName() != RoleName.MANAGER && user.getRoleName() != RoleName.STAFF) {
            return false;
        }
        Integer storeId = resolveStoreId(user);
        if (storeId == null) {
            return false;
        }
        StoreStatus status = storeStatusTransition.promoteAndRead(storeId, VietnamTime.today());
        return status != null && status.blocksStoreStaff();
    }

    private Integer resolveStoreId(AuthenticatedUser user) {
        if (user.getEmployeeId() == null) {
            return null;
        }
        if (user.getRoleName() == RoleName.MANAGER) {
            Integer managedStoreId = storeRepository.findByManager_Id(user.getEmployeeId())
                    .map(Store::getId)
                    .orElse(null);
            if (managedStoreId != null) {
                return managedStoreId;
            }
        }
        return employeeRepository.findByIdWithStore(user.getEmployeeId())
                .map(Employee::getStore)
                .map(Store::getId)
                .orElse(null);
    }
}
