package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.geo.GeoDistance;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateStoreRequest;
import com.example.coffee_hrm.dto.request.UpdateStoreRequest;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmployeeRepository.StoreHeadcount;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreServiceImpl implements StoreService {

    static final String ACCESS_DENIED = "Access denied";
    static final String STORE_NOT_FOUND = "Store not found";
    static final String STORE_NAME_EXISTS = "Tên cửa hàng đã tồn tại";
    static final String STORE_NAME_REQUIRED = "Vui lòng nhập tên cửa hàng";
    static final String ADDRESS_REQUIRED = "Vui lòng nhập địa chỉ";
    static final String INVALID_COORDINATES = "Tọa độ cửa hàng không hợp lệ.";

    /** Nhân viên đã nghỉ việc không còn được tính là thành viên của cửa hàng. */
    private static final EmployeeStatus FORMER_EMPLOYEE_STATUS = EmployeeStatus.TERMINATED;

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public List<StoreResponse> getStores(AuthenticatedUser actor) {
        requireAdmin(actor);
        Map<Integer, Long> headcounts = employeeRepository.countHeadcountByStore(FORMER_EMPLOYEE_STATUS).stream()
                .collect(Collectors.toMap(StoreHeadcount::getStoreId, StoreHeadcount::getHeadcount));
        return storeRepository.findAllWithManager().stream()
                .map(store -> toStoreResponse(store, headcounts.getOrDefault(store.getId(), 0L)))
                .toList();
    }

    @Override
    public StoreResponse getStore(AuthenticatedUser actor, Integer storeId) {
        requireAdmin(actor);
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
        // Cửa hàng mới chưa có nhân viên và chưa chỉ định Manager; phân công thực hiện ở Quản lý nhân sự.
        Store store = Store.builder()
                .storeName(storeName)
                .address(address)
                .build();
        if (request.getTotalLeaveDays() != null) {
            store.setTotalLeaveDays(request.getTotalLeaveDays());
        }
        if (request.getIsActive() != null) {
            store.setIsActive(request.getIsActive());
        }
        return toStoreResponse(storeRepository.save(store), 0L);
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
        store.setTotalLeaveDays(request.getTotalLeaveDays());
        store.setIsActive(request.getIsActive());
        applyLocation(store, request.getLatitude(), request.getLongitude());
        return toStoreResponse(store, countMembers(storeId));
    }

    private void applyLocation(Store store, String latitudeText, String longitudeText) {
        BigDecimal latitude = parseCoordinate(latitudeText);
        BigDecimal longitude = parseCoordinate(longitudeText);
        if (latitude == null && longitude == null) {
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
        return StoreResponse.builder()
                .id(store.getId())
                .storeName(store.getStoreName())
                .address(store.getAddress())
                .totalLeaveDays(store.getTotalLeaveDays())
                .isActive(store.getIsActive())
                .employeeCount(employeeCount)
                .managerName(manager != null ? manager.getFullName() : null)
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .locationUpdatedAt(store.getLocationUpdatedAt())
                .build();
    }
}
