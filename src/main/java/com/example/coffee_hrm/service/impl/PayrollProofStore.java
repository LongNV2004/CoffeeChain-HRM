package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Minh chứng thanh toán lương (ảnh chụp giao dịch hoặc PDF) do Admin tải lên. Chỉ thêm mới, không ghi đè. */
@Component
public class PayrollProofStore {

    static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf");

    private final Path directory;

    public PayrollProofStore(@Value("${coffee-hrm.payroll.proof-directory:storage/payroll-proofs}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public record Stored(String key, String fileName) {
    }

    /** Trả về null khi không có file. */
    public Stored save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("Minh chứng thanh toán tối đa 2MB.");
        }
        String original = file.getOriginalFilename() == null ? "" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String extension = extension(original);
        if (!TYPES.containsKey(extension)) {
            throw new BusinessException("Minh chứng thanh toán chỉ nhận ảnh JPG, PNG hoặc file PDF.");
        }
        String key = UUID.randomUUID() + "." + extension;
        try (InputStream input = file.getInputStream()) {
            Files.createDirectories(directory);
            Files.copy(input, resolve(key));
        } catch (IOException ex) {
            throw new BusinessException("Không lưu được minh chứng thanh toán.");
        }
        String name = original.length() > 200 ? original.substring(original.length() - 200) : original;
        return new Stored(key, name);
    }

    public byte[] read(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được minh chứng thanh toán.");
        }
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // File mồ côi không ảnh hưởng dữ liệu thanh toán.
        }
    }

    public static String contentType(String key) {
        return TYPES.getOrDefault(extension(key), "application/octet-stream");
    }

    private Path resolve(String key) {
        Path file = directory.resolve(key).normalize();
        if (!file.startsWith(directory)) {
            throw new BusinessException("Không đọc được minh chứng thanh toán.");
        }
        return file;
    }

    private static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
