package com.example.coffee_hrm.dto.response;

import java.util.List;

public record ManagerLeavePage(List<ManagerLeaveItem> items, boolean hasMore) {
}
