package com.example.coffee_hrm.report;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Excel 2003 XML, mở được bằng Excel mà không cần thư viện ngoài. */
public final class ExcelSpreadsheet {

    private ExcelSpreadsheet() {
    }

    public record Sheet(String name, List<List<String>> rows) {
    }

    public static byte[] write(List<Sheet> sheets) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<?mso-application progid=\"Excel.Sheet\"?>\n");
        xml.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ");
        xml.append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n");
        for (Sheet sheet : sheets) {
            xml.append("<Worksheet ss:Name=\"").append(escape(sheet.name())).append("\"><Table>\n");
            for (List<String> row : sheet.rows()) {
                xml.append("<Row>");
                for (String cell : row) {
                    xml.append("<Cell><Data ss:Type=\"String\">")
                            .append(escape(cell == null ? "" : cell))
                            .append("</Data></Cell>");
                }
                xml.append("</Row>\n");
            }
            xml.append("</Table></Worksheet>\n");
        }
        xml.append("</Workbook>");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
