package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDetail;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDirectory;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.dto.request.CreateEmployeeRequest;
import com.example.coffee_hrm.dto.request.UpdateEmployeeRequest;
import java.util.List;

public interface EmployeeService {
    EmployeeResponse createEmployee(
            AuthenticatedUser actor,
            CreateEmployeeRequest request
    );
    List<EmployeeResponse> getAllEmployees(AuthenticatedUser actor);
    List<EmployeeResponse> getStoreEmployees(AuthenticatedUser actor, Integer storeId);
    EmployeeResponse getStoreEmployee(AuthenticatedUser actor, Integer storeId, Integer employeeId);
    EmployeeResponse updateEmployee(
            AuthenticatedUser actor,
            Integer storeId,
            Integer employeeId,
            UpdateEmployeeRequest request
    );

    /**
     * Chỉ Admin sửa CCCD. Các trường hồ sơ khác không bị đổi.
     */
    /**
     * Admin cập nhật ngày sinh trong hồ sơ nhân viên; {@code null} xóa ngày sinh đã lưu.
     */

    EmployeeResponse changeEmployeeRole(AuthenticatedUser actor, Integer storeId, Integer employeeId, String role);

    /**
     * Admin chấm dứt ngay hợp đồng hiện tại của quản lý cửa hàng, không qua đề xuất.
     */
    EmployeeResponse terminateManagerContract(AuthenticatedUser actor, Integer storeId, Integer employeeId, String reason);

    /**
     * Tình trạng Manager của cửa hàng và các Staff có thể thay thế (Admin).
     */
    com.example.coffee_hrm.dto.response.ManagerSuccession getManagerSuccession(AuthenticatedUser actor, Integer storeId);

    /**
     * Nhân sự của đúng cửa hàng Manager đang quản lý.
     * Cửa hàng lấy từ hồ sơ Manager, không nhận mã cửa hàng từ request.
     */
    ManagedEmployeeDirectory getManagedDirectory(AuthenticatedUser actor);

    /**
     * Chi tiết một nhân viên khi nhân viên đó thuộc cửa hàng Manager đang quản lý.
     */
    ManagedEmployeeDetail getManagedEmployee(AuthenticatedUser actor, Integer employeeId);
}
