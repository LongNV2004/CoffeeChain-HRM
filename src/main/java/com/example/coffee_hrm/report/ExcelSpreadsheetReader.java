package com.example.coffee_hrm.report;

import com.example.coffee_hrm.common.exception.BusinessException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/** Đọc lại file Excel 2003 XML mà {@link ExcelSpreadsheet} đã ghi. */
public final class ExcelSpreadsheetReader {

    private static final String NS = "urn:schemas-microsoft-com:office:spreadsheet";

    private ExcelSpreadsheetReader() {
    }

    public static List<ExcelSpreadsheet.Sheet> read(byte[] body) {
        if (body == null || body.length == 0) {
            throw new BusinessException("Không đọc được file Excel của lần gửi này.");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element root = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(body))
                    .getDocumentElement();
            NodeList sheets = root.getElementsByTagNameNS(NS, "Worksheet");
            List<ExcelSpreadsheet.Sheet> result = new ArrayList<>();
            for (int i = 0; i < sheets.getLength(); i++) {
                Element sheet = (Element) sheets.item(i);
                result.add(new ExcelSpreadsheet.Sheet(sheet.getAttributeNS(NS, "Name"), rows(sheet)));
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Không đọc được file Excel của lần gửi này.");
        }
    }

    private static List<List<String>> rows(Element sheet) {
        NodeList rowNodes = sheet.getElementsByTagNameNS(NS, "Row");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            NodeList cells = row.getElementsByTagNameNS(NS, "Data");
            List<String> values = new ArrayList<>();
            for (int cell = 0; cell < cells.getLength(); cell++) {
                values.add(cells.item(cell).getTextContent());
            }
            rows.add(values);
        }
        return rows;
    }
}
