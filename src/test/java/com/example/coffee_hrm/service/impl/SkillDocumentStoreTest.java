package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.SkillFilePayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDocumentStoreTest {

    @TempDir
    Path directory;

    @Test
    void acceptsPdfAndRejectsARenamedTextFile() {
        SkillDocumentStore store = new SkillDocumentStore(directory.toString());
        byte[] pdf = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

        var stored = store.prepare(List.of(new SkillFilePayload("huong-dan.pdf", pdf)), 0);

        assertEquals(1, stored.size());
        assertEquals("application/pdf", stored.getFirst().contentType());
        assertTrue(stored.getFirst().storedName().endsWith(".pdf"));
        assertThrows(BusinessException.class,
                () -> store.prepare(List.of(new SkillFilePayload("ghi-chu.pdf", "khong phai pdf".getBytes(StandardCharsets.UTF_8))), 0));
    }

    @Test
    void rejectsASixthFile() {
        SkillDocumentStore store = new SkillDocumentStore(directory.toString());
        byte[] pdf = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> store.prepare(List.of(new SkillFilePayload("them.pdf", pdf)), 5));

        assertEquals("Mỗi kỹ năng chỉ đính kèm tối đa 5 tài liệu.", ex.getMessage());
    }
}
