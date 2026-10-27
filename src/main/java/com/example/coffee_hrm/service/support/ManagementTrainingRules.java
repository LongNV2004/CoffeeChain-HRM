package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingResult;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.User;

/**
 * Chứng chỉ quản lý chỉ được cấp khi đang là Manager và có kết quả Đạt.
 * Không đụng chứng chỉ nhân viên.
 */
public final class ManagementTrainingRules {

    public static final String MANAGER_ONLY =
            "Chỉ tài khoản Admin hoặc Manager mới được tham gia lớp chứng chỉ quản lý.";

    private ManagementTrainingRules() {
    }

    public static void requireManagerAccount(User account) {
        if (account == null || account.getRole() == null) {
            throw new BusinessException(MANAGER_ONLY);
        }
        RoleName roleName = account.getRole().getRoleName();
        if (roleName != RoleName.MANAGER && roleName != RoleName.ADMIN) {
            throw new BusinessException(MANAGER_ONLY);
        }
    }

    /**
     * @param hasAnotherPass còn một kết quả Đạt khác của cùng nhân viên
     */
    public static void applyEvaluation(Employee employee,
                                       RoleName currentRole,
                                       TrainingResult result,
                                       User recorder,
                                       boolean hasAnotherPass) {
        if (employee == null || result == null) {
            return;
        }
        if (result == TrainingResult.PASS
                && (currentRole == RoleName.MANAGER || currentRole == RoleName.ADMIN)) {
            if (!Boolean.TRUE.equals(employee.getManagementCertified())) {
                employee.setManagementCertified(true);
                employee.setManagementCertifiedAt(VietnamTime.now());
                employee.setManagementCertifiedBy(recorder);
            }
            return;
        }
        if (result != TrainingResult.PASS && !hasAnotherPass) {
            employee.setManagementCertified(false);
            employee.setManagementCertifiedAt(null);
            employee.setManagementCertifiedBy(null);
        }
    }
}
