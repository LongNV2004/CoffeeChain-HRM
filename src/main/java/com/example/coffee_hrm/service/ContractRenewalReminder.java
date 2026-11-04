package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.NotificationRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.service.impl.ContractValidityRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Nhắc đúng Manager của cửa hàng một lần khi hợp đồng của nhân viên cửa hàng đó
 * chuyển sang Chờ gia hạn.
 */
@Slf4j
@Service
public class ContractRenewalReminder {

    static final String REFERENCE_TYPE = "EMPLOYMENT_CONTRACT";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmploymentContractRepository contractRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate reminderTransaction;

    public ContractRenewalReminder(EmploymentContractRepository contractRepository,
                                   UserRepository userRepository,
                                   NotificationRepository notificationRepository,
                                   NotificationService notificationService,
                                   PlatformTransactionManager transactionManager) {
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.reminderTransaction = new TransactionTemplate(transactionManager);
        this.reminderTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int remindDue(LocalDate today) {
        if (today == null) {
            return 0;
        }
        List<Integer> ids = contractRepository.findRenewalWindowIds(
                today, today.plusDays(ContractValidityRules.RENEWAL_WINDOW_DAYS));
        int sent = 0;
        for (Integer id : ids) {
            try {
                if (Boolean.TRUE.equals(reminderTransaction.execute(status -> sendOne(id, today)))) {
                    sent++;
                }
            } catch (RuntimeException ex) {
                log.warn("Không gửi được nhắc gia hạn cho hợp đồng {}: {}", id, ex.getMessage());
            }
        }
        return sent;
    }

    public void remindIfDue(Integer contractId) {
        if (contractId == null) {
            return;
        }
        try {
            reminderTransaction.executeWithoutResult(status -> sendOne(contractId, VietnamTime.today()));
        } catch (RuntimeException ex) {
            log.warn("Không gửi được nhắc gia hạn ngay khi lưu hợp đồng {}: {}", contractId, ex.getMessage());
        }
    }

    private boolean sendOne(Integer contractId, LocalDate today) {
        EmploymentContract contract = contractRepository.findReminderTarget(contractId).orElse(null);
        if (contract == null || contract.getId() == null) {
            return false;
        }
        boolean renewed = contractRepository.existsByPreviousContract_Id(contract.getId());
        if (ContractValidityRules.resolve(contract, today, renewed) != ContractValidityStatus.CHO_GIA_HAN) {
            return false;
        }
        if (notificationRepository.existsByTypeAndReferenceTypeAndReferenceId(
                NotificationType.CONTRACT_RENEWAL_DUE, REFERENCE_TYPE, contract.getId())) {
            return false;
        }
        User manager = managerOf(contract);
        if (manager == null) {
            return false;
        }
        Employee employee = contract.getEmployee();
        String employeeName = employee.getFullName() == null ? "nhân viên" : employee.getFullName();
        String contractCode = contract.getContractCode() == null ? "chưa có mã" : contract.getContractCode();
        String endDate = contract.getEndDate() == null ? "chưa rõ" : contract.getEndDate().format(DISPLAY_DATE);
        try {
            notificationService.notifyUsers(
                    List.of(manager),
                    "Hợp đồng chờ gia hạn",
                    "Hợp đồng " + contractCode + " của " + employeeName
                            + " (mã " + employee.getId() + ") đã chuyển sang trạng thái Chờ gia hạn. Ngày hết hạn "
                            + endDate + ".",
                    NotificationType.CONTRACT_RENEWAL_DUE,
                    REFERENCE_TYPE,
                    contract.getId());
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
        return true;
    }

    private User managerOf(EmploymentContract contract) {
        Employee employee = contract.getEmployee();
        Store store = employee == null ? null : employee.getStore();
        Employee managerEmployee = store == null ? null : store.getManager();
        if (managerEmployee == null || managerEmployee.getId() == null) {
            return null;
        }
        return userRepository.findByEmployee_Id(managerEmployee.getId())
                .filter(user -> user.getId() != null
                        && user.getRole() != null
                        && user.getRole().getRoleName() == RoleName.MANAGER)
                .orElse(null);
    }
}
