package com.example.coffee_hrm.service.ocr;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.security.CitizenIds;
import com.example.coffee_hrm.dto.response.ContractOcrView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ContractOcrService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORM_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;

    public ContractOcrService(EmployeeRepository employeeRepository, StoreRepository storeRepository) {
        this.employeeRepository = employeeRepository;
        this.storeRepository = storeRepository;
    }

    public ContractOcrView inspect(AuthenticatedUser actor, String fileName, byte[] content) {
        if (actor == null || actor.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Bạn không có quyền tạo hợp đồng.");
        }
        Prepared prepared = prepare(fileName, content);
        Employee match = match(actor, prepared.parsed());
        return view(fileName, content.length, prepared, match);
    }

    public Acceptance requireAccepted(String fileName, byte[] content, Employee employee) {
        Prepared prepared = prepare(fileName, content);
        if (!prepared.parsed().contract()) {
            throw new BusinessException(prepared.failure() == null
                    ? "Tài liệu không phải hợp đồng lao động hoặc không trích xuất được thông tin cần thiết."
                    : prepared.failure());
        }
        if (prepared.parsed().citizenId() == null) {
            throw new BusinessException("Chưa nhận dạng được số căn cước công dân. Không thể tạo hợp đồng khi chưa đối chiếu được nhân viên.");
        }
        if (employee == null || !prepared.parsed().citizenId().equals(CitizenIds.normalize(employee.getCitizenId()))) {
            throw new BusinessException("Số căn cước trên hợp đồng không khớp hồ sơ nhân viên đã chọn.");
        }
        if (prepared.parsed().fullName() == null) {
            throw new BusinessException("Chưa nhận dạng được họ tên. Không thể tạo hợp đồng khi chưa đối chiếu được nhân viên.");
        }
        if (!ContractTexts.sameName(prepared.parsed().fullName(), employee.getFullName())) {
            throw new BusinessException("Họ tên trên hợp đồng không khớp hồ sơ nhân viên đã chọn.");
        }
        if (!SignatureInk.PRESENT.equals(prepared.manager().status())
                || !SignatureInk.PRESENT.equals(prepared.employee().status())) {
            throw new BusinessException(signatureBlock(prepared));
        }
        return new Acceptance(hash(content), "DAT");
    }

    private Prepared prepare(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException("Vui lòng tải lên file hợp đồng.");
        }
        if (content.length > 2 * 1024 * 1024) {
            throw new BusinessException("File hợp đồng vượt quá dung lượng cho phép (2 MB).");
        }
        ContractDocumentReader.Read read = ContractDocumentReader.read(fileName, content);
        if (read.engineNote() != null && read.text().isBlank()) {
            return Prepared.failed(read.engineNote());
        }
        ContractFieldParser.Parsed parsed = ContractFieldParser.parse(read.text());
        if (!parsed.contract()) {
            return Prepared.failed("Tài liệu không phải hợp đồng lao động hoặc không trích xuất được thông tin cần thiết.");
        }
        SignatureDetector.Mark manager = SignatureDetector.Mark.unknown(true, "Chưa xác định được vùng chữ ký.");
        SignatureDetector.Mark employee = SignatureDetector.Mark.unknown(false, "Chưa xác định được vùng chữ ký.");
        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
            try {
                SignatureDetector.Result detected = SignatureDetector.detect(content);
                manager = detected.manager();
                employee = detected.employee();
            } catch (java.io.IOException ex) {
                manager = SignatureDetector.Mark.unknown(true, "Không đọc được vùng chữ ký trên PDF.");
                employee = SignatureDetector.Mark.unknown(false, "Không đọc được vùng chữ ký trên PDF.");
            }
        } else if (read.scanned()) {
            manager = SignatureDetector.Mark.unknown(true, "Ảnh chưa xác định được đúng vùng chữ ký của Manager.");
            employee = SignatureDetector.Mark.unknown(false, "Ảnh chưa xác định được đúng vùng chữ ký của nhân viên.");
        }
        return new Prepared(parsed, manager, employee, null);
    }

    private Employee match(AuthenticatedUser actor, ContractFieldParser.Parsed parsed) {
        if (parsed == null || !parsed.contract() || parsed.citizenId() == null || parsed.fullName() == null) {
            return null;
        }
        if (actor.getEmployeeId() == null) {
            return null;
        }
        Store store = storeRepository.findByManager_Id(actor.getEmployeeId()).orElse(null);
        if (store == null) {
            return null;
        }
        for (Employee employee : employeeRepository.findByStoreIdWithAccount(store.getId())) {
            if (actor.getEmployeeId().equals(employee.getId())) {
                continue;
            }
            if (parsed.citizenId().equals(CitizenIds.normalize(employee.getCitizenId()))
                    && ContractTexts.sameName(parsed.fullName(), employee.getFullName())) {
                return employee;
            }
        }
        return null;
    }

    private ContractOcrView view(String fileName, int size, Prepared prepared, Employee match) {
        if (prepared.failure() != null) {
            return ContractOcrView.builder()
                    .status("FAILED")
                    .message(prepared.failure())
                    .fileName(fileName)
                    .fileSize(size)
                    .fields(List.of())
                    .warnings(List.of())
                    .managerSignature(signature("MANAGER", prepared.manager()))
                    .employeeSignature(signature("EMPLOYEE", prepared.employee()))
                    .overall("CAN_KIEM_TRA")
                    .allowsCreate(false)
                    .build();
        }
        List<String> warnings = new ArrayList<>(prepared.parsed().warnings());
        if (match == null) {
            warnings.add("Họ tên hoặc số căn cước chưa khớp một hồ sơ nhân viên trong cửa hàng. Hãy kiểm tra và chọn đúng nhân viên.");
        }
        boolean signaturesPresent = SignatureInk.PRESENT.equals(prepared.manager().status())
                && SignatureInk.PRESENT.equals(prepared.employee().status());
        boolean signaturesMissing = SignatureInk.ABSENT.equals(prepared.manager().status())
                || SignatureInk.ABSENT.equals(prepared.employee().status());
        boolean passed = match != null && signaturesPresent;
        String overall;
        if (passed) {
            overall = "DAT";
        } else if (signaturesMissing || (prepared.parsed().citizenId() != null && match == null)) {
            overall = "KHONG_DAT";
        } else {
            overall = "CAN_KIEM_TRA";
        }
        return ContractOcrView.builder()
                .status("SUCCESS")
                .message(passed
                        ? "Đã trích xuất thông tin và phát hiện chữ ký của cả hai bên. Hãy kiểm tra lại trước khi tạo hợp đồng."
                        : "Đã đọc tài liệu. Còn thông tin hoặc chữ ký chưa đạt, hợp đồng chưa được tạo.")
                .fileName(fileName)
                .fileSize(size)
                .fields(fields(prepared.parsed()))
                .suggestedEmployeeId(match == null ? null : match.getId())
                .warnings(warnings)
                .managerSignature(signature("MANAGER", prepared.manager()))
                .employeeSignature(signature("EMPLOYEE", prepared.employee()))
                .overall(overall)
                .allowsCreate(passed)
                .build();
    }

    private static List<ContractOcrView.Field> fields(ContractFieldParser.Parsed parsed) {
        return List.of(
                field("fullName", "Họ và tên", parsed.fullName(), parsed.fullName()),
                field("citizenId", "Số căn cước công dân", parsed.citizenId(), mask(parsed.citizenId())),
                field("birthDate", "Ngày sinh", iso(parsed.birthDate()), display(parsed.birthDate())),
                field("address", "Địa chỉ cư trú", parsed.address(), parsed.address()),
                field("phone", "Số điện thoại", parsed.phone(), parsed.phone()),
                field("employeeCode", "Mã nhân viên trên hợp đồng", parsed.employeeCode(), parsed.employeeCode()),
                field("startDate", "Ngày bắt đầu", iso(parsed.startDate()), display(parsed.startDate())),
                field("endDate", "Ngày kết thúc", iso(parsed.endDate()), display(parsed.endDate())),
                field("salary", "Mức lương (đồng/giờ)", salary(parsed.salary()), salary(parsed.salary())),
                field("weeklyShiftCount", "Số ca/tuần",
                        parsed.weeklyShiftCount() == null ? null : parsed.weeklyShiftCount().toString(),
                        parsed.weeklyShiftCount() == null ? null : parsed.weeklyShiftCount() + " ca/tuần"));
    }

    private static ContractOcrView.Field field(String key, String label, String value, String display) {
        boolean recognized = value != null && !value.isBlank();
        return ContractOcrView.Field.builder()
                .key(key)
                .label(label)
                .value(recognized ? value : null)
                .display(recognized ? display : "Chưa nhận dạng được")
                .recognized(recognized)
                .source("OCR")
                .build();
    }

    private static ContractOcrView.Signature signature(String party, SignatureDetector.Mark mark) {
        SignatureDetector.Mark safe = mark == null ? SignatureDetector.Mark.unknown(false, "Chưa chạy kiểm tra chữ ký.") : mark;
        String label = switch (safe.status()) {
            case SignatureInk.PRESENT -> "Có chữ ký";
            case SignatureInk.ABSENT -> "Không có chữ ký";
            default -> "Chưa xác định";
        };
        return ContractOcrView.Signature.builder()
                .party(party)
                .status(safe.status())
                .label(label)
                .page(safe.page())
                .location(safe.page() == null ? "Chưa xác định vị trí" : "Trang " + safe.page())
                .reason(safe.reason())
                .build();
    }

    private static String signatureBlock(Prepared prepared) {
        if (SignatureInk.ABSENT.equals(prepared.manager().status()) || SignatureInk.ABSENT.equals(prepared.employee().status())) {
            return "Hợp đồng chưa đủ chữ ký của Manager và nhân viên. Không thể tạo hợp đồng.";
        }
        return "Chưa xác định đủ chữ ký của Manager và nhân viên. Không thể tạo hợp đồng khi còn phải kiểm tra thủ công.";
    }

    private static String mask(String citizenId) {
        String masked = CitizenIds.mask(citizenId);
        return masked == null ? null : masked;
    }

    private static String iso(java.time.LocalDate date) {
        return date == null ? null : date.format(FORM_DATE);
    }

    private static String display(java.time.LocalDate date) {
        return date == null ? null : date.format(DISPLAY_DATE);
    }

    private static String salary(BigDecimal salary) {
        return salary == null ? null : salary.stripTrailingZeros().toPlainString();
    }

    private static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record Acceptance(String fileHash, String result) {
    }

    private record Prepared(ContractFieldParser.Parsed parsed,
                            SignatureDetector.Mark manager,
                            SignatureDetector.Mark employee,
                            String failure) {
        static Prepared failed(String message) {
            return new Prepared(ContractFieldParser.Parsed.notAContract(),
                    SignatureDetector.Mark.unknown(true, "Chưa có kết quả chữ ký hợp lệ."),
                    SignatureDetector.Mark.unknown(false, "Chưa có kết quả chữ ký hợp lệ."),
                    message);
        }
    }
}
