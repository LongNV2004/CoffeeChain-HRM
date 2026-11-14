package com.example.coffee_hrm.security;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.service.impl.ContractValidityRules;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quyền của Manager theo hợp đồng. Dùng cùng quy tắc ngày của {@link ContractValidityRules}.
 *
 * <ul>
 *   <li><b>Quyền cá nhân</b> (hồ sơ, hợp đồng, chấm công, xem đào tạo): luôn có, kể cả khi chưa có hợp đồng.</li>
 *   <li><b>Tham gia đào tạo</b> chứng chỉ quản lý: cần hợp đồng đang hiệu lực hoặc chờ hiệu lực
 *       ({@link #allowsTraining}).</li>
 *   <li><b>Nghiệp vụ quản lý cửa hàng</b>: cần hợp đồng đang hiệu lực ({@link Limit#NONE})
 *       và chứng chỉ quản lý (xem {@link ManagerOperationGuard}).</li>
 * </ul>
 */
public final class ManagerContractAccess {

    public static final String REQUEST_LIMIT = "coffee.managerContractLimit";

    public static final String NO_CONTRACT_MESSAGE =
            "Tài khoản của bạn đang chờ hoàn thiện hợp đồng lao động. Vui lòng liên hệ Admin để được hỗ trợ.";

    public static final String WAITING_START_MESSAGE =
            "Hợp đồng của bạn chưa đến ngày hiệu lực. Bạn đã có thể tham gia đào tạo chứng chỉ quản lý; "
                    + "chức năng quản lý cửa hàng sẽ mở khi hợp đồng có hiệu lực và bạn đạt chứng chỉ quản lý.";

    public static final String NOT_IN_FORCE_MESSAGE =
            "Hợp đồng của bạn chưa có hiệu lực hoặc không còn hiệu lực nên chưa dùng được chức năng quản lý cửa hàng.";

    public static final String TRAINING_REQUIRES_CONTRACT =
            "Manager cần có hợp đồng lao động đang hiệu lực hoặc chờ hiệu lực mới được tham gia đào tạo chứng chỉ quản lý.";

    public static final String PROFILE_REDIRECT = "/profile?limited=contract";

    private static final Pattern OWN_CONTRACT_FILE = Pattern.compile("^/manager/contract/(\\d+)/file$");

    public enum Limit {
        /** Hợp đồng đang hiệu lực: không bị giới hạn bởi hợp đồng. */
        NONE,
        /** Chưa có hợp đồng nào. */
        NO_CONTRACT,
        /** Có hợp đồng hợp lệ nhưng chưa đến ngày hiệu lực: được tham gia đào tạo. */
        WAITING_START,
        /** Hợp đồng đã hết hạn, đã chấm dứt hoặc không gia hạn và đã kết thúc. */
        NOT_IN_FORCE
    }

    private ManagerContractAccess() {
    }

    public static Limit assess(List<EmploymentContract> contracts, LocalDate today) {
        if (contracts == null || contracts.isEmpty()) {
            return Limit.NO_CONTRACT;
        }
        if (today == null) {
            return Limit.NOT_IN_FORCE;
        }
        EmploymentContract current = ContractValidityRules.applicable(contracts, today);
        if (current != null) {
            ContractValidityStatus status = ContractValidityRules.resolve(
                    current, today, hasDirectRenewal(contracts, current.getId()));
            if (!status.isClosed() && status != ContractValidityStatus.CHO_HIEU_LUC) {
                return Limit.NONE;
            }
        }
        return allowsTraining(contracts, today) ? Limit.WAITING_START : Limit.NOT_IN_FORCE;
    }

    /**
     * Được tham gia đào tạo chứng chỉ quản lý: có hợp đồng còn cho làm việc trong ngày
     * (đang hiệu lực, chờ gia hạn, đã/không gia hạn nhưng chưa kết thúc) hoặc hợp đồng chờ hiệu lực.
     * Dùng lại {@link ContractValidityRules#openContract}; hợp đồng đã hết hạn/chấm dứt không được.
     */
    public static boolean allowsTraining(List<EmploymentContract> contracts, LocalDate today) {
        return ContractValidityRules.openContract(contracts, today) != null;
    }

    public static String message(Limit limit) {
        return switch (limit == null ? Limit.NONE : limit) {
            case NO_CONTRACT -> NO_CONTRACT_MESSAGE;
            case WAITING_START -> WAITING_START_MESSAGE;
            case NOT_IN_FORCE -> NOT_IN_FORCE_MESSAGE;
            case NONE -> null;
        };
    }

    /** Giữ tương thích: kiểm tra như Manager chưa có hợp đồng, không phải trang chấm công cá nhân. */
    public static boolean allowsWhenLimited(String path, String method) {
        return allowsWhenLimited(path, method, Limit.NO_CONTRACT, false);
    }

    /**
     * Khi bị giới hạn theo hợp đồng, Manager vẫn dùng được các quyền cá nhân. Dashboard và nghiệp vụ
     * cửa hàng không nằm trong danh sách này.
     *
     * @param selfHistory request xem lịch sử chấm công của chính mình ({@code ?self=true})
     */
    public static boolean allowsWhenLimited(String path, String method, Limit limit, boolean selfHistory) {
        if (path == null || path.isBlank()) {
            return true;
        }
        if (isPublic(path) || "/logout".equals(path) || "/login".equals(path)) {
            return true;
        }
        if (allowsPersonal(path, method, selfHistory)) {
            return true;
        }
        return limit == Limit.WAITING_START && allowsTrainingParticipation(path, method);
    }

    /**
     * Nhóm 1 – quyền cá nhân: hồ sơ, hợp đồng của mình, thông báo, lịch sử chấm công của mình
     * và xem thông tin lớp đào tạo. Không phụ thuộc hợp đồng hay chứng chỉ.
     */
    public static boolean allowsPersonal(String path, String method, boolean selfHistory) {
        if (path == null) {
            return false;
        }
        if ("POST".equalsIgnoreCase(method)) {
            return "/profile".equals(path)
                    || "/profile/password".equals(path)
                    || "/notifications/read-all".equals(path)
                    || path.matches("/notifications/\\d+/read");
        }
        if (!isRead(method)) {
            return false;
        }
        return "/profile".equals(path)
                || "/profile/contract".equals(path)
                || "/profile/contracts".equals(path)
                || "/profile/history".equals(path)
                || "/notifications".equals(path)
                || "/notifications/feed".equals(path)
                // Lịch sử chấm công cá nhân (theo ngày/khoảng ngày) và ảnh chấm công của chính mình;
                // service đọc ảnh tự kiểm tra quyền sở hữu.
                || ("/attendance/manager/history".equals(path) && selfHistory)
                || path.matches("/attendance/photos/\\d+/(in|out)")
                || path.matches("/attendance/training-photos/\\d+/(in|out)")
                // Xem thông tin cơ bản của lớp đào tạo.
                || "/training".equals(path)
                || path.matches("/training/classes/\\d+");
    }

    /** Tham gia lớp đào tạo: chấm công buổi học và xem tài liệu lớp. */
    public static boolean allowsTrainingParticipation(String path, String method) {
        if (path == null) {
            return false;
        }
        if ("POST".equalsIgnoreCase(method)) {
            return path.matches("/attendance/manager/training/check-(in|out)(/preview)?");
        }
        return isRead(method)
                && ("/attendance/manager/choose".equals(path)
                || "/attendance/manager/training".equals(path)
                || path.matches("/training/classes/\\d+/documents/\\d+"));
    }

    /** Tải file hợp đồng của chính mình (không phải của nhân viên khác). */
    public static boolean ownsContractFile(String path, String method, Integer employeeId,
                                           EmploymentContractRepository contracts) {
        if (!isRead(method) || employeeId == null || contracts == null || path == null) {
            return false;
        }
        Matcher matcher = OWN_CONTRACT_FILE.matcher(path);
        if (!matcher.matches()) {
            return false;
        }
        EmploymentContract contract = contracts.findDetailById(Integer.valueOf(matcher.group(1))).orElse(null);
        return contract != null
                && contract.getEmployee() != null
                && employeeId.equals(contract.getEmployee().getId());
    }

    private static boolean isRead(String method) {
        return method == null || "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private static boolean hasDirectRenewal(List<EmploymentContract> contracts, Integer contractId) {
        return ContractValidityRules.hasDirectRenewal(contracts, contractId);
    }

    private static boolean isPublic(String path) {
        return "/".equals(path)
                || "/coffee".equals(path)
                || "/error".equals(path)
                || "/favicon.ico".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/");
    }
}
