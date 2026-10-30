package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.dto.response.EmployeeHistoryEvent;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDetail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gắn trạng thái hợp đồng hiện tại và dựng lịch sử từ hồ sơ, hợp đồng, đào tạo đã lưu.
 * Không tạo bản ghi mới và không xóa hợp đồng cũ.
 */
public final class EmployeeRecordSupport {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private EmployeeRecordSupport() {
    }

    public record ContractTotals(long effectiveCount, long expiringCount) {
    }

    public static ContractTotals applyCurrentStatus(List<EmployeeResponse> employees,
                                                     List<EmploymentContractResponse> contracts) {
        if (employees == null || employees.isEmpty()) {
            return new ContractTotals(0, 0);
        }
        Set<Integer> employeeIds = new HashSet<>();
        for (EmployeeResponse employee : employees) {
            if (employee.getId() != null) {
                employeeIds.add(employee.getId());
            }
        }
        Map<Integer, EmploymentContractResponse> current = new HashMap<>();
        long effective = 0;
        long expiring = 0;
        if (contracts != null) {
            for (EmploymentContractResponse contract : contracts) {
                if (contract.getEmployeeId() == null || !employeeIds.contains(contract.getEmployeeId())) {
                    continue;
                }
                if (contract.getValidityStatus() == ContractValidityStatus.DANG_HIEU_LUC
                        || contract.getValidityStatus() == ContractValidityStatus.SAP_HET_HAN) {
                    effective++;
                }
                if (contract.getValidityStatus() == ContractValidityStatus.SAP_HET_HAN) {
                    expiring++;
                }
                if (contract.getValidityStatus() == null) {
                    continue;
                }
                current.merge(contract.getEmployeeId(), contract, EmployeeRecordSupport::prefer);
            }
        }
        for (EmployeeResponse employee : employees) {
            EmploymentContractResponse contract = current.get(employee.getId());
            if (contract == null) {
                employee.setContractStatusKey("NONE");
                employee.setContractStatusLabel("Chưa có hợp đồng");
            } else {
                employee.setContractStatusKey(contract.getValidityStatus().name());
                employee.setContractStatusLabel(contract.getValidityStatusLabel());
            }
        }
        return new ContractTotals(effective, expiring);
    }

    public static List<EmployeeHistoryEvent> history(EmployeeResponse employee,
                                                     List<ManagedEmployeeDetail.TrainingLine> trainings,
                                                     List<EmploymentContractResponse> contracts) {
        if (employee == null) {
            return List.of();
        }
        return history(
                employee.getCreatedAt(),
                employee.getHireDate(),
                employee.getTerminationDate(),
                employee.getStoreName(),
                employee.getCertificateIssuedOn(),
                employee.isHasCertificate(),
                trainings,
                contracts);
    }

    public static List<EmployeeHistoryEvent> history(LocalDateTime createdAt,
                                                     LocalDate hireDate,
                                                     LocalDate terminationDate,
                                                     String storeName,
                                                     LocalDate certificateIssuedOn,
                                                     boolean hasCertificate,
                                                     List<ManagedEmployeeDetail.TrainingLine> trainings,
                                                     List<EmploymentContractResponse> contracts) {
        List<EmployeeHistoryEvent> events = new ArrayList<>();
        if (createdAt != null) {
            events.add(new EmployeeHistoryEvent(
                    createdAt.toLocalDate(),
                    "Tạo hồ sơ nhân viên",
                    "Hồ sơ được ghi nhận trên hệ thống."));
        }
        if (hireDate != null) {
            String store = storeName == null || storeName.isBlank() ? "cửa hàng hiện tại" : storeName;
            events.add(new EmployeeHistoryEvent(hireDate, "Ngày vào làm", "Bắt đầu làm việc tại " + store + "."));
        }
        if (storeName != null && !storeName.isBlank()) {
            events.add(new EmployeeHistoryEvent(
                    hireDate != null ? hireDate : (createdAt == null ? null : createdAt.toLocalDate()),
                    "Cửa hàng hiện tại",
                    "Nhân viên đang thuộc " + storeName
                            + ". Hợp đồng và hồ sơ được giữ khi điều chuyển hoặc nghỉ việc."));
        }
        if (terminationDate != null) {
            events.add(new EmployeeHistoryEvent(
                    terminationDate,
                    "Nghỉ việc",
                    "Hồ sơ và toàn bộ hợp đồng được giữ lại."));
        }
        if (certificateIssuedOn != null) {
            events.add(new EmployeeHistoryEvent(
                    certificateIssuedOn,
                    "Cấp Chứng chỉ nhân viên",
                    "Ngày cấp được lưu trên hồ sơ."));
        } else if (hasCertificate) {
            events.add(new EmployeeHistoryEvent(
                    null,
                    "Chứng chỉ nhân viên",
                    "Nhân viên đang có chứng chỉ. Ngày cấp chưa được ghi trên hồ sơ."));
        }
        List<EmploymentContractResponse> ordered = contracts == null ? List.of() : contracts.stream()
                .sorted(Comparator
                        .comparing(EmploymentContractResponse::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EmploymentContractResponse::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EmploymentContractResponse::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            EmploymentContractResponse contract = ordered.get(index);
            boolean renewed = index > 0;
            LocalDate created = contract.getCreatedAt() == null
                    ? contract.getStartDate()
                    : contract.getCreatedAt().toLocalDate();
            String code = contract.getContractCode() == null ? "" : " " + contract.getContractCode();
            String title = renewed ? "Gia hạn hợp đồng" + code : "Tạo hợp đồng" + code;
            events.add(new EmployeeHistoryEvent(created, title, contractDetail(contract, renewed)));
            if (contract.getTerminatedDate() != null) {
                events.add(new EmployeeHistoryEvent(
                        contract.getTerminatedDate(),
                        "Chấm dứt hợp đồng" + code,
                        "Hợp đồng cũ và file đã ký vẫn được lưu."));
            } else if (contract.getValidityStatus() == ContractValidityStatus.DA_HET_HAN && contract.getEndDate() != null) {
                events.add(new EmployeeHistoryEvent(
                        contract.getEndDate(),
                        "Hợp đồng hết hạn" + code,
                        "Ngày kết thúc là ngày cuối cùng hợp đồng còn hiệu lực."));
            }
        }
        if (trainings != null) {
            for (ManagedEmployeeDetail.TrainingLine course : trainings) {
                String result = course.resultLabel() == null ? "" : " · " + course.resultLabel();
                events.add(new EmployeeHistoryEvent(
                        course.startDate(),
                        "Tham gia đào tạo",
                        (course.className() == null ? "Lớp đào tạo" : course.className()) + result));
            }
        }
        events.sort(Comparator.comparing(EmployeeHistoryEvent::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
    }

    private static EmploymentContractResponse prefer(EmploymentContractResponse left, EmploymentContractResponse right) {
        int compared = Integer.compare(rank(left.getValidityStatus()), rank(right.getValidityStatus()));
        if (compared < 0) {
            return left;
        }
        if (compared > 0) {
            return right;
        }
        LocalDate leftStart = left.getStartDate();
        LocalDate rightStart = right.getStartDate();
        if (leftStart == null) {
            return right;
        }
        if (rightStart == null || !leftStart.isBefore(rightStart)) {
            return left;
        }
        return right;
    }

    private static int rank(ContractValidityStatus status) {
        if (status == ContractValidityStatus.SAP_HET_HAN) {
            return 0;
        }
        if (status == ContractValidityStatus.DANG_HIEU_LUC) {
            return 1;
        }
        if (status == ContractValidityStatus.CHUA_HIEU_LUC) {
            return 2;
        }
        if (status == ContractValidityStatus.DA_HET_HAN) {
            return 3;
        }
        return 4;
    }

    private static String contractDetail(EmploymentContractResponse contract, boolean renewed) {
        String kept = renewed ? " Hợp đồng cũ không bị xóa." : "";
        return "Hiệu lực " + format(contract.getStartDate()) + " – " + format(contract.getEndDate())
                + ". Trạng thái: " + (contract.getValidityStatusLabel() == null ? "Chưa xác định" : contract.getValidityStatusLabel())
                + "." + kept;
    }

    private static String format(LocalDate date) {
        return date == null ? "Chưa cập nhật" : date.format(DAY);
    }
}
