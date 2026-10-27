package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.RoleName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingTrainerOption {

    private Integer userId;
    private Integer employeeId;
    private RoleName roleName;
    private String fullName;
    /** Cửa hàng Manager đang được phân công quản lý (Stores.ManagerId). */
    private Integer storeId;
    private String storeName;
}
