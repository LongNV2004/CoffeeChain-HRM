package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ContractFileStore {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final Path directory;

    public ContractFileStore(
            @Value("${coffee-hrm.contract.file-directory:storage/contract-files}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public StoredContractFile store(ContractFilePayload payload) {
        if (payload == null || payload.content() == null || payload.content().length == 0) {
            throw new BusinessException("Vui lòng tải lên file hợp đồng đã ký.");
        }
        if (payload.content().length > MAX_BYTES) {
            throw new BusinessException("File hợp đồng vượt quá dung lượng cho phép (2 MB).");
        }
        String extension = extensionOf(payload.originalName());
        String contentType = contentTypeOf(extension, payload.content());
        String storedName = "c" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.createDirectories(directory);
            Files.write(resolve(storedName), payload.content());
        } catch (IOException ex) {
            throw new BusinessException("Không lưu được file hợp đồng. Hãy thử lại.");
        }
        return new StoredContractFile(storedName, displayName(payload.originalName()), contentType, payload.content().length);
    }

    public byte[] read(String storedName) {
        try {
            Path path = resolve(storedName);
            if (!Files.isRegularFile(path)) {
                throw new BusinessException("Không tìm thấy file hợp đồng.");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new BusinessException("Không tìm thấy file hợp đồng.");
        }
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException | BusinessException ex) {
            // File mồ côi được dọn khi có thể. Lỗi dọn file không được che lỗi gốc.
        }
    }

    private static String extensionOf(String originalName) {
        String name = originalName == null ? "" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException("File hợp đồng phải là PDF hoặc DOCX.");
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!"pdf".equals(extension) && !"docx".equals(extension)) {
            throw new BusinessException("File hợp đồng phải là PDF hoặc DOCX.");
        }
        return extension;
    }

    private static String contentTypeOf(String extension, byte[] content) {
        if ("pdf".equals(extension)) {
            if (!isPdf(content)) {
                throw new BusinessException("Nội dung file không phải PDF hợp lệ.");
            }
            return PDF;
        }
        if (!isDocx(content)) {
            throw new BusinessException("Nội dung file không phải DOCX hợp lệ.");
        }
        return DOCX;
    }

    private static boolean isPdf(byte[] content) {
        return content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-';
    }

    private static boolean isDocx(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
            return false;
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 40) {
                String name = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                if ("word/document.xml".equals(name)) {
                    return true;
                }
            }
        } catch (IOException ex) {
            return false;
        }
        return false;
    }

    private static String displayName(String originalName) {
        String name = originalName == null ? "hop-dong" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("\"", "").replace("\r", "").replace("\n", "").trim();
        if (name.isBlank() || name.length() > 255) {
            return "hop-dong";
        }
        return name;
    }

    private Path resolve(String storedName) {
        if (storedName == null || !storedName.matches("c[0-9a-f]{32}\\.(pdf|docx)")) {
            throw new BusinessException("Không tìm thấy file hợp đồng.");
        }
        Path path = directory.resolve(storedName).normalize();
        if (!path.startsWith(directory)) {
            throw new BusinessException("Không tìm thấy file hợp đồng.");
        }
        return path;
    }

    public record StoredContractFile(String storedName, String originalName, String contentType, long size) {
    }
}
