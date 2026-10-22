package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class AttendanceReportFileStore {

    private final Path directory;

    public AttendanceReportFileStore(
            @Value("${coffee-hrm.attendance.report-directory:storage/attendance-reports}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public void save(int storeId, int year, int month, int version, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("Không tạo được file Excel của báo cáo.");
        }
        try {
            Files.createDirectories(directory);
            Files.write(path(storeId, year, month, version), bytes);
        } catch (IOException ex) {
            throw new BusinessException("Không lưu được file Excel của báo cáo.");
        }
    }

    public void delete(int storeId, int year, int month, int version) {
        try {
            Files.deleteIfExists(path(storeId, year, month, version));
        } catch (IOException ex) {
            throw new BusinessException("Không xoá được file Excel của lần gửi này.");
        }
    }

    public byte[] read(int storeId, int year, int month, int version) {
        Path file = path(storeId, year, month, version);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file Excel của báo cáo.");
        }
    }

    private Path path(int storeId, int year, int month, int version) {
        String name = storeId + "-" + year + "-" + month + "-v" + version + ".xls";
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory)) {
            throw new BusinessException("Không đọc được file Excel của báo cáo.");
        }
        return file;
    }
}
