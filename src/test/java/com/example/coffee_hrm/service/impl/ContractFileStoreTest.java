package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractFileStoreTest {

    @TempDir
    Path directory;

    private ContractFileStore store;

    @BeforeEach
    void setUp() {
        store = new ContractFileStore(directory.toString());
    }

    @Test
    void storesPdfAndDocxAfterCheckingContent() throws Exception {
        byte[] pdf = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        ContractFileStore.StoredContractFile storedPdf = store.store(new ContractFilePayload("hop-dong.pdf", pdf));
        assertTrue(storedPdf.storedName().matches("c[0-9a-f]{32}\\.pdf"));
        assertEquals("application/pdf", storedPdf.contentType());
        assertArrayEquals(pdf, store.read(storedPdf.storedName()));

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        ContractFileStore.StoredContractFile storedDocx = store.store(new ContractFilePayload("a\\hop-dong.docx", out.toByteArray()));
        assertTrue(storedDocx.storedName().endsWith(".docx"));
        assertEquals("hop-dong.docx", storedDocx.originalName());
        assertTrue(Files.exists(directory.resolve(storedDocx.storedName())));
    }

    @Test
    void rejectsWrongTypeOversizeAndUnsafeNames() {
        BusinessException wrong = assertThrows(BusinessException.class,
                () -> store.store(new ContractFilePayload("hop-dong.txt", "hello".getBytes(StandardCharsets.UTF_8))));
        assertEquals("File hợp đồng phải là PDF hoặc DOCX.", wrong.getMessage());

        BusinessException fakePdf = assertThrows(BusinessException.class,
                () -> store.store(new ContractFilePayload("hop-dong.pdf", "hello".getBytes(StandardCharsets.UTF_8))));
        assertEquals("Nội dung file không phải PDF hợp lệ.", fakePdf.getMessage());

        byte[] oversized = new byte[ContractFileStore.MAX_BYTES + 1];
        BusinessException tooLarge = assertThrows(BusinessException.class,
                () -> store.store(new ContractFilePayload("lon.pdf", oversized)));
        assertEquals("File hợp đồng vượt quá dung lượng cho phép (2 MB).", tooLarge.getMessage());

        assertThrows(BusinessException.class, () -> store.read("../secret.pdf"));
        assertThrows(BusinessException.class, () -> store.read("c" + "ab".repeat(16) + ".exe"));
    }
}
