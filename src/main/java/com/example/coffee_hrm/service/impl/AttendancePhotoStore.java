package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class AttendancePhotoStore {

    static final String PHOTO_INVALID = "Ảnh không hợp lệ. Hãy chụp lại.";
    private static final Pattern NAME = Pattern.compile("(?:t)?[0-9]+-(in|out)\\.jpg");

    private final Path directory;

    public AttendancePhotoStore(
            @Value("${coffee-hrm.attendance.photo-directory:storage/attendance-photos}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String save(Integer attendanceId, String kind, byte[] jpeg) {
        if (attendanceId == null || (!"in".equals(kind) && !"out".equals(kind)) || !isJpeg(jpeg)) {
            throw new BusinessException(PHOTO_INVALID);
        }
        return write(attendanceId + "-" + kind + ".jpg", jpeg);
    }

    public String saveTraining(Integer attendanceId, String kind, byte[] jpeg) {
        if (attendanceId == null || (!"in".equals(kind) && !"out".equals(kind)) || !isJpeg(jpeg)) {
            throw new BusinessException(PHOTO_INVALID);
        }
        return write("t" + attendanceId + "-" + kind + ".jpg", jpeg);
    }

    private String write(String name, byte[] jpeg) {
        try {
            Files.createDirectories(directory);
            Files.write(resolve(name), jpeg);
            return name;
        } catch (IOException ex) {
            throw new BusinessException("Không lưu được ảnh. Hãy thử lại.");
        }
    }

    public byte[] read(String name) {
        try {
            Path path = resolve(name);
            if (!Files.isRegularFile(path)) {
                throw new BusinessException("Không tìm thấy ảnh.");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new BusinessException("Không tìm thấy ảnh.");
        }
    }

    private Path resolve(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new BusinessException(PHOTO_INVALID);
        }
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory)) {
            throw new BusinessException(PHOTO_INVALID);
        }
        return path;
    }

    static boolean isJpeg(byte[] jpeg) {
        return jpeg != null
                && jpeg.length >= 3
                && jpeg.length <= 2_000_000
                && (jpeg[0] & 0xFF) == 0xFF
                && (jpeg[1] & 0xFF) == 0xD8;
    }
}
