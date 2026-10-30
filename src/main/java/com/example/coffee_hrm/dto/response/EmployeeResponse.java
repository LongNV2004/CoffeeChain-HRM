package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponse {

    private Integer id;
    private String fullName;
    private String phone;
    /** Chỉ có ở màn hình chi tiết hoặc form sửa của người có quyền. Danh sách để null. */
    private String citizenId;
    /** Dạng che số, dùng cho danh sách và bảng. */
    private String citizenIdMasked;
    private String email;
    private String address;
    private String avatarUrl;
    private Integer storeId;
    private String storeName;
    private EmployeeStatus status;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private LocalDateTime createdAt;
    private Integer userId;
    /** Null khi nhân viên chưa có tài khoản đăng nhập. */
    private RoleName roleName;
    /** Null khi chưa có tài khoản. Không bao gồm mật khẩu hay dữ liệu xác thực. */
    private Boolean accountActive;
    private boolean hasCertificate;
    /** Ngày cấp Chứng chỉ nhân viên. Null khi chưa cấp hoặc ngày cấp chưa được ghi. */
    private LocalDate certificateIssuedOn;
    /** Chứng chỉ quản lý hợp lệ. Không thay thế chứng chỉ nhân viên. */
    private boolean managementCertified;
    /** Nhân viên đang được gắn làm Manager của chính cửa hàng mình (Stores.ManagerId). */
    private boolean storeManager;
    /** NONE khi chưa có hợp đồng. Các mã còn lại trùng ContractValidityStatus. */
    @Setter
    private String contractStatusKey;
    @Setter
    private String contractStatusLabel;
}
