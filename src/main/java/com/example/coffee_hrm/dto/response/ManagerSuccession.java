package com.example.coffee_hrm.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Tình trạng Manager của một cửa hàng và danh sách Staff có thể thay thế.
 * Staff đủ điều kiện khi tài khoản hoạt động và có hợp đồng đang hiệu lực
 * (cùng quy tắc chặn truy cập chức năng quản lý).
 */
public record ManagerSuccession(boolean storeHasManager,
                                String currentManagerName,
                                List<Candidate> candidates) {

    public ManagerSuccession {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public List<Candidate> getEligibleCandidates() {
        return candidates.stream().filter(Candidate::eligible).toList();
    }

    public long getIneligibleCount() {
        return candidates.stream().filter(candidate -> !candidate.eligible()).count();
    }

    /** Ứng viên ứng với một nhân viên, hoặc {@code null} nếu nhân viên không phải Staff đang làm việc. */
    public Candidate candidateOf(Integer employeeId) {
        if (employeeId == null) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> employeeId.equals(candidate.employeeId()))
                .findFirst()
                .orElse(null);
    }

    public record Candidate(Integer employeeId,
                            String fullName,
                            String email,
                            String contractCode,
                            LocalDate contractStartDate,
                            LocalDate contractEndDate,
                            String contractStatusLabel,
                            boolean contractValid,
                            boolean accountActive,
                            boolean managementCertified,
                            List<String> blockers) {

        public Candidate {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }

        public boolean eligible() {
            return blockers.isEmpty();
        }
    }
}
