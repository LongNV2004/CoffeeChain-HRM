package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class EmploymentContractListView {

    private final List<EmploymentContractResponse> contracts;
    private final long matchCount;
    private final int page;
    private final int totalPages;
    private final int fromIndex;
    private final int toIndex;
    private final String keyword;
    private final Integer storeId;
    private final String validity;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final List<StoreChoice> stores;
    private final boolean admin;
    private final boolean manager;
    private final boolean filtered;

    public static EmploymentContractListView empty(boolean admin, boolean manager) {
        return EmploymentContractListView.builder()
                .contracts(List.of())
                .stores(List.of())
                .page(1)
                .admin(admin)
                .manager(manager)
                .build();
    }

    @Getter
    @Builder
    public static class StoreChoice {
        private final Integer id;
        private final String name;
    }
}
