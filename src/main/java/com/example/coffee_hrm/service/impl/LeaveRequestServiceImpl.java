package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateLeaveRequest;
import com.example.coffee_hrm.dto.response.LeaveHistoryPage;
import com.example.coffee_hrm.dto.response.LeavePreviewResponse;
import com.example.coffee_hrm.dto.response.LeaveRequestResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.LeaveRequest;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.LeaveBalanceRepository;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveRequestServiceImpl implements LeaveRequestService {
    private static final int HISTORY_PAGE_SIZE = 20;
    private static final Set<ApprovalStatus> BLOCKING_STATUSES =
            EnumSet.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    @Override
    public LeavePreviewResponse preview(LocalDate startDate, LocalDate endDate, AuthenticatedUser user) {
        Employee employee = requireEmployee(user, false);
        return validateDatesAndQuota(employee, startDate, endDate);
    }

    @Override
    @Transactional
    public LeaveRequestResponse create(CreateLeaveRequest request, AuthenticatedUser user) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException("Vui lòng nhập lý do xin nghỉ.");
        }
        String reason = request.getReason().trim();
        if (reason.length() > 255) {
            throw new BusinessException("Lý do không được vượt quá 255 ký tự.");
        }

        // Serialize leave submissions for the same employee to prevent duplicate pending requests.
        Employee employee = requireEmployee(user, true);
        validateDatesAndQuota(employee, request.getStartDate(), request.getEndDate());
        LeaveRequest created = leaveRequestRepository.saveAndFlush(LeaveRequest.builder()
                .employee(employee)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(reason)
                .status(ApprovalStatus.PENDING)
                .build());
        return toResponse(created);
    }

    @Override
    public LeaveHistoryPage history(int page, AuthenticatedUser user) {
        Employee employee = requireEmployee(user, false);
        if (page < 0 || page > 100_000) {
            throw new BusinessException("Trang lịch sử không hợp lệ.");
        }
        List<LeaveRequestResponse> items = leaveRequestRepository
                .findByEmployee_IdOrderByCreatedAtDescIdDesc(employee.getId(),
                        PageRequest.of(page, HISTORY_PAGE_SIZE))
                .stream().map(this::toResponse).toList();
        long count = leaveRequestRepository.countByEmployee_Id(employee.getId());
        return new LeaveHistoryPage(items, count > ((long) page + 1) * HISTORY_PAGE_SIZE);
    }

    private Employee requireEmployee(AuthenticatedUser user, boolean writeLock) {
        if (user == null || user.getEmployeeId() == null || user.getStoreId() == null) {
            throw new BusinessException("Tài khoản chưa được gắn với nhân viên/cửa hàng.");
        }
        Employee employee = (writeLock
                ? employeeRepository.lockByIdForLeaveRequest(user.getEmployeeId())
                : employeeRepository.findByIdWithStore(user.getEmployeeId()))
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        if (employee.getStore() == null
                || !Objects.equals(employee.getStore().getId(), user.getStoreId())
                || employee.getStatus() != EmployeeStatus.ACTIVE
                || !Boolean.TRUE.equals(employee.getStore().getIsActive())) {
            throw new BusinessException("Nhân viên hoặc cửa hàng không còn hợp lệ. Vui lòng đăng nhập lại.");
        }
        return employee;
    }

    private LeavePreviewResponse validateDatesAndQuota(Employee employee, LocalDate startDate,
                                                       LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("Vui lòng chọn đầy đủ ngày bắt đầu và kết thúc.");
        }
        if (startDate.isBefore(VietnamTime.today())) {
            throw new BusinessException("Không thể xin nghỉ cho ngày trong quá khứ.");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        }
        Integer employeeId = employee.getId();
        Integer storeId = employee.getStore().getId();
        if (leaveRequestRepository
                .existsByEmployee_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId, BLOCKING_STATUSES, endDate, startDate)) {
            throw new BusinessException("Khoảng ngày này trùng với đơn đang chờ hoặc đã duyệt.");
        }
        List<LocalDate> assignedDates = shiftAssignmentRepository.findAssignedWorkDatesForLeave(
                employeeId, storeId, startDate, endDate, AssignmentStatus.ASSIGNED)
                .stream().distinct().toList();
        Map<Integer, Long> requestedByYear = assignedDates.stream()
                .collect(Collectors.groupingBy(LocalDate::getYear, TreeMap::new, Collectors.counting()));
        LocalDate yearStart = LocalDate.of(startDate.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(endDate.getYear(), 12, 31);
        Map<Integer, Long> pendingByYear = shiftAssignmentRepository.findPendingLeaveWorkDates(
                employeeId, storeId, yearStart, yearEnd,
                AssignmentStatus.ASSIGNED, ApprovalStatus.PENDING).stream()
                .collect(Collectors.groupingBy(LocalDate::getYear, HashMap::new, Collectors.counting()));
        int totalDays = employee.getStore().getTotalLeaveDays() == null
                ? 0 : employee.getStore().getTotalLeaveDays();
        List<LeavePreviewResponse.YearDetail> years = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : requestedByYear.entrySet()) {
            int year = entry.getKey();
            int requested = Math.toIntExact(entry.getValue());
            int used = leaveBalanceRepository.findUsedDays(employeeId, year).orElse(0);
            int pending = Math.toIntExact(pendingByYear.getOrDefault(year, 0L));
            int available = totalDays - used - pending;
            if (requested > available) {
                throw new BusinessException("Năm " + year + " chỉ còn "
                        + Math.max(available, 0) + " ngày phép khả dụng, không đủ "
                        + requested + " ngày cần xin.");
            }
            years.add(new LeavePreviewResponse.YearDetail(year, requested,
                    totalDays, used, pending, available));
        }
        return new LeavePreviewResponse(assignedDates.size(), years);
    }

    private LeaveRequestResponse toResponse(LeaveRequest item) {
        return LeaveRequestResponse.builder()
                .id(item.getId())
                .employeeId(item.getEmployee().getId())
                .employeeName(item.getEmployee().getFullName())
                .startDate(item.getStartDate())
                .endDate(item.getEndDate())
                .reason(item.getReason())
                .status(item.getStatus())
                .approvedById(item.getApprovedBy() != null ? item.getApprovedBy().getId() : null)
                .approvedByName(item.getApprovedBy() != null && item.getApprovedBy().getEmployee() != null
                        ? item.getApprovedBy().getEmployee().getFullName() : null)
                .createdAt(item.getCreatedAt())
                .resolvedDate(item.getResolvedDate())
                .build();
    }
}
