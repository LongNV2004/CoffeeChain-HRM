package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.ResignationItemStatus;
import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateResignationProposalRequest;
import com.example.coffee_hrm.dto.request.ResignationItemRequest;
import com.example.coffee_hrm.dto.response.ResignationDecisionResult;
import com.example.coffee_hrm.dto.response.ResignationEmployeeOption;
import com.example.coffee_hrm.dto.response.ResignationItemResponse;
import com.example.coffee_hrm.dto.response.ResignationProposalResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.ResignationProposal;
import com.example.coffee_hrm.entity.ResignationProposalItem;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ResignationProposalItemRepository;
import com.example.coffee_hrm.repository.ResignationProposalRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AccountMailService;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ResignationProposalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResignationProposalServiceImpl implements ResignationProposalService {

    static final String DUPLICATE_EMPLOYEE = "Mỗi nhân viên chỉ được xuất hiện một lần trong đề xuất.";
    static final String OUTSIDE_STORE = "Nhân viên không thuộc cửa hàng của bạn.";
    static final String ALREADY_LEFT = "Nhân viên đã nghỉ việc, không thể đưa vào đề xuất.";
    static final String OPEN_PROPOSAL = "Nhân viên đang nằm trong một đề xuất nghỉ việc chưa kết thúc.";
    static final String BEFORE_HIRE = "Ngày nghỉ việc không được trước ngày vào làm.";
    static final String REASON_REQUIRED = "Vui lòng nhập lý do từ chối.";
    static final String ITEM_REASON_REQUIRED = "Vui lòng nhập lý do đề xuất nghỉ việc cho từng nhân sự.";
    static final String ALREADY_PROCESSED = "Nhân sự này đã được xử lý. Không xử lý lại.";
    static final String NOT_STAFF = "Chỉ được đề xuất nghỉ việc cho nhân viên Staff đang làm tại cửa hàng.";
    static final String SELF_PROPOSAL = "Không thể tự đề xuất nghỉ việc cho Manager đang quản lý cửa hàng.";
    static final String EMPTY_LIST = "Vui lòng thêm ít nhất một nhân sự vào danh sách.";
    static final String NO_PENDING = "Không còn nhân sự đang chờ duyệt.";
    private static final int MAX_ITEMS = 30;
    private static final String REFERENCE = "RESIGNATION_PROPOSAL";

    private final ResignationProposalRepository proposalRepository;
    private final ResignationProposalItemRepository itemRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final NotificationService notificationService;
    private final AccountMailService accountMailService;

    @Override
    @Transactional
    public ResignationDecisionResult create(AuthenticatedUser actor, CreateResignationProposalRequest request) {
        Store store = requireManagedStore(actor);
        Employee manager = employeeRepository.findById(actor.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên của Manager."));
        String title = requireTitle(request == null ? null : request.getTitle());
        String note = normalizeOptional(request == null ? null : request.getNote(), 500, "Ghi chú");
        List<PreparedItem> people = prepareItems(store, manager, request == null ? null : request.getItems());

        ResignationProposal proposal = ResignationProposal.builder()
                .store(store)
                .requestedBy(manager)
                .title(title)
                .note(note)
                .status(ResignationProposalStatus.PENDING)
                .items(new ArrayList<>())
                .build();
        int sortOrder = 1;
        for (PreparedItem person : people) {
            ResignationProposalItem item = ResignationProposalItem.builder()
                    .sortOrder(sortOrder++)
                    .employee(person.employee())
                    .position(person.position())
                    .proposedTerminationDate(person.proposedDate())
                    .reason(person.reason())
                    .note(person.note())
                    .status(ResignationItemStatus.PENDING)
                    .build();
            item.setProposal(proposal);
            proposal.getItems().add(item);
        }

        ResignationProposal saved;
        try {
            saved = proposalRepository.save(proposal);
            proposalRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.error("Không lưu được đề xuất nghỉ việc vì danh sách nhân sự bị trùng", ex);
            throw new BusinessException("Không lưu được đề xuất vì có nhân sự bị trùng hoặc đang nằm trong đề xuất khác chưa kết thúc.");
        }

        String managerName = displayName(manager);
        String message = "Manager " + managerName + " đã gửi đề xuất nghỉ việc \"" + saved.getTitle()
                + "\" (mã " + saved.getId() + ", " + saved.getItems().size() + " nhân sự) cho cửa hàng "
                + store.getStoreName() + ".";
        List<User> admins = userRepository.findActiveByRoleName(RoleName.ADMIN);
        notificationService.notifyUsers(
                admins,
                "Đề xuất nghỉ việc mới",
                message,
                NotificationType.RESIGNATION_PROPOSAL_CREATED,
                REFERENCE,
                saved.getId());
        boolean emailSent = sendToUsers(admins, "CoffeeHRM - Đề xuất nghỉ việc mới", message);
        log.info("Manager {} đã gửi đề xuất nghỉ việc {} với {} nhân sự",
                manager.getId(), saved.getId(), saved.getItems().size());
        return ResignationDecisionResult.builder()
                .employeeName(saved.getTitle())
                .emailSent(emailSent)
                .processedCount(saved.getItems().size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResignationProposalResponse> listMine(AuthenticatedUser actor) {
        Store store = requireManagedStore(actor);
        return distinct(proposalRepository.findMine(actor.getEmployeeId(), store.getId())).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResignationProposalResponse getMine(AuthenticatedUser actor, Integer id) {
        Store store = requireManagedStore(actor);
        ResignationProposal proposal = proposalRepository.findDetailById(id)
                .filter(item -> item.getRequestedBy() != null
                        && actor.getEmployeeId().equals(item.getRequestedBy().getId())
                        && item.getStore() != null
                        && store.getId().equals(item.getStore().getId()))
                .orElseThrow(() -> new BusinessException("Không tìm thấy đề xuất nghỉ việc."));
        return toResponse(proposal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResignationEmployeeOption> listSelectableEmployees(AuthenticatedUser actor) {
        Store store = requireManagedStore(actor);
        Employee manager = employeeRepository.findById(actor.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên của Manager."));
        List<Employee> eligible = employeeRepository.findByStoreIdWithAccount(store.getId()).stream()
                .filter(employee -> eligibilityMessage(employee, store, manager) == null)
                .sorted(Comparator.comparing(Employee::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        Set<Integer> openIds = new HashSet<>(itemRepository.findOpenEmployeeIds(
                eligible.stream().map(Employee::getId).toList(),
                ResignationItemStatus.PENDING,
                ResignationItemStatus.APPROVED));
        return eligible.stream()
                .filter(employee -> !openIds.contains(employee.getId()))
                .map(employee -> ResignationEmployeeOption.builder()
                        .id(employee.getId())
                        .fullName(employee.getFullName())
                        .positionLabel(positionLabel(employee))
                        .statusLabel(employee.getStatus() == EmployeeStatus.ON_LEAVE ? "Đang nghỉ phép" : "Đang làm việc")
                        .hireDate(employee.getHireDate() == null ? "" : employee.getHireDate().toString())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String managedStoreLabel(AuthenticatedUser actor) {
        return requireManagedStore(actor).getStoreName();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResignationProposalResponse> listForAdmin(AuthenticatedUser actor,
                                                          Integer storeId,
                                                          ResignationProposalStatus status,
                                                          String keyword) {
        requireAdmin(actor);
        String normalized = normalizeKeyword(keyword);
        Integer proposalId = parseProposalId(normalized);
        return distinct(proposalRepository.search(storeId, status, normalized, proposalId)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResignationProposalResponse getForAdmin(AuthenticatedUser actor, Integer id) {
        requireAdmin(actor);
        return toResponse(requireProposal(id));
    }

    @Override
    @Transactional
    public ResignationDecisionResult approveItem(AuthenticatedUser actor,
                                                 Integer proposalId,
                                                 Integer itemId) {
        requireAdmin(actor);
        LockedProposal locked = lockProposal(proposalId);
        ResignationProposalItem item = requireItem(locked.items(), itemId);
        requirePending(item);
        boolean applied = decideApproval(locked.proposal(), item, requireUser(actor.getUserId()), VietnamTime.today());
        refreshProposalStatus(locked.proposal(), locked.items());
        proposalRepository.save(locked.proposal());
        proposalRepository.flush();
        boolean emailSent = notifyApproved(locked.proposal(), item);
        log.info("Admin {} đã duyệt nhân sự {} trong đề xuất nghỉ việc {}", actor.getUserId(), itemId, proposalId);
        return decision(item, emailSent, applied, 1);
    }

    @Override
    @Transactional
    public ResignationDecisionResult resendDecisionNotice(AuthenticatedUser actor, Integer proposalId, Integer itemId) {
        requireAdmin(actor);
        LockedProposal locked = lockProposal(proposalId);
        ResignationProposalItem item = requireItem(locked.items(), itemId);
        if (item.getStatus() != ResignationItemStatus.APPROVED || item.getAppliedAt() == null) {
            throw new BusinessException("Chỉ gửi lại email cho nhân sự đã được duyệt nghỉ việc.");
        }
        if (item.getDecisionNoticeSentAt() != null) {
            throw new BusinessException("Email thông báo nghỉ việc đã được gửi trước đó.");
        }
        boolean emailSent = sendEmployeeDecision(item);
        if (emailSent) {
            item.setDecisionNoticeSentAt(VietnamTime.now());
        }
        log.info("Admin {} gửi lại email nghỉ việc cho nhân sự {} trong đề xuất {}", actor.getUserId(), itemId, proposalId);
        return decision(item, emailSent, true, 1);
    }

    @Override
    @Transactional
    public ResignationDecisionResult rejectItem(AuthenticatedUser actor,
                                                Integer proposalId,
                                                Integer itemId,
                                                String rejectReason) {
        requireAdmin(actor);
        LockedProposal locked = lockProposal(proposalId);
        ResignationProposalItem item = requireItem(locked.items(), itemId);
        requirePending(item);
        applyRejection(item, requireRejectReason(rejectReason), requireUser(actor.getUserId()));
        refreshProposalStatus(locked.proposal(), locked.items());
        proposalRepository.save(locked.proposal());
        proposalRepository.flush();
        notifyManagerInApp(locked.proposal(), item, false);
        log.info("Admin {} đã từ chối nhân sự {} trong đề xuất nghỉ việc {}", actor.getUserId(), itemId, proposalId);
        return decision(item, false, false, 1);
    }

    @Override
    @Transactional
    public ResignationDecisionResult approvePending(AuthenticatedUser actor, Integer proposalId) {
        requireAdmin(actor);
        LockedProposal locked = lockProposal(proposalId);
        List<ResignationProposalItem> pending = pendingItems(locked.items());
        if (pending.isEmpty()) {
            throw new BusinessException(NO_PENDING);
        }
        User reviewer = requireUser(actor.getUserId());
        LocalDate today = VietnamTime.today();
        boolean anyApplied = false;
        for (ResignationProposalItem item : pending) {
            anyApplied = decideApproval(locked.proposal(), item, reviewer, today) || anyApplied;
        }
        refreshProposalStatus(locked.proposal(), locked.items());
        proposalRepository.save(locked.proposal());
        proposalRepository.flush();
        boolean emailSent = true;
        for (ResignationProposalItem item : pending) {
            emailSent = notifyApproved(locked.proposal(), item) && emailSent;
        }
        log.info("Admin {} đã duyệt {} nhân sự đang chờ trong đề xuất nghỉ việc {}",
                actor.getUserId(), pending.size(), proposalId);
        return ResignationDecisionResult.builder()
                .emailSent(emailSent)
                .terminationApplied(anyApplied)
                .processedCount(pending.size())
                .build();
    }

    @Override
    @Transactional
    public ResignationDecisionResult rejectPending(AuthenticatedUser actor, Integer proposalId, String rejectReason) {
        requireAdmin(actor);
        String reason = requireRejectReason(rejectReason);
        LockedProposal locked = lockProposal(proposalId);
        List<ResignationProposalItem> pending = pendingItems(locked.items());
        if (pending.isEmpty()) {
            throw new BusinessException(NO_PENDING);
        }
        User reviewer = requireUser(actor.getUserId());
        for (ResignationProposalItem item : pending) {
            applyRejection(item, reason, reviewer);
        }
        refreshProposalStatus(locked.proposal(), locked.items());
        proposalRepository.save(locked.proposal());
        proposalRepository.flush();
        for (ResignationProposalItem item : pending) {
            notifyManagerInApp(locked.proposal(), item, false);
        }
        log.info("Admin {} đã từ chối {} nhân sự đang chờ trong đề xuất nghỉ việc {}",
                actor.getUserId(), pending.size(), proposalId);
        return ResignationDecisionResult.builder()
                .emailSent(false)
                .processedCount(pending.size())
                .build();
    }

    @Override
    @Transactional
    public int applyDueTerminations(LocalDate today) {
        LocalDate effectiveDay = today == null ? VietnamTime.today() : today;
        int applied = 0;
        for (Integer id : itemRepository.findDueIds(ResignationItemStatus.APPROVED, effectiveDay)) {
            if (applyLockedDueItem(id, effectiveDay)) {
                applied++;
            }
        }
        return applied;
    }

    @Override
    @Transactional(readOnly = true)
    public int countPendingProposals() {
        return proposalRepository.countByStatusIn(List.of(
                ResignationProposalStatus.PENDING,
                ResignationProposalStatus.PARTIALLY_PROCESSED));
    }

    private boolean applyLockedDueItem(Integer itemId, LocalDate today) {
        ResignationProposalItem item = itemRepository.lockById(itemId).orElse(null);
        if (item == null) {
            return false;
        }
        boolean applied = applyIfDue(item, today);
        if (applied && sendEmployeeDecision(item)) {
            item.setDecisionNoticeSentAt(VietnamTime.now());
        }
        return applied;
    }

    private boolean decideApproval(ResignationProposal proposal,
                                   ResignationProposalItem item,
                                   User reviewer,
                                   LocalDate today) {
        requirePending(item);
        Employee employee = requireCurrentEmployee(item);
        ensureStillEligible(proposal, employee);
        LocalDate approvedDate = today == null ? VietnamTime.today() : today;
        if (employee.getHireDate() != null && approvedDate.isBefore(employee.getHireDate())) {
            throw new BusinessException(BEFORE_HIRE);
        }
        item.setStatus(ResignationItemStatus.APPROVED);
        item.setApprovedTerminationDate(approvedDate);
        item.setRejectReason(null);
        item.setReviewedAt(VietnamTime.now());
        item.setReviewedBy(reviewer);
        return applyIfDue(item, approvedDate);
    }

    private void applyRejection(ResignationProposalItem item, String rejectReason, User reviewer) {
        item.setStatus(ResignationItemStatus.REJECTED);
        item.setRejectReason(rejectReason);
        item.setApprovedTerminationDate(null);
        item.setReviewedAt(VietnamTime.now());
        item.setReviewedBy(reviewer);
    }

    private boolean applyIfDue(ResignationProposalItem item, LocalDate today) {
        if (item.getStatus() != ResignationItemStatus.APPROVED
                || item.getAppliedAt() != null
                || item.getApprovedTerminationDate() == null
                || item.getApprovedTerminationDate().isAfter(today)) {
            return false;
        }
        Employee employee = employeeRepository.lockByIdForLeaveRequest(item.getEmployee().getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên."));
        if (employee.getStatus() != EmployeeStatus.TERMINATED) {
            employee.setStatus(EmployeeStatus.TERMINATED);
            employee.setTerminationDate(item.getApprovedTerminationDate());
        } else if (employee.getTerminationDate() == null) {
            employee.setTerminationDate(item.getApprovedTerminationDate());
        }
        Store store = employee.getStore();
        if (store != null && store.getManager() != null && employee.getId().equals(store.getManager().getId())) {
            store.setManager(null);
        }
        employeeRepository.save(employee);
        disableStaffAccount(employee);
        item.setAppliedAt(VietnamTime.now());
        if (item.getEmployee() != null) {
            item.getEmployee().setStatus(employee.getStatus());
            item.getEmployee().setTerminationDate(employee.getTerminationDate());
        }
        return true;
    }

    private void disableStaffAccount(Employee employee) {
        User account = employee.getUser();
        if (account == null || account.getRole() == null) {
            account = userRepository.findByEmployee_Id(employee.getId()).orElse(account);
        }
        if (account == null || account.getRole() == null || account.getRole().getRoleName() == null) {
            return;
        }
        RoleName role = account.getRole().getRoleName();
        if (role == RoleName.ADMIN || role == RoleName.MANAGER) {
            return;
        }
        if (!Boolean.FALSE.equals(account.getIsActive())) {
            account.setIsActive(false);
            userRepository.save(account);
        }
        employee.setUser(account);
    }

    private void ensureStillEligible(ResignationProposal proposal, Employee employee) {
        if (employee.getStore() == null || proposal.getStore() == null
                || !proposal.getStore().getId().equals(employee.getStore().getId())) {
            throw new BusinessException("Nhân viên không còn thuộc cửa hàng của đề xuất này.");
        }
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException("Nhân viên đã nghỉ việc. Hãy từ chối dòng này nếu không còn cần duyệt.");
        }
        RoleName role = roleOf(employee);
        if (role == RoleName.ADMIN || role == RoleName.MANAGER) {
            throw new BusinessException(NOT_STAFF);
        }
    }

    private Employee requireCurrentEmployee(ResignationProposalItem item) {
        Employee employee = item.getEmployee();
        if (employee == null || employee.getId() == null) {
            throw new BusinessException("Không tìm thấy hồ sơ nhân viên.");
        }
        return employeeRepository.findByIdWithStoreAccount(employee.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên."));
    }

    private List<PreparedItem> prepareItems(Store store, Employee manager, List<ResignationItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(EMPTY_LIST);
        }
        if (requests.size() > MAX_ITEMS) {
            throw new BusinessException("Mỗi đề xuất chứa tối đa 30 nhân sự.");
        }
        Map<Integer, Employee> byId = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findByStoreIdWithAccount(store.getId())) {
            byId.putIfAbsent(employee.getId(), employee);
        }
        Set<Integer> requestedIds = new HashSet<>();
        List<Integer> ids = new ArrayList<>();
        for (ResignationItemRequest request : requests) {
            if (request == null || request.getEmployeeId() == null) {
                throw new BusinessException("Vui lòng chọn nhân viên.");
            }
            if (!requestedIds.add(request.getEmployeeId())) {
                throw new BusinessException(DUPLICATE_EMPLOYEE);
            }
            ids.add(request.getEmployeeId());
        }
        Set<Integer> openIds = new HashSet<>(itemRepository.findOpenEmployeeIds(
                ids, ResignationItemStatus.PENDING, ResignationItemStatus.APPROVED));
        LocalDate today = VietnamTime.today();
        List<PreparedItem> prepared = new ArrayList<>();
        for (ResignationItemRequest request : requests) {
            Employee employee = byId.get(request.getEmployeeId());
            if (employee == null) {
                throw new BusinessException(OUTSIDE_STORE);
            }
            String blocked = eligibilityMessage(employee, store, manager);
            if (OUTSIDE_STORE.equals(blocked) || employee.getStore() == null
                    || !store.getId().equals(employee.getStore().getId())) {
                throw new BusinessException(OUTSIDE_STORE);
            }
            if (blocked != null) {
                throw new BusinessException(blocked + " (" + displayName(employee) + ")");
            }
            if (openIds.contains(employee.getId())) {
                throw new BusinessException(OPEN_PROPOSAL + " (" + displayName(employee) + ")");
            }
            if (employee.getHireDate() != null && today.isBefore(employee.getHireDate())) {
                throw new BusinessException(BEFORE_HIRE);
            }
            String reason = normalizeRequired(request.getReason(), 500, ITEM_REASON_REQUIRED, "Lý do đề xuất nghỉ việc");
            String note = normalizeOptional(request.getNote(), 500, "Ghi chú riêng");
            prepared.add(new PreparedItem(employee, positionLabel(employee), today, reason, note));
        }
        return prepared;
    }

    private String eligibilityMessage(Employee employee, Store store, Employee manager) {
        if (employee == null || employee.getStore() == null || store == null
                || !store.getId().equals(employee.getStore().getId())) {
            return OUTSIDE_STORE;
        }
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            return ALREADY_LEFT;
        }
        if (employee.getStatus() != EmployeeStatus.ACTIVE && employee.getStatus() != EmployeeStatus.ON_LEAVE) {
            return "Nhân viên không ở trạng thái hợp lệ để đề xuất nghỉ việc.";
        }
        if (manager != null && manager.getId().equals(employee.getId())) {
            return SELF_PROPOSAL;
        }
        RoleName role = roleOf(employee);
        if (role == RoleName.ADMIN || role == RoleName.MANAGER) {
            return NOT_STAFF;
        }
        return null;
    }

    private boolean notifyApproved(ResignationProposal proposal, ResignationProposalItem item) {
        notifyManagerInApp(proposal, item, true);
        boolean sent = sendEmployeeDecision(item);
        if (sent) {
            item.setDecisionNoticeSentAt(VietnamTime.now());
        }
        return sent;
    }

    private void notifyManagerInApp(ResignationProposal proposal, ResignationProposalItem item, boolean approved) {
        String employeeName = displayName(item.getEmployee());
        String result = approved
                ? "đã được duyệt. Nhân sự nghỉ việc và tài khoản đăng nhập đã bị vô hiệu hóa."
                : "đã bị từ chối. Lý do: " + item.getRejectReason() + ". Nhân sự vẫn làm việc và tài khoản vẫn hoạt động.";
        String message = "Nhân sự " + employeeName + " trong đề xuất nghỉ việc \"" + proposal.getTitle()
                + "\" (mã " + proposal.getId() + ") " + result;
        notificationService.notifyUsers(
                managerRecipients(proposal),
                approved ? "Đề xuất nghỉ việc đã được duyệt" : "Đề xuất nghỉ việc bị từ chối",
                message,
                approved ? NotificationType.RESIGNATION_PROPOSAL_APPROVED : NotificationType.RESIGNATION_PROPOSAL_REJECTED,
                REFERENCE,
                proposal.getId());
    }

    private boolean sendEmployeeDecision(ResignationProposalItem item) {
        if (item.getDecisionNoticeSentAt() != null) {
            return true;
        }
        Employee employee = item.getEmployee();
        String email = employee == null ? null : employee.getEmail();
        String storeName = null;
        if (item.getProposal() != null && item.getProposal().getStore() != null) {
            storeName = item.getProposal().getStore().getStoreName();
        } else if (employee != null && employee.getStore() != null) {
            storeName = employee.getStore().getStoreName();
        }
        try {
            boolean sent = accountMailService.sendResignationDecision(
                    email,
                    displayName(employee),
                    storeName,
                    item.getReviewedAt() == null ? VietnamTime.now() : item.getReviewedAt());
            if (!sent) {
                log.error("Đã lưu duyệt nghỉ việc cho nhân sự {} nhưng chưa gửi được email quyết định",
                        employee == null ? null : employee.getId());
            }
            return sent;
        } catch (RuntimeException ex) {
            log.error("Gửi email quyết định nghỉ việc thất bại cho nhân sự {} sau khi đã lưu trạng thái",
                    employee == null ? null : employee.getId(), ex);
            return false;
        }
    }

    private List<User> managerRecipients(ResignationProposal proposal) {
        if (proposal.getRequestedBy() == null || proposal.getRequestedBy().getId() == null) {
            return List.of();
        }
        return userRepository.findByEmployee_Id(proposal.getRequestedBy().getId())
                .map(List::of)
                .orElse(List.of());
    }

    private boolean sendToUsers(List<User> recipients, String subject, String message) {
        if (recipients == null || recipients.isEmpty()) {
            return false;
        }
        boolean allSent = true;
        boolean any = false;
        for (User recipient : recipients) {
            String email = emailOf(recipient);
            if (email == null) {
                allSent = false;
                continue;
            }
            any = true;
            String name = recipient.getEmployee() != null ? displayName(recipient.getEmployee()) : recipient.getUsername();
            allSent = accountMailService.sendResignationNotice(email, name, subject, message) && allSent;
        }
        return any && allSent;
    }

    private String emailOf(User user) {
        if (user == null) {
            return null;
        }
        if (user.getEmployee() != null && user.getEmployee().getEmail() != null
                && !user.getEmployee().getEmail().isBlank()) {
            return user.getEmployee().getEmail().trim();
        }
        return user.getUsername();
    }

    private ResignationDecisionResult decision(ResignationProposalItem item,
                                               boolean emailSent,
                                               boolean applied,
                                               int count) {
        return ResignationDecisionResult.builder()
                .employeeName(displayName(item.getEmployee()))
                .approvedTerminationDate(item.getApprovedTerminationDate())
                .emailSent(emailSent)
                .terminationApplied(applied)
                .processedCount(count)
                .build();
    }

    private LockedProposal lockProposal(Integer proposalId) {
        ResignationProposal proposal = proposalRepository.lockById(proposalId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đề xuất nghỉ việc."));
        List<ResignationProposalItem> items = new ArrayList<>(itemRepository.lockByProposalId(proposalId));
        items.sort(Comparator.comparing(ResignationProposalItem::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ResignationProposalItem::getId, Comparator.nullsLast(Integer::compareTo)));
        return new LockedProposal(proposal, items);
    }

    private ResignationProposalItem requireItem(List<ResignationProposalItem> items, Integer itemId) {
        return items.stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân sự trong đề xuất này."));
    }

    private void requirePending(ResignationProposalItem item) {
        if (item.getStatus() != ResignationItemStatus.PENDING) {
            throw new BusinessException(ALREADY_PROCESSED);
        }
    }

    private List<ResignationProposalItem> pendingItems(List<ResignationProposalItem> items) {
        return items.stream()
                .filter(item -> item.getStatus() == ResignationItemStatus.PENDING)
                .toList();
    }

    private void refreshProposalStatus(ResignationProposal proposal, List<ResignationProposalItem> items) {
        long pending = items.stream().filter(item -> item.getStatus() == ResignationItemStatus.PENDING).count();
        if (pending == items.size()) {
            proposal.setStatus(ResignationProposalStatus.PENDING);
        } else if (pending == 0) {
            proposal.setStatus(ResignationProposalStatus.COMPLETED);
        } else {
            proposal.setStatus(ResignationProposalStatus.PARTIALLY_PROCESSED);
        }
    }

    private ResignationProposal requireProposal(Integer id) {
        return proposalRepository.findDetailById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đề xuất nghỉ việc."));
    }

    private ResignationProposalResponse toResponse(ResignationProposal proposal) {
        List<ResignationProposalItem> items = proposal.getItems() == null
                ? List.of()
                : proposal.getItems().stream()
                .sorted(Comparator.comparing(ResignationProposalItem::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ResignationProposalItem::getId, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        int pending = 0;
        int approved = 0;
        int rejected = 0;
        List<ResignationItemResponse> lines = new ArrayList<>();
        for (ResignationProposalItem item : items) {
            if (item.getStatus() == ResignationItemStatus.PENDING) {
                pending++;
            } else if (item.getStatus() == ResignationItemStatus.APPROVED) {
                approved++;
            } else if (item.getStatus() == ResignationItemStatus.REJECTED) {
                rejected++;
            }
            lines.add(toItem(item, proposal));
        }
        return ResignationProposalResponse.builder()
                .id(proposal.getId())
                .title(proposal.getTitle())
                .note(proposal.getNote())
                .storeId(proposal.getStore() == null ? null : proposal.getStore().getId())
                .storeName(proposal.getStore() == null ? null : proposal.getStore().getStoreName())
                .managerId(proposal.getRequestedBy() == null ? null : proposal.getRequestedBy().getId())
                .managerName(proposal.getRequestedBy() == null ? null : proposal.getRequestedBy().getFullName())
                .status(proposal.getStatus())
                .statusLabel(proposal.getStatus() == null ? null : proposal.getStatus().getLabel())
                .outcomeLabel(outcomeLabel(proposal.getStatus(), approved, rejected, items.size()))
                .createdAt(proposal.getCreatedAt())
                .totalCount(items.size())
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .items(lines)
                .build();
    }

    private ResignationItemResponse toItem(ResignationProposalItem item, ResignationProposal proposal) {
        Employee employee = item.getEmployee();
        User reviewer = item.getReviewedBy();
        String reviewerName = null;
        if (reviewer != null) {
            reviewerName = reviewer.getEmployee() != null && reviewer.getEmployee().getFullName() != null
                    ? reviewer.getEmployee().getFullName()
                    : reviewer.getUsername();
        }
        return ResignationItemResponse.builder()
                .id(item.getId())
                .employeeId(employee == null ? null : employee.getId())
                .employeeName(employee == null ? null : employee.getFullName())
                .positionLabel(item.getPosition())
                .storeId(proposal.getStore() == null ? null : proposal.getStore().getId())
                .storeName(proposal.getStore() == null ? null : proposal.getStore().getStoreName())
                .hireDate(employee == null || employee.getHireDate() == null ? "" : employee.getHireDate().toString())
                .currentStatusLabel(employeeStatusLabel(employee == null ? null : employee.getStatus()))
                .proposedTerminationDate(item.getProposedTerminationDate())
                .approvedTerminationDate(item.getApprovedTerminationDate())
                .reason(item.getReason())
                .note(item.getNote())
                .status(item.getStatus())
                .statusLabel(item.getStatus() == null ? null : item.getStatus().getLabel())
                .rejectReason(item.getRejectReason())
                .reviewedAt(item.getReviewedAt())
                .reviewedByName(reviewerName)
                .applied(item.getAppliedAt() != null)
                .decisionNoticeSent(item.getDecisionNoticeSentAt() != null)
                .build();
    }

    private String outcomeLabel(ResignationProposalStatus status, int approved, int rejected, int total) {
        if (status != ResignationProposalStatus.COMPLETED || total == 0) {
            return null;
        }
        if (rejected == 0 && approved == total) {
            return "Đã duyệt toàn bộ";
        }
        if (approved == 0 && rejected == total) {
            return "Đã từ chối toàn bộ";
        }
        return "Xử lý hỗn hợp";
    }

    private String employeeStatusLabel(EmployeeStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case ACTIVE -> "Đang làm việc";
            case ON_LEAVE -> "Đang nghỉ phép";
            case TERMINATED -> "Đã nghỉ việc";
        };
    }

    private String positionLabel(Employee employee) {
        RoleName role = roleOf(employee);
        if (role == RoleName.MANAGER) {
            return "Quản lý";
        }
        if (role == RoleName.ADMIN) {
            return "Admin";
        }
        return "Nhân viên";
    }

    private RoleName roleOf(Employee employee) {
        if (employee == null || employee.getUser() == null || employee.getUser().getRole() == null) {
            return null;
        }
        return employee.getUser().getRole().getRoleName();
    }

    private List<ResignationProposal> distinct(List<ResignationProposal> proposals) {
        Map<Integer, ResignationProposal> unique = new LinkedHashMap<>();
        for (ResignationProposal proposal : proposals) {
            unique.putIfAbsent(proposal.getId(), proposal);
        }
        return new ArrayList<>(unique.values());
    }

    private Store requireManagedStore(AuthenticatedUser actor) {
        requireManager(actor);
        return storeRepository.findByManager_Id(actor.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được gán làm quản lý cửa hàng."));
    }

    private void requireManager(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER || actor.getEmployeeId() == null) {
            throw new BusinessException("Chỉ Manager mới được thao tác đề xuất nghỉ việc của cửa hàng mình.");
        }
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.getRoleName() != RoleName.ADMIN) {
            throw new BusinessException("Chỉ Admin mới được duyệt hoặc từ chối đề xuất nghỉ việc.");
        }
    }

    private User requireUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản người dùng."));
    }

    private String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("Vui lòng nhập tên đề xuất.");
        }
        String normalized = title.trim();
        if (normalized.length() > 150) {
            throw new BusinessException("Tên đề xuất tối đa 150 ký tự.");
        }
        return normalized;
    }

    private String requireRejectReason(String reason) {
        return normalizeRequired(reason, 500, REASON_REQUIRED, "Lý do từ chối");
    }

    private String normalizeRequired(String value, int max, String blankMessage, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(blankMessage);
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new BusinessException(label + " tối đa " + max + " ký tự.");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new BusinessException(label + " tối đa " + max + " ký tự.");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private Integer parseProposalId(String keyword) {
        if (keyword == null || !keyword.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Integer.valueOf(keyword);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String displayName(Employee employee) {
        if (employee == null || employee.getFullName() == null || employee.getFullName().isBlank()) {
            return "nhân sự";
        }
        return employee.getFullName().trim();
    }

    private record PreparedItem(Employee employee, String position, LocalDate proposedDate, String reason, String note) {
    }

    private record LockedProposal(ResignationProposal proposal, List<ResignationProposalItem> items) {
    }
}
