package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminStoreCard {

    private final Integer id;
    private final String storeName;
    private final String address;
    private final String statusLabel;
    private final String stateKey;
    private final String metric;
    private final String metricHint;
    private final String actionHref;
    private final String actionLabel;
    private final String secondaryHref;
    private final String secondaryLabel;
    private final Integer reportId;
    private final Integer reportYear;
    private final Integer reportMonth;
    private final boolean canRevise;

    public static AdminStoreCard of(StoreResponse store,
                                    String metric,
                                    String metricHint,
                                    String actionHref,
                                    String actionLabel) {
        String address = store.getAddress() == null || store.getAddress().isBlank()
                ? "Chưa cập nhật"
                : store.getAddress();
        return AdminStoreCard.builder()
                .id(store.getId())
                .storeName(store.getStoreName())
                .address(address)
                .statusLabel(store.getStatusLabel() == null ? "Chưa cập nhật" : store.getStatusLabel())
                .stateKey(store.getStateKey() == null ? "inactive" : store.getStateKey())
                .metric(metric)
                .metricHint(metricHint)
                .actionHref(actionHref)
                .actionLabel(actionLabel)
                .build();
    }
}
