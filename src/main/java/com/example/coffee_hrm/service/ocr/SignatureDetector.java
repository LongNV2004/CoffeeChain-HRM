package com.example.coffee_hrm.service.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tìm nhãn chữ ký trên PDF chữ và kiểm tra nét viết trong vùng bên dưới hoặc bên phải nhãn.
 * Không coi nhãn, dòng kẻ hoặc một dòng tên in là chữ ký.
 */
final class SignatureDetector {

    static final int MAX_PAGES = 8;

    private SignatureDetector() {
    }

    static Result detect(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new IOException("too-many-pages");
            }
            List<Line> lines = lines(document);
            PDFRenderer renderer = new PDFRenderer(document);
            Mark manager = mark(document, renderer, lines, true);
            Mark employee = mark(document, renderer, lines, false);
            return new Result(manager, employee);
        }
    }

    private static Mark mark(PDDocument document, PDFRenderer renderer, List<Line> lines, boolean manager) throws IOException {
        List<Line> labels = lines.stream()
                .filter(line -> manager ? line.managerLabel() : line.employeeLabel())
                .toList();
        if (labels.isEmpty()) {
            return Mark.unknown(manager, "Không xác định được vùng chữ ký trên tài liệu.");
        }
        Line label = labels.get(labels.size() - 1);
        PDPage page = document.getPage(label.page());
        BufferedImage image = renderer.renderImageWithDPI(label.page(), 150);
        float scaleX = image.getWidth() / page.getCropBox().getWidth();
        float scaleY = image.getHeight() / page.getCropBox().getHeight();
        String below = SignatureInk.classify(crop(image, label, scaleX, scaleY, true));
        String beside = SignatureInk.classify(crop(image, label, scaleX, scaleY, false));
        String status = SignatureInk.PRESENT.equals(below) || SignatureInk.PRESENT.equals(beside)
                ? SignatureInk.PRESENT
                : (SignatureInk.UNKNOWN.equals(below) || SignatureInk.UNKNOWN.equals(beside)
                ? SignatureInk.UNKNOWN
                : SignatureInk.ABSENT);
        String reason = switch (status) {
            case SignatureInk.PRESENT -> "Phát hiện nét chữ ký " + (manager ? "bên sử dụng lao động" : "bên người lao động")
                    + " tại trang " + (label.page() + 1) + ".";
            case SignatureInk.ABSENT -> "Vùng ký " + (manager ? "Manager" : "nhân viên")
                    + " chỉ có nhãn hoặc dòng kẻ, không có nét viết tay.";
            default -> "Vùng ký không đủ rõ để kết luận. Cần kiểm tra thủ công.";
        };
        return new Mark(manager, status, label.page() + 1, reason);
    }

    private static BufferedImage crop(BufferedImage image, Line label, float scaleX, float scaleY, boolean below) {
        int x = Math.round((below ? label.x() : label.x() + label.width()) * scaleX);
        int y = Math.round((below ? label.y() + label.height() : Math.max(0, label.y() - 8)) * scaleY);
        int width = Math.round((below ? 230 : 210) * scaleX);
        int height = Math.round((below ? 130 : label.height() + 28) * scaleY);
        x = clamp(x, 0, image.getWidth() - 1);
        y = clamp(y, 0, image.getHeight() - 1);
        width = Math.min(width, image.getWidth() - x);
        height = Math.min(height, image.getHeight() - y);
        if (width < 8 || height < 8) {
            return null;
        }
        return image.getSubimage(x, y, width, height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<Line> lines(PDDocument document) throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                for (TextPosition position : positions) {
                    String unicode = position.getUnicode();
                    if (unicode == null || unicode.isEmpty()) {
                        continue;
                    }
                    glyphs.add(new Glyph(unicode, position.getXDirAdj(), position.getYDirAdj(),
                            position.getWidthDirAdj(), position.getHeightDir(), getCurrentPageNo() - 1));
                }
            }
        };
        stripper.setSortByPosition(true);
        stripper.getText(document);
        glyphs.sort(Comparator.comparingInt(Glyph::page).thenComparing(Glyph::y).thenComparing(Glyph::x));
        List<Line> lines = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            if (!current.isEmpty()) {
                Glyph first = current.get(0);
                Glyph previous = current.get(current.size() - 1);
                float gap = glyph.x() - (previous.x() + previous.width());
                if (first.page() != glyph.page() || Math.abs(first.y() - glyph.y()) > 3 || gap > 36) {
                    lines.add(Line.of(current));
                    current = new ArrayList<>();
                }
            }
            current.add(glyph);
        }
        if (!current.isEmpty()) {
            lines.add(Line.of(current));
        }
        return lines;
    }

    private record Glyph(String text, float x, float y, float width, float height, int page) {
    }

    private record Line(String text, float x, float y, float width, float height, int page) {
        static Line of(List<Glyph> glyphs) {
            StringBuilder text = new StringBuilder();
            float x = Float.MAX_VALUE;
            float y = 0;
            float right = 0;
            float height = 0;
            for (Glyph glyph : glyphs) {
                text.append(glyph.text());
                x = Math.min(x, glyph.x());
                y += glyph.y();
                right = Math.max(right, glyph.x() + glyph.width());
                height = Math.max(height, glyph.height());
            }
            return new Line(text.toString(), x, y / glyphs.size(), Math.max(8, right - x), Math.max(8, height), glyphs.get(0).page());
        }

        boolean managerLabel() {
            String folded = ContractTexts.fold(text);
            return folded.contains("ben a") || folded.contains("dai dien") || folded.contains("nguoi su dung lao dong");
        }

        boolean employeeLabel() {
            String folded = ContractTexts.fold(text);
            return folded.contains("ben b") || folded.contains("nguoi lao dong");
        }
    }

    record Mark(boolean manager, String status, Integer page, String reason) {
        static Mark unknown(boolean manager, String reason) {
            return new Mark(manager, SignatureInk.UNKNOWN, null, reason);
        }
    }

    record Result(Mark manager, Mark employee) {
    }
}
