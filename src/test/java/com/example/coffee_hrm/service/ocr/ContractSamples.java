package com.example.coffee_hrm.service.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ContractSamples {

    private ContractSamples() {
    }

    public static byte[] signedContract(String fullName, String citizenId) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.setLeading(16);
                stream.newLineAtOffset(48, 720);
                stream.showText("HOP DONG LAO DONG");
                stream.newLine();
                stream.showText("Ho va ten: " + fullName);
                stream.newLine();
                stream.showText("CCCD: " + citizenId);
                stream.newLine();
                stream.showText("Ngay sinh: 01/02/2000");
                stream.newLine();
                stream.showText("Dia chi: 12 Nguyen Trai");
                stream.newLine();
                stream.showText("Dien thoai: 0901234567");
                stream.newLine();
                stream.showText("Ma nhan vien: NV030");
                stream.newLine();
                stream.showText("Tu ngay 01/10/2026 den ngay 01/10/2027");
                stream.newLine();
                stream.showText("Luong 25000 dong/gio");
                stream.newLine();
                stream.showText("So ca 7 ca/tuan");
                stream.newLine();
                stream.showText("So hop dong HD202610-0001 khong dung lam CCCD");
                stream.endText();
                label(stream, font, 48, 230, "DAI DIEN BEN A");
                scribble(stream, 60, 170);
                label(stream, font, 320, 230, "NGUOI LAO DONG");
                scribble(stream, 340, 170);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] signedContractAcrossPages(String fullName, String citizenId) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage first = new PDPage();
            document.addPage(first);
            try (PDPageContentStream stream = new PDPageContentStream(document, first)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.setLeading(16);
                stream.newLineAtOffset(48, 720);
                stream.showText("HOP DONG LAO DONG");
                stream.newLine();
                stream.showText("Ho va ten: " + fullName);
                stream.newLine();
                stream.showText("CCCD: " + citizenId);
                stream.newLine();
                stream.showText("Tu ngay 01/10/2026 den ngay 01/10/2027");
                stream.newLine();
                stream.showText("Luong 25000 dong/gio");
                stream.newLine();
                stream.showText("So ca 7 ca/tuan");
                stream.endText();
            }
            PDPage second = new PDPage();
            document.addPage(second);
            try (PDPageContentStream stream = new PDPageContentStream(document, second)) {
                label(stream, font, 48, 230, "DAI DIEN BEN A");
                scribble(stream, 60, 170);
                label(stream, font, 320, 230, "NGUOI LAO DONG");
                scribble(stream, 340, 170);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] scannedImagePdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(LosslessFactory.createFromImage(document, image), 40, 400, 120, 60);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] contractWithoutSignatures(String fullName, String citizenId) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.setLeading(16);
                stream.newLineAtOffset(48, 720);
                stream.showText("HOP DONG LAO DONG");
                stream.newLine();
                stream.showText("Ho va ten: " + fullName);
                stream.newLine();
                stream.showText("CCCD: " + citizenId);
                stream.endText();
                label(stream, font, 48, 230, "DAI DIEN BEN A");
                label(stream, font, 320, 230, "NGUOI LAO DONG");
                stream.setLineWidth(1f);
                stream.moveTo(48, 200);
                stream.lineTo(220, 200);
                stream.stroke();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static void label(PDPageContentStream stream, PDType1Font font, float x, float y, String text) throws IOException {
        stream.beginText();
        stream.setFont(font, 12);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private static void scribble(PDPageContentStream stream, float x, float y) throws IOException {
        stream.setLineWidth(2.4f);
        stream.moveTo(x, y);
        stream.curveTo(x + 25, y - 36, x + 55, y + 28, x + 90, y - 8);
        stream.curveTo(x + 110, y - 48, x + 40, y - 60, x + 15, y - 24);
        stream.stroke();
    }
}
