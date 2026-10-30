package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.SkillFilePayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class SkillDocumentStore {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_FILES = 5;

    private final Path directory;

    public SkillDocumentStore(
            @Value("${coffee-hrm.training.file-directory:storage/skill-documents}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public List<Stored> prepare(List<SkillFilePayload> files, int alreadyStored) {
        List<SkillFilePayload> present = files == null ? List.of() : files.stream()
                .filter(file -> file != null && file.content() != null && file.content().length > 0)
                .toList();
        if (present.isEmpty()) {
            return List.of();
        }
        if (alreadyStored + present.size() > MAX_FILES) {
            throw new BusinessException("Mỗi kỹ năng chỉ đính kèm tối đa 5 tài liệu.");
        }
        List<Stored> prepared = new ArrayList<>();
        for (SkillFilePayload file : present) {
            if (file.content().length > MAX_BYTES) {
                throw new BusinessException("Mỗi tài liệu không được vượt quá 2 MB.");
            }
            String extension = extensionOf(file.originalName());
            String contentType = contentTypeOf(extension, file.content());
            String storedName = "s" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
            prepared.add(new Stored(storedName, displayName(file.originalName()), contentType, file.content().length, file.content()));
        }
        return prepared;
    }

    public void write(Stored stored) {
        try {
            Files.createDirectories(directory);
            Files.write(resolve(stored.storedName()), stored.content());
        } catch (IOException ex) {
            throw new BusinessException("Không lưu được tài liệu kỹ năng. Hãy thử lại.");
        }
    }

    public byte[] read(String storedName) {
        try {
            Path path = resolve(storedName);
            if (!Files.isRegularFile(path)) {
                throw new BusinessException("Không tìm thấy tài liệu kỹ năng.");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new BusinessException("Không tìm thấy tài liệu kỹ năng.");
        }
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException | BusinessException ex) {
            // File mồ côi được dọn khi có thể.
        }
    }

    private Path resolve(String storedName) {
        if (storedName == null || !storedName.matches("s[0-9a-f]{32}\\.(pdf|docx|jpg|jpeg|png)")) {
            throw new BusinessException("Không tìm thấy tài liệu kỹ năng.");
        }
        Path path = directory.resolve(storedName).normalize();
        if (!path.startsWith(directory)) {
            throw new BusinessException("Không tìm thấy tài liệu kỹ năng.");
        }
        return path;
    }

    private static String extensionOf(String originalName) {
        String name = originalName == null ? "" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException("Tài liệu phải là PDF, DOCX, JPG hoặc PNG.");
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!"pdf".equals(extension) && !"docx".equals(extension)
                && !"jpg".equals(extension) && !"jpeg".equals(extension) && !"png".equals(extension)) {
            throw new BusinessException("Tài liệu phải là PDF, DOCX, JPG hoặc PNG.");
        }
        return extension;
    }

    private static String contentTypeOf(String extension, byte[] content) {
        if ("pdf".equals(extension)) {
            if (content.length < 5 || content[0] != '%' || content[1] != 'P' || content[2] != 'D' || content[3] != 'F') {
                throw new BusinessException("Nội dung file không phải PDF hợp lệ.");
            }
            return "application/pdf";
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            if (content.length < 3 || (content[0] & 0xff) != 0xff || (content[1] & 0xff) != 0xd8 || (content[2] & 0xff) != 0xff) {
                throw new BusinessException("Nội dung file không phải ảnh JPEG hợp lệ.");
            }
            return "image/jpeg";
        }
        if ("png".equals(extension)) {
            if (content.length < 8 || (content[0] & 0xff) != 0x89 || content[1] != 'P' || content[2] != 'N' || content[3] != 'G') {
                throw new BusinessException("Nội dung file không phải ảnh PNG hợp lệ.");
            }
            return "image/png";
        }
        if (!isDocx(content)) {
            throw new BusinessException("Nội dung file không phải DOCX hợp lệ.");
        }
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    private static boolean isDocx(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
            return false;
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 40) {
                if ("word/document.xml".equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException ex) {
            return false;
        }
        return false;
    }

    private static String displayName(String originalName) {
        String name = originalName == null ? "tai-lieu" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("\"", "").replace("\r", "").replace("\n", "").trim();
        if (name.isBlank() || name.length() > 255) {
            return "tai-lieu";
        }
        return name;
    }

    public record Stored(String storedName, String originalName, String contentType, long size, byte[] content) {
    }
}
