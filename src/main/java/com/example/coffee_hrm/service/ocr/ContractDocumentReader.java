package com.example.coffee_hrm.service.ocr;

import com.example.coffee_hrm.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Đọc chữ từ PDF văn bản bằng PDFBox. PDF ảnh quét và JPG/PNG dùng Tesseract cài trên máy nếu có.
 * Không gửi tài liệu ra dịch vụ bên ngoài.
 */
final class ContractDocumentReader {

    private static final int MAX_PAGES = SignatureDetector.MAX_PAGES;

    private ContractDocumentReader() {
    }

    static Read read(String fileName, byte[] content) {
        String extension = extension(fileName);
        if ("pdf".equals(extension)) {
            if (!isPdf(content)) {
                throw new BusinessException("Nội dung file không phải PDF hợp lệ.");
            }
            return readPdf(content);
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            if (!isJpeg(content)) {
                throw new BusinessException("Nội dung file không phải ảnh JPEG hợp lệ.");
            }
            return readImage(content);
        }
        if ("png".equals(extension)) {
            if (!isPng(content)) {
                throw new BusinessException("Nội dung file không phải ảnh PNG hợp lệ.");
            }
            return readImage(content);
        }
        if ("docx".equals(extension)) {
            throw new BusinessException("Không đọc được chữ ký trên file DOCX. Hãy tải hợp đồng dạng PDF hoặc ảnh.");
        }
        throw new BusinessException("File hợp đồng phải là PDF, JPG, JPEG hoặc PNG.");
    }

    private static Read readPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new BusinessException("Hợp đồng vượt quá " + MAX_PAGES + " trang.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text != null && text.strip().length() >= 40) {
                return new Read(text, false, null);
            }
            String scanned = tesseractPdf(document);
            if (scanned == null) {
                return new Read("", true, "PDF là ảnh quét và máy chưa có Tesseract với gói ngôn ngữ vie, nên không đọc được chữ.");
            }
            return new Read(scanned, true, null);
        } catch (InvalidPasswordException ex) {
            throw new BusinessException("File PDF đang được đặt mật khẩu. Hãy tải bản không khóa.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file PDF.");
        }
    }

    private static Read readImage(byte[] content) {
        if (!tesseractAvailable()) {
            return new Read("", true, "Máy chưa có Tesseract với gói ngôn ngữ vie, nên không đọc được chữ trên ảnh.");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                return new Read("", true, "Không đọc được ảnh hợp đồng.");
            }
            String text = tesseract(image);
            if (text == null || text.isBlank()) {
                return new Read("", true, "OCR không đọc được nội dung trên ảnh.");
            }
            return new Read(text, true, null);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            return new Read("", true, "Không đọc được ảnh hợp đồng.");
        }
    }

    private static String tesseractPdf(PDDocument document) throws IOException {
        if (!tesseractAvailable()) {
            return null;
        }
        org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
        StringBuilder text = new StringBuilder();
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            BufferedImage image = renderer.renderImageWithDPI(page, 150);
            String pageText = tesseract(image);
            if (pageText == null) {
                return null;
            }
            text.append(pageText).append('\n');
        }
        return text.toString();
    }

    private static String tesseract(BufferedImage image) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("coffee-ocr-");
            Path imageFile = directory.resolve("page.png");
            ImageIO.write(image, "png", imageFile.toFile());
            Process process = new ProcessBuilder("tesseract", imageFile.toString(), "stdout", "-l", "vie")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(25, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("OCR hết thời gian chờ. Hãy thử lại với file rõ hơn.");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new BusinessException("OCR không đọc được nội dung hợp đồng.");
            }
            return output;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("OCR bị gián đoạn. Hãy thử lại.");
        } finally {
            deleteTemp(directory);
        }
    }

    private static boolean tesseractAvailable() {
        try {
            Process process = new ProcessBuilder("tesseract", "--version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.exitValue() == 0 && output.toLowerCase(Locale.ROOT).contains("tesseract");
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void deleteTemp(Path directory) {
        if (directory == null) {
            return;
        }
        try (var files = Files.list(directory)) {
            files.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Thư mục tạm được dọn khi có thể.
                }
            });
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // Không ghi nội dung hợp đồng ra log.
        }
    }

    private static String extension(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException("File hợp đồng phải là PDF, JPG, JPEG hoặc PNG.");
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isPdf(byte[] content) {
        return content.length >= 5 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
    }

    private static boolean isJpeg(byte[] content) {
        return content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
    }

    private static boolean isPng(byte[] content) {
        return content.length >= 8
                && (content[0] & 0xff) == 0x89
                && content[1] == 'P' && content[2] == 'N' && content[3] == 'G';
    }

    record Read(String text, boolean scanned, String engineNote) {
    }
}
