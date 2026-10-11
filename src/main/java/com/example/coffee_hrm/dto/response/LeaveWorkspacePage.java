package com.example.coffee_hrm.dto.response;
import java.util.List;
public record LeaveWorkspacePage(List<LeaveWorkspaceRow> items, long totalElements,
                                 int totalPages, int currentPage) { }
