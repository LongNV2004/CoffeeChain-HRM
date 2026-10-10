package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateStoreRequest;
import com.example.coffee_hrm.dto.request.UpdateStoreRequest;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private StoreServiceImpl storeService;

    private AuthenticatedUser admin;
    private Store store;

    @BeforeEach
    void setUp() {
        admin = actor(RoleName.ADMIN);
        store = Store.builder().id(1).storeName("Store A").address("A").totalLeaveDays(12).isActive(true).build();
        when(storeRepository.findByIdWithManager(1)).thenReturn(Optional.of(store));
        when(employeeRepository.countByStore_IdAndStatusNot(1, EmployeeStatus.TERMINATED)).thenReturn(5L);
    }

    @Test
    void updatesStoreInformation() {
        StoreResponse result = storeService.updateStore(admin, 1, request("  Store B  ", " 12 Lê Lợi ", 15, false));

        assertEquals("Store B", store.getStoreName());
        assertEquals("12 Lê Lợi", store.getAddress());
        assertEquals(15, store.getTotalLeaveDays());
        assertFalse(store.getIsActive());
        assertEquals("Store B", result.getStoreName());
        assertEquals(5L, result.getEmployeeCount());
    }

    @Test
    void rejectsDuplicateStoreName() {
        when(storeRepository.existsByStoreNameIgnoreCaseAndIdNot("Store B", 1)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.updateStore(admin, 1, request("Store B", "A", 12, true)));

        assertEquals(StoreServiceImpl.STORE_NAME_EXISTS, ex.getMessage());
        assertEquals("Store A", store.getStoreName());
    }

    @Test
    void rejectsUnknownStore() {
        when(storeRepository.findByIdWithManager(99)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.updateStore(admin, 99, request("Store B", "A", 12, true)));

        assertEquals(StoreServiceImpl.STORE_NOT_FOUND, ex.getMessage());
    }

    @Test
    void rejectsNonAdmin() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.updateStore(actor(RoleName.MANAGER), 1, request("Store B", "A", 12, true)));

        assertEquals(StoreServiceImpl.ACCESS_DENIED, ex.getMessage());
        verify(storeRepository, never()).findByIdWithManager(any());
    }

    @Test
    void createsStoreWithTrimmedValuesAndNoManager() {
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store toInsert = invocation.getArgument(0);
            assertNull(toInsert.getId());
            toInsert.setId(7);
            return toInsert;
        });

        StoreResponse result = storeService.createStore(admin,
                createRequest("   Coffee Nguyễn Trãi   ", "  12 Nguyễn Trãi, Hà Nội  ", 14, true));

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository).save(captor.capture());
        Store saved = captor.getValue();
        assertEquals("Coffee Nguyễn Trãi", saved.getStoreName());
        assertEquals("12 Nguyễn Trãi, Hà Nội", saved.getAddress());
        assertEquals(14, saved.getTotalLeaveDays());
        assertTrue(saved.getIsActive());
        assertNull(saved.getManager());
        assertEquals(7, result.getId());
        assertEquals(0L, result.getEmployeeCount());
        assertNull(result.getManagerName());
    }

    @Test
    void createStoreDefaultsToActiveAndEntityLeaveDays() {
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreResponse result = storeService.createStore(admin, createRequest("Store C", "C", null, null));

        assertTrue(result.getIsActive());
        assertEquals(12, result.getTotalLeaveDays());
    }

    @Test
    void createStoreRejectsDuplicateNameBeforeInsert() {
        when(storeRepository.existsByStoreNameIgnoreCase("Store A")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.createStore(admin, createRequest("  Store A ", "A", 12, true)));

        assertEquals(StoreServiceImpl.STORE_NAME_EXISTS, ex.getMessage());
        verify(storeRepository, never()).save(any());
    }

    @Test
    void createStoreRejectsBlankNameAndAddress() {
        BusinessException blankName = assertThrows(BusinessException.class,
                () -> storeService.createStore(admin, createRequest("   ", "A", 12, true)));
        BusinessException blankAddress = assertThrows(BusinessException.class,
                () -> storeService.createStore(admin, createRequest("Store C", "", 12, true)));
        BusinessException nullName = assertThrows(BusinessException.class,
                () -> storeService.createStore(admin, createRequest(null, "A", 12, true)));

        assertEquals(StoreServiceImpl.STORE_NAME_REQUIRED, blankName.getMessage());
        assertEquals(StoreServiceImpl.ADDRESS_REQUIRED, blankAddress.getMessage());
        assertEquals(StoreServiceImpl.STORE_NAME_REQUIRED, nullName.getMessage());
        verify(storeRepository, never()).save(any());
    }

    @Test
    void createStoreRejectsManagerAndStaff() {
        for (RoleName role : new RoleName[]{RoleName.MANAGER, RoleName.STAFF}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storeService.createStore(actor(role), createRequest("Store C", "C", 12, true)));
            assertEquals(StoreServiceImpl.ACCESS_DENIED, ex.getMessage());
        }
        verify(storeRepository, never()).save(any());
    }

    @Test
    void adminSavesValidStoreCoordinates() {
        StoreResponse result = storeService.updateStore(admin, 1,
                request("Store A", "A", 12, true, "21.028511", "105.854167"));

        assertEquals(0, new BigDecimal("21.028511").compareTo(store.getLatitude()));
        assertEquals(0, new BigDecimal("105.854167").compareTo(store.getLongitude()));
        assertNotNull(store.getLocationUpdatedAt());
        assertEquals(0, store.getLatitude().compareTo(result.getLatitude()));
    }

    @Test
    void invalidStoreCoordinatesAreRejected() {
        store.setLatitude(new BigDecimal("10.5"));
        store.setLongitude(new BigDecimal("106.5"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.updateStore(admin, 1, request("Store A", "A", 12, true, "91", "106.5")));

        assertEquals(StoreServiceImpl.INVALID_COORDINATES, ex.getMessage());
        assertEquals(0, new BigDecimal("10.5").compareTo(store.getLatitude()));
    }

    @Test
    void blankCoordinatesKeepTheStoredLocation() {
        store.setLatitude(new BigDecimal("10.5"));
        store.setLongitude(new BigDecimal("106.5"));

        storeService.updateStore(admin, 1, request("Store B", "A", 12, true, "  ", null));

        assertEquals(0, new BigDecimal("10.5").compareTo(store.getLatitude()));
        assertNull(store.getLocationUpdatedAt());
    }

    @Test
    void sameCoordinatesDoNotRefreshTheUpdateTime() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 1, 8, 0);
        store.setLatitude(new BigDecimal("21.028511"));
        store.setLongitude(new BigDecimal("105.854167"));
        store.setLocationUpdatedAt(updatedAt);

        storeService.updateStore(admin, 1, request("Store A", "A", 12, true, "21.028511", "105.854167"));

        assertEquals(updatedAt, store.getLocationUpdatedAt());
    }

    @Test
    void managerAndStaffCannotChangeStoreCoordinates() {
        store.setLatitude(new BigDecimal("10.5"));
        store.setLongitude(new BigDecimal("106.5"));
        for (RoleName role : new RoleName[]{RoleName.MANAGER, RoleName.STAFF}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storeService.updateStore(actor(role), 1,
                            request("Store A", "A", 12, true, "21.028511", "105.854167")));
            assertEquals(StoreServiceImpl.ACCESS_DENIED, ex.getMessage());
        }
        assertEquals(0, new BigDecimal("10.5").compareTo(store.getLatitude()));
        verify(storeRepository, never()).findByIdWithManager(any());
    }

    private CreateStoreRequest createRequest(String storeName, String address, Integer totalLeaveDays, Boolean isActive) {
        return CreateStoreRequest.builder()
                .storeName(storeName)
                .address(address)
                .totalLeaveDays(totalLeaveDays)
                .isActive(isActive)
                .build();
    }

    private UpdateStoreRequest request(String storeName, String address, int totalLeaveDays, boolean isActive) {
        return request(storeName, address, totalLeaveDays, isActive, null, null);
    }

    private UpdateStoreRequest request(String storeName, String address, int totalLeaveDays, boolean isActive,
                                      String latitude, String longitude) {
        return UpdateStoreRequest.builder()
                .storeName(storeName)
                .address(address)
                .totalLeaveDays(totalLeaveDays)
                .isActive(isActive)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder().id(1).username("user").passwordHash("x").role(role).build());
    }
}
