package com.example.coffee_hrm.dto.response;
import java.util.List;
public record LeaveReplacementSuggestion(LeaveReplacementGap gap,
                                          List<LeaveReplacementCandidate> candidates) { }
