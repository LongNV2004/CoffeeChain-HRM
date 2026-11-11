package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh sách nhân sự của cửa hàng cho Phân bổ nhân sự trong một tuần.
 * Chỉ mô tả từng nhân viên và khả năng được phân bổ; không chứa lịch hay ca thiếu người.
 */
@Value
@Builder
public class StaffAllocationRoster {

    LocalDate weekStart;
    LocalDate weekEnd;
    /** Lý do không thể phân bổ cho tuần này (tuần đã bắt đầu, chưa cấu hình ca...). */
    String blockedMessage;
    @Builder.Default
    List<Row> rows = List.of();

    public boolean isAllowed() {
        return blockedMessage == null;
    }

    public int getTotal() {
        return rows.size();
    }

    /** Chỉ nhân viên có thể phân bổ: màn hình Phân bổ nhân sự không hiển thị người không đủ điều kiện. */
    public List<Row> getEligibleRows() {
        return rows.stream().filter(Row::isEligible).toList();
    }

    public long getEligibleCount() {
        return rows.stream().filter(Row::isEligible).count();
    }

    public long getIneligibleCount() {
        return rows.size() - getEligibleCount();
    }

    /** Trạng thái hợp đồng có trong danh sách, theo thứ tự xuất hiện, dùng cho bộ lọc. */
    public Map<String, String> getContractStatusOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (Row row : rows) {
            options.putIfAbsent(row.getContractStatusKey(), row.getContractStatusLabel());
        }
        return options;
    }

    @Value
    @Builder
    public static class Row {
        Integer employeeId;
        String employeeCode;
        String fullName;
        String initials;
        /** Chức danh trên hợp đồng (Pha chế, Phục vụ, Thu ngân...). */
        String position;
        /** Loại hợp đồng: xác định / không xác định thời hạn. {@code null} khi chưa có hợp đồng. */
        String contractTermLabel;
        /** Khóa trạng thái hợp đồng ({@code ContractValidityStatus} hoặc {@code NONE}). */
        String contractStatusKey;
        String contractStatusLabel;
        /** ok / warn / bad / muted: màu hiển thị trạng thái hợp đồng. */
        String contractTone;
        /** enough / short / none / na. */
        String registrationKey;
        String registrationLabel;
        Integer requiredShifts;
        int registeredShifts;
        int missingShifts;
        boolean eligible;
        /** Lý do không đủ điều kiện; {@code null} khi đủ điều kiện. */
        String reason;
        /** warn (cam: chưa có hợp đồng, hợp đồng chưa hiệu lực) hoặc bad (đỏ/xám). */
        String reasonTone;
    }
}
