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
    private static final Pattern BIRTH = Pattern.compile("(?iu)ngày\\s*sinh\\s*[:\\-]?");
    private static final Pattern ADDRESS = Pattern.compile(
            "(?iu)(?:địa\\s*chỉ\\s*thường\\s*trú|dia\\s*chi\\s*thuong\\s*tru|địa\\s*chỉ\\s*cư\\s*trú|dia\\s*chi\\s*cu\\s*tru|đ\\s*/\\s*c\\s*thường\\s*trú|đc\\s*thường\\s*trú|địa\\s*chỉ|dia\\s*chi)\\s*[:\\-]?\\s*([^\\r\\n]{1,180})");
    private static final Pattern PHONE = Pattern.compile(
            "(?iu)(?:số\\s*|so\\s*)?(?:điện\\s*thoại|dien\\s*thoai|sđt|sdt)\\s*[:\\-]?\\s*(0\\d{8,10})");
    private static final Pattern EMPLOYEE_CODE = Pattern.compile(
            "(?iu)(?:mã\\s*nhân\\s*viên|ma\\s*nhan\\s*vien)\\s*[:\\-]?\\s*([A-Za-z0-9\\-]{1,20})");
    private static final Pattern RANGE = Pattern.compile(
            "(?iu)(?:từ|tu)\\s*ngày\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{4})\\s*(?:đến|den|tới|toi)\\s*ngày\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{4})");
    private static final Pattern START = Pattern.compile(
            "(?iu)ngày\\s*bắt\\s*đầu(?:\\s+làm\\s+việc)?\\s*[:\\-]?");
    private static final Pattern END = Pattern.compile(
            "(?iu)ngày\\s*kết\\s*thúc(?:\\s+hợp\\s*đồng)?(?:\\s*\\([^)]{0,40}\\))?\\s*[:\\-]?");
    private static final Pattern RATE = Pattern.compile("(?iu)(?:đồng|dong)\\s*/\\s*(?:giờ|gio)");
    private static final Pattern SHIFTS = Pattern.compile("(?iu)ca\\s*/\\s*(?:tuần|tuan)");
    private static final Pattern CITIZEN_LABEL = Pattern.compile(
            "(?iu)(?:số\\s*)?(?:cccd|căn\\s*cước(?:\\s*công\\s*dân)?|can\\s*cuoc(?:\\s*cong\\s*dan)?)\\s*[:\\-]?");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private ContractFieldParser() {
    }

    static Parsed parse(String text) {
        String source = text == null ? "" : text.replace('\u00A0', ' ');
        if (!ContractTexts.fold(source).contains("hop dong")) {
            return Parsed.notAContract();
        }
        List<String> warnings = new ArrayList<>();
        Set<String> review = new LinkedHashSet<>();
        String name = personName(source, warnings);
        String citizenId = singleCitizen(source, warnings, review);
        LocalDate birth = labeledDate(BIRTH, source, warnings, review, "ngày sinh", "birthDate");
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
            start = labeledDate(START, source, warnings, review, "ngày bắt đầu", "startDate");
        }
        if (end == null) {
            end = labeledDate(END, source, warnings, review, "ngày kết thúc", "endDate");
        }
        if (start != null && end != null && end.isBefore(start)) {
            warnings.add("Ngày kết thúc đọc được đứng trước ngày bắt đầu. Hãy kiểm tra lại hai mốc này.");
            review.remove("startDate");
            review.remove("endDate");
            start = null;
            end = null;
        }
        BigDecimal salary = singleSalary(source, warnings, review);
        Integer shifts = singleShifts(source, warnings, review);
        return new Parsed(true, name, citizenId, birth, address, phone, employeeCode, start, end, salary, shifts,
                warnings, Set.copyOf(review));
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

    private static String singleCitizen(String source, List<String> warnings, Set<String> review) {
        Matcher matcher = CITIZEN_LABEL.matcher(source);
        Set<String> values = new LinkedHashSet<>();
        boolean reconstructed = false;
        while (matcher.find()) {
            DigitRun run = readDigits(source, matcher.end(), 12);
            if (run != null && run.digits().length() == 12) {
                values.add(run.digits());
                reconstructed = reconstructed || run.reconstructed();
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều số căn cước công dân. Hệ thống không tự chọn.");
            return null;
        }
        if (values.isEmpty()) {
            return null;
        }
        if (reconstructed) {
            review.add("citizenId");
        }
        return values.iterator().next();
    }

    private static String singleCode(String source, String citizenId, List<String> warnings) {
        Matcher matcher = EMPLOYEE_CODE.matcher(source);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            String code = matcher.group(1).replaceAll("\\s+", "").trim();
            String significant = code.replaceAll("[.\\s…_\\-:/]+", "");
            if (significant.isEmpty()) {
                continue;
            }
            values.add(code);
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều giá trị cho mã nhân viên. Hãy kiểm tra trước khi tạo hợp đồng.");
            return null;
        }
        if (values.isEmpty()) {
            return null;
        }
        String code = values.iterator().next();
        String digits = code.replaceAll("\\D", "");
        if (digits.length() == 12 || (citizenId != null && digits.equals(citizenId))) {
            warnings.add("Mã nhân viên đọc được trùng dạng số căn cước. Hệ thống không dùng giá trị này.");
            return null;
        }
        return code;
    }

    private static BigDecimal singleSalary(String source, List<String> warnings, Set<String> review) {
        Matcher matcher = RATE.matcher(source);
        Set<BigDecimal> values = new LinkedHashSet<>();
        boolean reconstructed = false;
        while (matcher.find()) {
            String tail = tailAfterLastLetter(source.substring(Math.max(0, matcher.start() - 48), matcher.start()));
            Money money = joinMoney(tail);
            if (money != null) {
                values.add(money.amount());
                reconstructed = reconstructed || money.reconstructed();
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều mức lương đồng/giờ. Hãy kiểm tra và nhập lại.");
            return null;
        }
        if (values.isEmpty()) {
            return null;
        }
        if (reconstructed) {
            review.add("salary");
        }
        return values.iterator().next();
    }

    private static Integer singleShifts(String source, List<String> warnings, Set<String> review) {
        Matcher matcher = SHIFTS.matcher(source);
        Set<Integer> values = new LinkedHashSet<>();
        boolean reconstructed = false;
        while (matcher.find()) {
            String tail = tailAfterLastLetter(source.substring(Math.max(0, matcher.start() - 40), matcher.start()));
            DigitRun run = readDigits(tail, 0, 2);
            if (run == null || run.digits().isEmpty()) {
                continue;
            }
            int count = Integer.parseInt(run.digits());
            if (count >= 1 && count <= 21) {
                values.add(count);
                reconstructed = reconstructed || run.reconstructed();
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều số ca làm việc mỗi tuần. Hãy kiểm tra và nhập lại.");
            return null;
        }
        if (values.isEmpty()) {
            return null;
        }
        if (reconstructed) {
            review.add("weeklyShiftCount");
        }
        return values.iterator().next();
    }

    private static LocalDate labeledDate(Pattern label, String source, List<String> warnings, Set<String> review,
                                         String name, String key) {
        Matcher matcher = label.matcher(source);
        Set<LocalDate> values = new LinkedHashSet<>();
        boolean reconstructed = false;
        while (matcher.find()) {
            FoundDate found = dateFrom(source, matcher.end());
            if (found != null) {
                values.add(found.date());
                reconstructed = reconstructed || found.reconstructed();
            }
        }
        if (values.size() > 1) {
            warnings.add("Có nhiều giá trị cho " + name + ". Hãy kiểm tra trước khi tạo hợp đồng.");
            return null;
        }
        if (values.isEmpty()) {
            return null;
        }
        if (reconstructed) {
            review.add(key);
        }
        return values.iterator().next();
    }

    private static FoundDate dateFrom(String source, int origin) {
        int index = origin;
        int limit = Math.min(source.length(), origin + 90);
        int skipped = 0;
        while (index < limit && !Character.isDigit(source.charAt(index))) {
            if (skipped++ > 12) {
                return null;
            }
            index++;
        }
        StringBuilder day = new StringBuilder();
        StringBuilder month = new StringBuilder();
        StringBuilder year = new StringBuilder();
        StringBuilder current = day;
        while (index < limit) {
            char character = source.charAt(index);
            if (Character.isDigit(character)) {
                int max = current == year ? 4 : 2;
                if (current.length() >= max) {
                    if (current == year && year.length() == 4) {
                        break;
                    }
                    return null;
                }
                current.append(character);
                index++;
                continue;
            }
            if (character == '.' || Character.isWhitespace(character)) {
                index++;
                continue;
            }
            if (character == '/' || character == '-') {
                if (current.length() == 0) {
                    index++;
                    continue;
                }
                if (current == day) {
                    current = month;
                } else if (current == month) {
                    current = year;
                } else {
                    return null;
                }
                index++;
                continue;
            }
            break;
        }
        if (day.isEmpty() || month.isEmpty() || year.length() != 4) {
            return null;
        }
        LocalDate parsed = date(day + "/" + month + "/" + year);
        if (parsed == null) {
            return null;
        }
        String slice = source.substring(origin, index);
        return new FoundDate(parsed, slice.indexOf('.') >= 0);
    }

    private static DigitRun readDigits(String source, int origin, int maxDigits) {
        int index = origin;
        int limit = Math.min(source.length(), origin + 48);
        StringBuilder digits = new StringBuilder();
        boolean reconstructed = false;
        boolean started = false;
        while (index < limit) {
            char character = source.charAt(index);
            if (Character.isDigit(character)) {
                if (digits.length() == maxDigits) {
                    return null;
                }
                digits.append(character);
                started = true;
                index++;
                continue;
            }
            if (!started && (Character.isWhitespace(character) || character == ':' || character == '.')) {
                if (character == '.') {
                    reconstructed = true;
                }
                index++;
                continue;
            }
            if (started && (character == '.' || Character.isWhitespace(character) || character == '-')) {
                if (character == '.') {
                    reconstructed = true;
                }
                index++;
                continue;
            }
            break;
        }
        if (digits.isEmpty()) {
            return null;
        }
        return new DigitRun(digits.toString(), reconstructed);
    }

    private static String tailAfterLastLetter(String window) {
        int lastLetter = -1;
        for (int index = 0; index < window.length(); index++) {
            if (Character.isLetter(window.charAt(index))) {
                lastLetter = index;
            }
        }
        return window.substring(lastLetter + 1);
    }

    private static Money joinMoney(String tail) {
        Matcher matcher = Pattern.compile("\\d+").matcher(tail);
        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(matcher.group());
        }
        if (parts.isEmpty()) {
            return null;
        }
        boolean reconstructed = tail.indexOf('.') >= 0 && parts.stream().anyMatch(part -> part.length() == 1);
        if (parts.stream().allMatch(part -> part.length() == 1)) {
            String joined = String.join("", parts);
            if (joined.length() > 9) {
                return null;
            }
            return new Money(new BigDecimal(joined), true);
        }
        if (parts.size() >= 2
                && parts.get(0).length() <= 3
                && parts.subList(1, parts.size()).stream().allMatch(part -> part.length() == 3)) {
            return new Money(new BigDecimal(String.join("", parts)), reconstructed);
        }
        if (parts.size() == 2 && parts.get(1).length() <= 2) {
            return new Money(new BigDecimal(parts.get(0) + "." + parts.get(1)), false);
        }
        if (parts.size() == 1) {
            return new Money(new BigDecimal(parts.get(0)), false);
        }
        return null;
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
                  List<String> warnings,
                  Set<String> reviewKeys) {
        static Parsed notAContract() {
            return new Parsed(false, null, null, null, null, null, null, null, null, null, null, List.of(), Set.of());
        }
    }

    private record FoundDate(LocalDate date, boolean reconstructed) {
    }

    private record DigitRun(String digits, boolean reconstructed) {
    }

    private record Money(BigDecimal amount, boolean reconstructed) {
    }
}
