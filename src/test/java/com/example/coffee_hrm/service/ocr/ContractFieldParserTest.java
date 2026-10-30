package com.example.coffee_hrm.service.ocr;

import com.example.coffee_hrm.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractFieldParserTest {

    @Test
    void readsAClearContractAndIgnoresTheContractNumber() {
        String text = """
                HỢP ĐỒNG LAO ĐỘNG
                Số hợp đồng: HD202610-0001
                Họ và tên: Nguyễn Văn An
                Số CCCD: 079203000123
                Ngày sinh: 01/02/2000
                Địa chỉ cư trú: 12 Nguyễn Trãi
                Số điện thoại: 0901234567
                Mã nhân viên: NV030
                Từ ngày 01/10/2026 đến ngày 01/10/2027
                Mức lương 25.000 đồng/giờ
                Số ca làm việc 7 ca/tuần
                """;

        ContractFieldParser.Parsed parsed = ContractFieldParser.parse(text);

        assertTrue(parsed.contract());
        assertEquals("Nguyễn Văn An", parsed.fullName());
        assertEquals("079203000123", parsed.citizenId());
        assertEquals("NV030", parsed.employeeCode());
        assertEquals(new BigDecimal("25000"), parsed.salary());
        assertEquals(7, parsed.weeklyShiftCount());
        assertEquals(java.time.LocalDate.of(2026, 10, 1), parsed.startDate());
        assertEquals(java.time.LocalDate.of(2027, 10, 1), parsed.endDate());
    }

    @Test
    void doesNotInventValuesWhenTheDocumentIsNotAContract() {
        ContractFieldParser.Parsed parsed = ContractFieldParser.parse("Hoa don ban hang 25000 dong");

        assertFalse(parsed.contract());
        assertNull(parsed.fullName());
        assertNull(parsed.citizenId());
        assertNull(parsed.salary());
    }

    @Test
    void leavesAmbiguousSalaryAndMissingCitizenBlank() {
        String text = """
                Hợp đồng lao động
                Họ và tên: Nguyễn Văn An
                Mức lương 25.000 đồng/giờ
                Phụ cấp 30.000 đồng/giờ
                Số ca 5 ca/tuần
                """;

        ContractFieldParser.Parsed parsed = ContractFieldParser.parse(text);

        assertNull(parsed.citizenId());
        assertNull(parsed.salary());
        assertEquals(5, parsed.weeklyShiftCount());
        assertTrue(parsed.warnings().stream().anyMatch(warning -> warning.contains("lương")));
    }

    @Test
    void blankCoffeeTemplateDoesNotInventPersonalData() {
        String text = """
                HỢP ĐỒNG LAO ĐỘNG
                Số hợp đồng: ........................................................
                BÊN B — NGƯỜI LAO ĐỘNG
                Họ và tên ................................................ Mã nhân viên ................................
                Ngày sinh ................................................ Giới tính ................................
                Số CCCD ................................................ Ngày cấp ................................
                Số điện thoại ................................
                Địa chỉ thường trú ................................................ Email ................................
                Ngày bắt đầu làm việc: ....... / ....... / ............
                Ngày kết thúc hợp đồng (nếu có): ....... / ....... / ............
                Mức lương: ........................................ đồng/giờ
                Số ca làm việc theo hợp đồng: ............ ca/tuần.
                """;

        ContractFieldParser.Parsed parsed = ContractFieldParser.parse(text);

        assertTrue(parsed.contract());
        assertNull(parsed.fullName());
        assertNull(parsed.citizenId());
        assertNull(parsed.employeeCode());
        assertNull(parsed.address());
        assertNull(parsed.phone());
        assertNull(parsed.startDate());
        assertNull(parsed.endDate());
        assertNull(parsed.salary());
        assertNull(parsed.weeklyShiftCount());
    }

    @Test
    void filledCoffeeTemplateMapsLabelsUsedByTheBlankForm() {
        String text = """
                HỢP ĐỒNG LAO ĐỘNG
                Số hợp đồng: HD202610-0009
                Họ và tên Nguyễn Văn An Mã nhân viên NV030
                Ngày sinh 01 / 02 / 2000
                Số CCCD 079203000123
                Số điện thoại 0901234567
                Địa chỉ thường trú 12 Nguyễn Trãi
                Ngày bắt đầu làm việc: 01 / 10 / 2026
                Ngày kết thúc hợp đồng (nếu có): 01 / 10 / 2027
                Mức lương: 25.000 đồng/giờ
                Số ca làm việc theo hợp đồng: 7 ca/tuần.
                """;

        ContractFieldParser.Parsed parsed = ContractFieldParser.parse(text);

        assertEquals("Nguyễn Văn An", parsed.fullName());
        assertEquals(java.time.LocalDate.of(2000, 2, 1), parsed.birthDate());
        assertEquals("079203000123", parsed.citizenId());
        assertEquals("NV030", parsed.employeeCode());
        assertEquals("12 Nguyễn Trãi", parsed.address());
        assertEquals("0901234567", parsed.phone());
        assertEquals(java.time.LocalDate.of(2026, 10, 1), parsed.startDate());
        assertEquals(java.time.LocalDate.of(2027, 10, 1), parsed.endDate());
        assertEquals(new BigDecimal("25000"), parsed.salary());
        assertEquals(7, parsed.weeklyShiftCount());
    }

    @Test
    void employeeAPdfJoinsDigitsSplitByDots() throws Exception {
        byte[] pdf = ContractFieldParserTest.class.getResourceAsStream("/ocr/nhan-vien-a.pdf").readAllBytes();
        ContractDocumentReader.Read read = ContractDocumentReader.read("Mau_Hop_Dong_Lao_Dong_Nhan_Vien_A.pdf", pdf);
        ContractFieldParser.Parsed parsed = ContractFieldParser.parse(read.text());

        assertFalse(read.scanned());
        assertTrue(parsed.contract());
        assertEquals("Nguyễn Văn A", parsed.fullName());
        assertEquals("012345678903", parsed.citizenId());
        assertEquals(java.time.LocalDate.of(2004, 2, 11), parsed.birthDate());
        assertEquals(java.time.LocalDate.of(2026, 10, 30), parsed.startDate());
        assertEquals(java.time.LocalDate.of(2026, 11, 30), parsed.endDate());
        assertEquals(new BigDecimal("25000"), parsed.salary());
        assertEquals(12, parsed.weeklyShiftCount());
        assertEquals("6", parsed.employeeCode());
        assertTrue(parsed.reviewKeys().contains("startDate"));
        assertTrue(parsed.reviewKeys().contains("salary"));
        assertFalse(parsed.reviewKeys().contains("citizenId"));
        assertFalse(parsed.fullName().equals("Mau_Hop_Dong_Lao_Dong_Nhan_Vien_A"));
    }

    @Test
    void passwordProtectedPdfIsRejected() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                    () -> ContractDocumentReader.read("khoa.pdf", out.toByteArray()));
            assertTrue(ex.getMessage().contains("mật khẩu"));
        }
    }
}
