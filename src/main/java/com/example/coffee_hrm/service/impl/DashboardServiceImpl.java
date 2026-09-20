package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.dto.response.AdminDashboardView;
import com.example.coffee_hrm.dto.response.ManagerDashboardView;
import com.example.coffee_hrm.dto.response.StaffDashboardView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ShiftChangeRequestRepository shiftChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final TrainingClassRepository trainingClassRepository;

    @Override
    public AdminDashboardView buildAdminDashboard(AuthenticatedUser user) {
        return AdminDashboardView.builder()
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .totalStores(storeRepository.count())
                .activeStores(storeRepository.countByIsActiveTrue())
                .totalEmployees(employeeRepository.count())
                .pendingTrainingClassCount(trainingClassRepository.countByStatus(TrainingClassStatus.PENDING_APPROVAL))
                .build();
    }

    @Override
    public ManagerDashboardView buildManagerDashboard(AuthenticatedUser user) {
        Store store = user.getEmployeeId() == null
                ? null
                : storeRepository.findByManager_Id(user.getEmployeeId()).orElse(null);

        long employeeCount = 0;
        long todayShiftCount = 0;
        long pendingLeaveCount = 0;
        long pendingShiftChangeCount = 0;

        if (store != null) {
            Integer storeId = store.getId();
            employeeCount = employeeRepository.countByStore_IdAndStatusNot(storeId, EmployeeStatus.TERMINATED);
            todayShiftCount = shiftAssignmentRepository.countByEmployee_Store_IdAndWorkDateAndStatus(
                    storeId, LocalDate.now(), AssignmentStatus.ASSIGNED);
            pendingLeaveCount = leaveRequestRepository.countByEmployee_Store_IdAndStatus(storeId, ApprovalStatus.PENDING);
            pendingShiftChangeCount = shiftChangeRequestRepository.countPendingForManager(storeId);
        }

        return ManagerDashboardView.builder()
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .storeId(store != null ? store.getId() : null)
                .storeName(store != null ? store.getStoreName() : null)
                .storeAddress(store != null ? store.getAddress() : null)
                .storeEmployeeCount(employeeCount)
                .todayShiftCount(todayShiftCount)
                .pendingLeaveCount(pendingLeaveCount)
                .pendingShiftChangeCount(pendingShiftChangeCount)
                .build();
    }

    @Override
    public StaffDashboardView buildStaffDashboard(AuthenticatedUser user) {
        Integer employeeId = user.getEmployeeId();
        Employee employee = employeeId == null ? null : employeeRepository.findByIdWithStore(employeeId).orElse(null);

        Attendance todayAttendance = employeeId == null
                ? null
                : attendanceRepository.findByEmployee_IdAndWorkDate(employeeId, LocalDate.now()).orElse(null);

        List<StaffDashboardView.ScheduleItem> upcoming = employeeId == null
                ? List.of()
                : shiftAssignmentRepository.findUpcomingByEmployee(
                        employeeId, LocalDate.now(), AssignmentStatus.ASSIGNED, PageRequest.of(0, 7))
                .stream()
                .map(item -> StaffDashboardView.ScheduleItem.builder()
                        .workDate(item.getWorkDate())
                        .shiftName(item.getShift().getShiftName())
                        .startTime(item.getShift().getStartTime())
                        .endTime(item.getShift().getEndTime())
                        .status(item.getStatus().getDbValue())
                        .build())
                .toList();

        List<StaffDashboardView.LeaveItem> leaves = employeeId == null
                ? List.of()
                : leaveRequestRepository.findByEmployee_IdOrderByCreatedAtDesc(employeeId, PageRequest.of(0, 5))
                .stream()
                .map(item -> StaffDashboardView.LeaveItem.builder()
                        .startDate(item.getStartDate())
                        .endDate(item.getEndDate())
                        .reason(item.getReason())
                        .status(item.getStatus().getDbValue())
                        .build())
                .toList();

        List<StaffDashboardView.ShiftChangeItem> changes = employeeId == null
                ? List.of()
                : shiftChangeRequestRepository.findByEmployee_IdOrderByRequestDateDesc(employeeId, PageRequest.of(0, 5))
                .stream()
                .map(item -> StaffDashboardView.ShiftChangeItem.builder()
                        .requestDate(item.getRequestDate())
                        .reason(item.getReason())
                        .status(item.getStatus().getDbValue())
                        .build())
                .toList();

        List<StaffDashboardView.IncomingShiftChangeItem> incoming = employeeId == null
                ? List.of()
                : shiftChangeRequestRepository.findIncomingRequestsForEmployee(employeeId)
                .stream()
                .map(item -> {
                    String timeRange = (item.getAssignment() != null && item.getAssignment().getShift() != null)
                            ? item.getAssignment().getShift().getStartTime() + " – " + item.getAssignment().getShift().getEndTime()
                            : "—";
                    String shiftName = (item.getAssignment() != null && item.getAssignment().getShift() != null)
                            ? item.getAssignment().getShift().getShiftName()
                            : "Ca làm việc";
                    LocalDate workDate = item.getAssignment() != null ? item.getAssignment().getWorkDate() : null;
                    String proposal = "Đề xuất đổi ca";
                    if (item.getTargetAssignment() != null && item.getTargetAssignment().getShift() != null) {
                        proposal = "Đổi với: " + item.getTargetAssignment().getShift().getShiftName()
                                + " (" + item.getTargetAssignment().getWorkDate() + ")";
                    }
                    return StaffDashboardView.IncomingShiftChangeItem.builder()
                            .requestId(item.getId())
                            .requesterName(item.getEmployee() != null ? item.getEmployee().getFullName() : "Đồng nghiệp")
                            .shiftName(shiftName)
                            .timeRange(timeRange)
                            .workDate(workDate)
                            .reason(item.getReason())
                            .requestDate(item.getRequestDate())
                            .proposalSummary(proposal)
                            .build();
                })
                .toList();

        return StaffDashboardView.builder()
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .phone(employee != null ? employee.getPhone() : null)
                .email(employee != null ? employee.getEmail() : null)
                .storeName(employee != null && employee.getStore() != null ? employee.getStore().getStoreName() : user.getStoreName())
                .employeeStatus(employee != null && employee.getStatus() != null ? employee.getStatus().getDbValue() : null)
                .hireDate(employee != null ? employee.getHireDate() : null)
                .todayAttendanceStatus(todayAttendance != null && todayAttendance.getStatus() != null
                        ? todayAttendance.getStatus().getDbValue() : "Chưa chấm công")
                .todayCheckIn(todayAttendance != null ? todayAttendance.getCheckInTime() : null)
                .todayCheckOut(todayAttendance != null ? todayAttendance.getCheckOutTime() : null)
                .upcomingShifts(upcoming)
                .leaveRequests(leaves)
                .shiftChangeRequests(changes)
                .incomingShiftChanges(incoming)
                .build();
    }
}
