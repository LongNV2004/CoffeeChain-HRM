package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.geo.GeoDistance;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateStoreRequest;
import com.example.coffee_hrm.dto.request.UpdateStoreRequest;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmployeeRepository.StoreHeadcount;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreServiceImpl implements StoreService {

    public static final String ACCESS_DENIED = "Access denied";
    public static final String STORE_NOT_FOUND = "Store not found";
    public static final String STORE_NAME_EXISTS = "Tên cửa hàng đã tồn tại";
    public static final String STORE_NAME_REQUIRED = "Vui lòng nhập tên cửa hàng";
    public static final String ADDRESS_REQUIRED = "Vui lòng nhập địa chỉ";
    public static final String INVALID_COORDINATES = "Tọa độ cửa hàng không hợp lệ.";
    public static final String LOCATION_REQUIRED = "Vui lòng thiết lập vị trí chấm công của cửa hàng.";
    public static final String OPENING_DATE_REQUIRED = "Vui lòng chọn ngày khai trương";
    public static final String STATUS_ACTION_INVALID = "Thao tác trạng thái không hợp lệ.";
    public static final String ALREADY_INACTIVE = "Cửa hàng đã ngừng hoạt động.";
    public static final String NOT_INACTIVE = "Chỉ cửa hàng đang ngừng hoạt động mới được kích hoạt lại.";
    public static final String REACTIVATE_NEEDS_OPENING_DATE = "Vui lòng cập nhật ngày khai trương trước khi kích hoạt lại.";

    /** Nhân viên đã nghỉ việc không còn được tính là thành viên của cửa hàng. */
    private static final EmployeeStatus FORMER_EMPLOYEE_STATUS = EmployeeStatus.TERMINATED;

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreOpeningSetup storeOpeningSetup;
    private final StoreStatusTransition storeStatusTransition;

    @Override
    public List<StoreResponse> getStores(AuthenticatedUser actor) {
        requireAdmin(actor);
        storeStatusTransition.promoteDueStores(VietnamTime.today());
        Map<Integer, Long> headcounts = employeeRepository.countHeadcountByStore(FORMER_EMPLOYEE_STATUS).stream()
                .collect(Collectors.toMap(StoreHeadcount::getStoreId, StoreHeadcount::getHeadcount));
        return storeRepository.findAllWithManager().stream()
                .map(store -> toStoreResponse(store, headcounts.getOrDefault(store.getId(), 0L)))
                .toList();
    }

    @Override
    public StoreResponse getStore(AuthenticatedUser actor, Integer storeId) {
        requireAdmin(actor);
        storeStatusTransition.promoteDueStores(VietnamTime.today());
        Store store = requireStoreWithManager(storeId);
        return toStoreResponse(store, countMembers(storeId));
    }

    @Override
    @Transactional
    public StoreResponse createStore(AuthenticatedUser actor, CreateStoreRequest request) {
        requireAdmin(actor);
        String storeName = requireText(request.getStoreName(), STORE_NAME_REQUIRED);
        String address = requireText(request.getAddress(), ADDRESS_REQUIRED);
        if (storeRepository.existsByStoreNameIgnoreCase(storeName)) {
            throw new BusinessException(STORE_NAME_EXISTS);
        }
        LocalDate openingDate = requireOpeningDate(request.getOpeningDate());
        StoreStatus status = StoreStatus.fromOpeningDate(openingDate, VietnamTime.today());
        // Cửa hàng mới chưa có nhân viên và chưa chỉ định Manager; phân công thực hiện ở Nhân sự & đào tạo.
        // Ca, số chỗ và vị trí chấm công được gắn ngay để nhân viên check-in được.
        Store store = Store.builder()
                .storeName(storeName)
                .address(address)
                .openingDate(openingDate)
                .status(status)
                .isActive(status != StoreStatus.INACTIVE)
                .build();
        if (request.getTotalLeaveSlots() != null) {
            store.setTotalLeaveSlots(request.getTotalLeaveSlots());
        }
        applyLocation(store, request.getLatitude(), request.getLongitude(), true);
        Store saved = storeRepository.save(store);
        storeOpeningSetup.ensureOpeningShifts(saved);
        return toStoreResponse(saved, 0L);
    }

    @Override
    @Transactional
    public StoreResponse updateStore(AuthenticatedUser actor, Integer storeId, UpdateStoreRequest request) {
        requireAdmin(actor);
        Store store = requireStoreWithManager(storeId);
        String storeName = request.getStoreName().trim();
        if (storeRepository.existsByStoreNameIgnoreCaseAndIdNot(storeName, storeId)) {
            throw new BusinessException(STORE_NAME_EXISTS);
        }
        store.setStoreName(storeName);
        store.setAddress(request.getAddress().trim());
        store.setTotalLeaveSlots(request.getTotalLeaveSlots());
        store.setOpeningDate(requireOpeningDate(request.getOpeningDate()));
        if (effectiveStatus(store) != StoreStatus.INACTIVE) {
            store.assignStatus(StoreStatus.fromOpeningDate(store.getOpeningDate(), VietnamTime.today()));
        } else {
            store.assignStatus(StoreStatus.INACTIVE);
        }
        applyLocation(store, request.getLatitude(), request.getLongitude(), false);
        return toStoreResponse(store, countMembers(storeId));
    }

    @Override
    @Transactional
    public StoreResponse changeStatus(AuthenticatedUser actor, Integer storeId, String action) {
        requireAdmin(actor);
        storeStatusTransition.promoteDueStores(VietnamTime.today());
        Store store = requireStoreWithManager(storeId);
        StoreStatus current = effectiveStatus(store);
        if ("deactivate".equals(action)) {
            if (current == StoreStatus.INACTIVE) {
                throw new BusinessException(ALREADY_INACTIVE);
            }
            store.assignStatus(StoreStatus.INACTIVE);
        } else if ("reactivate".equals(action)) {
            if (current != StoreStatus.INACTIVE) {
                throw new BusinessException(NOT_INACTIVE);
            }
            if (store.getOpeningDate() == null) {
                throw new BusinessException(REACTIVATE_NEEDS_OPENING_DATE);
            }
            store.assignStatus(StoreStatus.fromOpeningDate(store.getOpeningDate(), VietnamTime.today()));
        } else {
            throw new BusinessException(STATUS_ACTION_INVALID);
        }
        return toStoreResponse(store, countMembers(storeId));
    }

    private void applyLocation(Store store, String latitudeText, String longitudeText, boolean required) {
        BigDecimal latitude = parseCoordinate(latitudeText);
        BigDecimal longitude = parseCoordinate(longitudeText);
        if (latitude == null && longitude == null) {
            if (required) {
                throw new BusinessException(LOCATION_REQUIRED);
            }
            return;
        }
        if (!GeoDistance.isValidLatitude(latitude) || !GeoDistance.isValidLongitude(longitude)) {
            throw new BusinessException(INVALID_COORDINATES);
        }
        boolean changed = store.getLatitude() == null
                || store.getLongitude() == null
                || store.getLatitude().compareTo(latitude) != 0
                || store.getLongitude().compareTo(longitude) != 0;
        store.setLatitude(latitude);
        store.setLongitude(longitude);
        if (changed) {
            store.setLocationUpdatedAt(VietnamTime.now());
        }
    }

    private BigDecimal parseCoordinate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(INVALID_COORDINATES);
        }
    }

    private long countMembers(Integer storeId) {
        return employeeRepository.countByStore_IdAndStatusNot(storeId, FORMER_EMPLOYEE_STATUS);
    }

    private Store requireStoreWithManager(Integer storeId) {
        return storeRepository.findByIdWithManager(storeId)
                .orElseThrow(() -> new BusinessException(STORE_NOT_FOUND));
    }

    private LocalDate requireOpeningDate(LocalDate openingDate) {
        if (openingDate == null) {
            throw new BusinessException(OPENING_DATE_REQUIRED);
        }
        return openingDate;
    }

    private StoreStatus effectiveStatus(Store store) {
        if (store.getStatus() != null) {
            return store.getStatus();
        }
        return Boolean.FALSE.equals(store.getIsActive()) ? StoreStatus.INACTIVE : StoreStatus.ACTIVE;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException(ACCESS_DENIED);
        }
    }

    private StoreResponse toStoreResponse(Store store, long employeeCount) {
        Employee manager = store.getManager();
        User managerAccount = manager != null ? manager.getUser() : null;
        StoreStatus status = effectiveStatus(store);
        return StoreResponse.builder()
                .id(store.getId())
                .storeName(store.getStoreName())
                .address(store.getAddress())
                .totalLeaveDays(store.getTotalLeaveDays())
                .totalLeaveSlots(store.getTotalLeaveSlots())
                .isActive(status != StoreStatus.INACTIVE)
                .status(status)
                .statusLabel(status.getLabel())
                .stateKey(stateKey(status))
                .openingDate(store.getOpeningDate())
                .employeeCount(employeeCount)
                .managerName(manager != null ? manager.getFullName() : null)
                .managerEmail(manager != null ? manager.getEmail() : null)
                .managerPhone(manager != null ? manager.getPhone() : null)
                .managerCitizenId(manager != null ? manager.getCitizenId() : null)
                .managerStatusLabel(manager != null ? employeeStatusLabel(manager.getStatus()) : null)
                .managerAssignmentLabel(manager != null ? "Đã phân công" : "Chưa phân công")
                .managerCredentialsPending(managerAccount != null
                        && Boolean.TRUE.equals(managerAccount.getMustChangePassword()))
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .locationUpdatedAt(store.getLocationUpdatedAt())
                .build();
    }

    private String employeeStatusLabel(EmployeeStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case ACTIVE -> "Đang làm việc";
            case ON_LEAVE -> "Đang nghỉ phép";
            case TERMINATED -> "Đã nghỉ việc";
        };
    }

    private String stateKey(StoreStatus status) {
        return switch (status) {
            case PRE_OPENING -> "pre-opening";
            case INACTIVE -> "inactive";
            case ACTIVE -> "active";
        };
    }
}
