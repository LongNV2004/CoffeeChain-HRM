package com.example.coffee_hrm.service.ocr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc các trường form từ văn bản đã tách. Không điền giá trị khi không thấy hoặc thấy nhiều giá trị khác nhau.
 */
final class ContractFieldParser {

    private static final Pattern NAME = Pattern.compile(
            "(?iu)(?:họ\\s*(?:và|&)\\s*tên|ho\\s*va\\s*ten)\\s*[:\\-]?\\s*([^\\r\\n]{2,80})");
    private static final Pattern CCCD = Pattern.compile(
            "(?iu)(?:số\\s*|so\\s*)?(?:cccd|căn\\s*cước(?:\\s*công\\s*dân)?|can\\s*cuoc(?:\\s*cong\\s*dan)?)\\s*[:\\-]?\\s*([0-9][0-9\\s.\\-]{10,20}[0-9])");
    private static final Pattern BIRTH = Pattern.compile(
            "(?iu)(?:ngày|ngay)\\s*sinh\\s*[:\\-]?\\s*(\\d{1,2}\\s*[/\\-]\\s*\\d{1,2}\\s*[/\\-]\\s*\\d{4})");
    private static final Pattern ADDRESS = Pattern.compile(
            "(?iu)(?:địa\\s*chỉ\\s*thường\\s*trú|dia\\s*chi\\s*thuong\\s*tru|địa\\s*chỉ\\s*cư\\s*trú|dia\\s*chi\\s*cu\\s*tru|địa\\s*chỉ|dia\\s*chi)\\s*[:\\-]?\\s*([^\\r\\n]{1,180})");
    private static final Pattern PHONE = Pattern.compile(
            "(?iu)(?:số\\s*|so\\s*)?(?:điện\\s*thoại|dien\\s*thoai|sđt|sdt)\\s*[:\\-]?\\s*(0\\d{8,10})");
    private static final Pattern EMPLOYEE_CODE = Pattern.compile(
            "(?iu)(?:mã\\s*nhân\\s*viên|ma\\s*nhan\\s*vien)\\s*[:\\-]?\\s*([A-Za-z0-9\\-]{1,20})");
    private static final Pattern RANGE = Pattern.compile(
            "(?iu)(?:từ|tu)\\s*ngày\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{4})\\s*(?:đến|den|tới|toi)\\s*ngày\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{4})");
    private static final Pattern START = Pattern.compile(
            "(?iu)(?:ngày|ngay)\\s*bắt\\s*đầu[^\\d\\r\\n]{0,40}?(\\d{1,2}\\s*[/\\-]\\s*\\d{1,2}\\s*[/\\-]\\s*\\d{4})");
    private static final Pattern END = Pattern.compile(
            "(?iu)(?:ngày|ngay)\\s*kết\\s*thúc[^\\d\\r\\n]{0,48}?(\\d{1,2}\\s*[/\\-]\\s*\\d{1,2}\\s*[/\\-]\\s*\\d{4})");
    private static final Pattern SALARY = Pattern.compile(
            "(?iu)(\\d{1,3}(?:[.\\s]\\d{3})+|\\d+)(?:[,.]\\d{1,2})?\\s*(?:đồng|dong)\\s*/\\s*(?:giờ|gio)");
    private static final Pattern SHIFTS = Pattern.compile("(?iu)(\\d{1,2})\\s*ca\\s*/\\s*(?:tuần|tuan)");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private ContractFieldParser() {
    }

    static Parsed parse(String text) {
        String source = text == null ? "" : text.replace('\u00A0', ' ');
        if (!ContractTexts.fold(source).contains("hop dong")) {
            return Parsed.notAContract();
        }
        List<String> warnings = new ArrayList<>();
        String name = personName(source, warnings);
        String citizenId = singleCitizen(source, warnings);
        LocalDate birth = singleDate(BIRTH, source, warnings, "ngày sinh");
        String address = clean(single(ADDRESS, source, warnings, "địa chỉ"));
        String phone = single(PHONE, source, warnings, "số điện thoại");
        String employeeCode = singleCode(source, citizenId, warnings);
        LocalDate start = null;
        LocalDate end = null;
        Matcher range = RANGE.matcher(source);
        Set<String> ranges = new LinkedHashSet<>();
        while (range.find()) {
            ranges.add(range.group(1) + "|" + range.group(2));
            start = date(range.group(1));
            end = date(range.group(2));
        }
        if (ranges.size() > 1) {
            warnings.add("Có nhiều khoảng thời hạn hợp đồng. Hãy kiểm tra ngày bắt đầu và ngày kết thúc.");
            start = null;
            end = null;
        }
        if (start == null) {
            start = singleDate(START, source, warnings, "ngày bắt đầu");
        }
        if (end == null) {
            end = singleDate(END, source, warnings, "ngày kết thúc");
        }
        BigDecimal salary = singleSalary(source, warnings);
        Integer shifts = singleShifts(source, warnings);
        return new Parsed(true, name, citizenId, birth, address, phone, employeeCode, start, end, salary, shifts, warnings);
    }

    private static String personName(String source, List<String> warnings) {
        String name = single(NAME, source, warnings, "họ tên");
        if (name == null) {
            return null;
        }
        Matcher marker = Pattern.compile("(?iu)\\s*mã\\s*nhân\\s*viên\\b.*").matcher(name);
        if (marker.find()) {
            name = clean(name.substring(0, marker.start()));
        }
        if (name == null || name.chars().noneMatch(Character::isLetter)) {
            return null;
        }
        return name;
    }

    private static String singleCitizen(String source, List<String> warnings) {
        Matcher matcher = CCCD.matcher(source);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            String digits = matcher.group(1).replaceAll("\\D", "");
            if (digits.length() == 12) {
                values.add(digits);
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều số căn cước công dân. Hệ thống không tự chọn.");
            return null;
        }
        return values.isEmpty() ? null : values.iterator().next();
    }

    private static String singleCode(String source, String citizenId, List<String> warnings) {
        String code = single(EMPLOYEE_CODE, source, warnings, "mã nhân viên");
        if (code == null) {
            return null;
        }
        String digits = code.replaceAll("\\D", "");
        if (digits.length() == 12 || (citizenId != null && digits.equals(citizenId))) {
            warnings.add("Mã nhân viên đọc được trùng dạng số căn cước. Hệ thống không dùng giá trị này.");
            return null;
        }
        return code;
    }

    private static BigDecimal singleSalary(String source, List<String> warnings) {
        Matcher matcher = SALARY.matcher(source);
        Set<BigDecimal> values = new LinkedHashSet<>();
        while (matcher.find()) {
            BigDecimal parsed = money(matcher.group(1));
            if (parsed != null) {
                values.add(parsed);
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều mức lương đồng/giờ. Hãy kiểm tra và nhập lại.");
            return null;
        }
        return values.isEmpty() ? null : values.iterator().next();
    }

    private static Integer singleShifts(String source, List<String> warnings) {
        Matcher matcher = SHIFTS.matcher(source);
        Set<Integer> values = new LinkedHashSet<>();
        while (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            if (count >= 1 && count <= 21) {
                values.add(count);
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều số ca làm việc mỗi tuần. Hãy kiểm tra và nhập lại.");
            return null;
        }
        return values.isEmpty() ? null : values.iterator().next();
    }

    private static String single(Pattern pattern, String source, List<String> warnings, String label) {
        Matcher matcher = pattern.matcher(source);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            String value = clean(matcher.group(1));
            if (value != null) {
                values.add(value);
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều giá trị cho " + label + ". Hãy kiểm tra trước khi tạo hợp đồng.");
            return null;
        }
        return values.isEmpty() ? null : values.iterator().next();
    }

    private static LocalDate singleDate(Pattern pattern, String source, List<String> warnings, String label) {
        Matcher matcher = pattern.matcher(source);
        Set<LocalDate> values = new LinkedHashSet<>();
        while (matcher.find()) {
            LocalDate value = date(matcher.group(1));
            if (value != null) {
                values.add(value);
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều giá trị cho " + label + ". Hãy kiểm tra trước khi tạo hợp đồng.");
            return null;
        }
        return values.isEmpty() ? null : values.iterator().next();
    }

    private static LocalDate date(String raw) {
        String[] parts = raw.replaceAll("\\s+", "").split("[/\\-]");
        if (parts.length != 3) {
            return null;
        }
        try {
            return LocalDate.parse(String.format("%s-%02d-%02d",
                    parts[2], Integer.parseInt(parts[1]), Integer.parseInt(parts[0])), ISO);
        } catch (DateTimeParseException | NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal money(String raw) {
        String digits = raw.replaceAll("[.\\s]", "");
        if (!digits.chars().allMatch(Character::isDigit) || digits.isEmpty()) {
            return null;
        }
        return new BigDecimal(digits);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        int stop = trimmed.indexOf("  ");
        if (stop > 0) {
            trimmed = trimmed.substring(0, stop).trim();
        }
        Matcher following = Pattern.compile(
                "(?iu)\\s+(?:email|giới\\s*tính|gioi\\s*tinh|ngày\\s*cấp|ngay\\s*cap|nơi\\s*cấp|noi\\s*cap)\\b.*")
                .matcher(trimmed);
        if (following.find()) {
            trimmed = trimmed.substring(0, following.start()).trim();
        }
        String letters = trimmed.replaceAll("[.\\s…_\\-:/]+", "");
        if (letters.length() < 2) {
            return null;
        }
        return trimmed;
    }

    record Parsed(boolean contract,
                  String fullName,
                  String citizenId,
                  LocalDate birthDate,
                  String address,
                  String phone,
                  String employeeCode,
                  LocalDate startDate,
                  LocalDate endDate,
                  BigDecimal salary,
                  Integer weeklyShiftCount,
                  List<String> warnings) {
        static Parsed notAContract() {
            return new Parsed(false, null, null, null, null, null, null, null, null, null, null, List.of());
        }
    }
}
