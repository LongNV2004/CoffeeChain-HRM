package com.example.coffee_hrm.dto.response;

import java.util.List;

public record LeaveHistoryPage(List<LeaveRequestResponse> items, boolean hasMore,
                               long totalElements, int page, int totalPages) {
}
