package com.example.coffee_hrm.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelSpreadsheetReaderTest {

    @Test
    void readsSheetsWrittenByTheReportExcel() {
        byte[] body = ExcelSpreadsheet.write(List.of(
                new ExcelSpreadsheet.Sheet("Tong hop", List.of(
                        List.of("Cửa hàng", "Cầu Giấy"),
                        List.of("Tên", "Lê & Thu")))));

        List<ExcelSpreadsheet.Sheet> sheets = ExcelSpreadsheetReader.read(body);

        assertEquals(1, sheets.size());
        assertEquals("Tong hop", sheets.getFirst().name());
        assertEquals("Cầu Giấy", sheets.getFirst().rows().getFirst().get(1));
        assertEquals("Lê & Thu", sheets.getFirst().rows().get(1).get(1));
    }
}
