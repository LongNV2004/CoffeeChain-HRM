package com.example.coffee_hrm.service.ocr;

import com.example.coffee_hrm.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes the confirmed contract facts onto the CoffeeHRM blank labor-contract
 * template. A PDF that already contains the employee's citizen ID is returned
 * unchanged, so a signed scan is never stamped a second time.
 */
public final class FilledContractPdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] FONT_CANDIDATES = {
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/tahoma.ttf",
            "C:/Windows/Fonts/times.ttf"
    };

    private FilledContractPdf() {
    }

    public record Data(
            String contractCode,
            LocalDate signedOn,
            String companyName,
            String companyAddress,
            String representativeName,
            String representativeTitle,
            String storeCode,
            String representativeCode,
            String employeeName,
            String employeeCode,
            String citizenId,
            String phone,
            String address,
            String email,
            String position,
            String department,
            String workplace,
            LocalDate startDate,
            LocalDate endDate,
            String salary,
            String weeklyShifts) {
    }

    public static boolean isBlankTemplate(byte[] pdf, String citizenId) {
        String text = textOf(pdf);
        if (text.isBlank() || !normalize(text).contains("coffeehrm")) {
            return false;
        }
        String digits = digits(citizenId);
        return digits.length() < 9 || !digits(text).contains(digits);
    }

    public static byte[] apply(byte[] source, Data data) {
        if (source == null || source.length == 0) {
            throw new BusinessException("File hợp đồng không có nội dung.");
        }
        if (!isBlankTemplate(source, data.citizenId())) {
            return source;
        }
        try (PDDocument document = Loader.loadPDF(source)) {
            PDFont font = loadFont(document);
            List<Line> lines = lines(document);
            paint(document, font, line(lines, 0, "so hop dong"), List.of(clean(data.contractCode())));
            paintSignedOn(document, font, lines, data);
            paint(document, font, line(lines, 0, "ten don vi"), List.of(clean(data.companyName())));
            paint(document, font, firstAddress(lines), List.of(clean(data.companyAddress())));
            paint(document, font, line(lines, 0, "nguoi dai dien"), List.of(clean(data.representativeName())));
            paint(document, font, line(lines, 0, "chuc vu"), List.of(clean(data.representativeTitle())));
            paint(document, font, line(lines, 0, "ma cua hang"), List.of(clean(data.storeCode())));
            paint(document, font, line(lines, 0, "ma nhan vien nguoi dai dien"),
                    List.of(clean(data.representativeCode())));
            paint(document, font, line(lines, 0, "ho va ten"),
                    List.of(clean(data.employeeName()), clean(data.employeeCode())));
            paint(document, font, line(lines, 0, "so cccd"), List.of(clean(data.citizenId())));
            paint(document, font, line(lines, 0, "so dien thoai"), List.of(clean(data.phone())));
            paint(document, font, line(lines, 0, "dia chi thuong tru"), List.of(clean(data.address())));
            paint(document, font, line(lines, 0, "email"), List.of(clean(data.email())));
            paint(document, font, line(lines, 0, "vi tri lam viec"), List.of(clean(data.position())));
            paint(document, font, line(lines, 0, "bo phan"), List.of(clean(data.department())));
            paint(document, font, line(lines, 0, "dia diem lam viec"), List.of(clean(data.workplace())));
            paintDate(document, font, line(lines, 0, "ngay bat dau"), data.startDate());
            paintDate(document, font, line(lines, 0, "ngay ket thuc"), data.endDate());
            paint(document, font, line(lines, 0, "muc luong"), List.of(clean(data.salary())));
            paint(document, font, line(lines, 0, "so ca lam viec"), List.of(clean(data.weeklyShifts())));
            paintSignatures(document, font, lines, data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException("Không ghi được thông tin vào file hợp đồng.");
        }
    }

    private static void paintSignedOn(PDDocument document, PDFont font, List<Line> lines, Data data)
            throws IOException {
        Line line = line(lines, 0, "hom nay");
        if (line == null || data.signedOn() == null) {
            return;
        }
        LocalDate date = data.signedOn();
        paint(document, font, line, List.of(
                String.valueOf(date.getDayOfMonth()),
                String.valueOf(date.getMonthValue()),
                String.valueOf(date.getYear()),
                clean(data.workplace())));
    }

    private static void paintDate(PDDocument document, PDFont font, Line line, LocalDate date)
            throws IOException {
        if (line == null || date == null) {
            return;
        }
        List<Run> runs = dotRuns(line);
        if (runs.size() >= 3) {
            paint(document, font, line, List.of(
                    String.format("%02d", date.getDayOfMonth()),
                    String.format("%02d", date.getMonthValue()),
                    String.valueOf(date.getYear())));
        } else {
            paint(document, font, line, List.of(date.format(DATE)));
        }
    }

    private static void paintSignatures(PDDocument document, PDFont font, List<Line> lines, Data data)
            throws IOException {
        List<Line> names = lines.stream()
                .filter(line -> line.page == 1 && line.normalized().contains("ho ten"))
                .toList();
        if (names.size() >= 2) {
            paint(document, font, names.get(0), List.of(clean(data.representativeName())));
            paint(document, font, names.get(1), List.of(clean(data.employeeName())));
            return;
        }
        if (names.size() == 1) {
            paint(document, font, names.get(0),
                    List.of(clean(data.representativeName()), clean(data.employeeName())));
        }
    }

    private static Line firstAddress(List<Line> lines) {
        for (Line line : lines) {
            String text = line.normalized();
            if (line.page == 0 && text.contains("dia chi")
                    && !text.contains("thuong tru") && !text.contains("dia diem")) {
                return line;
            }
        }
        return null;
    }

    private static Line line(List<Line> lines, int page, String label) {
        for (Line line : lines) {
            if (line.page == page && line.normalized().contains(label)) {
                return line;
            }
        }
        return null;
    }

    private static void paint(PDDocument document, PDFont font, Line line, List<String> values)
            throws IOException {
        if (line == null || values.isEmpty()) {
            return;
        }
        List<Run> runs = dotRuns(line);
        PDPage page = document.getPage(line.page);
        int count = Math.min(runs.size(), values.size());
        for (int i = 0; i < count; i++) {
            String value = values.get(i);
            if (value == null || value.isBlank()) {
                continue;
            }
            Run run = runs.get(i);
            float size = fit(font, value, run.width(), 9f);
            float width = Math.min(run.width(), textWidth(font, value, size) + 1.5f);
            try (PDPageContentStream stream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setNonStrokingColor(Color.WHITE);
                stream.addRect(run.x(), run.baseline() - 1.2f, Math.max(width, 8f), size + 3f);
                stream.fill();
                stream.setNonStrokingColor(Color.BLACK);
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(run.x(), run.baseline());
                stream.showText(value);
                stream.endText();
            }
        }
    }

    private static List<Line> lines(PDDocument document) throws IOException {
        List<Line> lines = new ArrayList<>();
        PositionStripper stripper = new PositionStripper();
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            stripper.positions.clear();
            stripper.setStartPage(page + 1);
            stripper.setEndPage(page + 1);
            stripper.getText(document);
            lines.addAll(group(page, stripper.positions));
        }
        return lines;
    }

    private static List<Line> group(int page, List<TextPosition> positions) {
        List<Line> lines = new ArrayList<>();
        List<TextPosition> current = new ArrayList<>();
        float y = Float.NaN;
        for (TextPosition position : positions) {
            if (current.isEmpty() || Math.abs(position.getYDirAdj() - y) <= 2f) {
                current.add(position);
                y = position.getYDirAdj();
            } else {
                lines.add(new Line(page, current));
                current = new ArrayList<>();
                current.add(position);
                y = position.getYDirAdj();
            }
        }
        if (!current.isEmpty()) {
            lines.add(new Line(page, current));
        }
        return lines;
    }

    private static List<Run> dotRuns(Line line) {
        List<Run> runs = new ArrayList<>();
        float startX = 0;
        float baseline = 0;
        float width = 0;
        boolean open = false;
        for (TextPosition position : line.positions) {
            if (".".equals(position.getUnicode())) {
                if (!open) {
                    startX = position.getXDirAdj();
                    baseline = position.getY();
                    width = 0;
                    open = true;
                }
                width = position.getXDirAdj() + position.getWidth() - startX;
            } else if (open) {
                if (width >= 8f) {
                    runs.add(new Run(startX, baseline, width));
                }
                open = false;
            }
        }
        if (open && width >= 8f) {
            runs.add(new Run(startX, baseline, width));
        }
        return runs;
    }

    private static PDFont loadFont(PDDocument document) throws IOException {
        for (String candidate : FONT_CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return PDType0Font.load(document, path.toFile());
            }
        }
        throw new BusinessException("Không tìm thấy font chữ để ghi nội dung hợp đồng.");
    }

    private static float fit(PDFont font, String value, float boxWidth, float preferred) throws IOException {
        float size = preferred;
        while (size > 6f && textWidth(font, value, size) > boxWidth) {
            size -= 0.5f;
        }
        return size;
    }

    private static float textWidth(PDFont font, String value, float size) throws IOException {
        return font.getStringWidth(value) / 1000f * size;
    }

    private static String textOf(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            return "";
        }
    }

    static String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private record Run(float x, float baseline, float width) {
    }

    private static final class Line {
        private final int page;
        private final List<TextPosition> positions;
        private final String normalized;

        private Line(int page, List<TextPosition> positions) {
            this.page = page;
            this.positions = List.copyOf(positions);
            StringBuilder text = new StringBuilder();
            positions.forEach(position -> text.append(position.getUnicode()));
            this.normalized = normalize(text.toString());
        }

        private String normalized() {
            return normalized;
        }
    }

    private static final class PositionStripper extends PDFTextStripper {
        private final List<TextPosition> positions = new ArrayList<>();

        private PositionStripper() throws IOException {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            positions.addAll(textPositions);
        }
    }
}
