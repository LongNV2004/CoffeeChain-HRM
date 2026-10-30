package com.example.coffee_hrm.service.ocr;

import java.awt.image.BufferedImage;

/**
 * Phân biệt nét viết tay với dòng kẻ và một dòng chữ in. Không kết luận có chữ ký khi vùng trống hoặc chỉ là dòng kẻ.
 */
final class SignatureInk {

    static final String PRESENT = "CO_CHU_KY";
    static final String ABSENT = "KHONG_CO_CHU_KY";
    static final String UNKNOWN = "CHUA_XAC_DINH";

    private SignatureInk() {
    }

    static String classify(BufferedImage image) {
        if (image == null || image.getWidth() < 8 || image.getHeight() < 8) {
            return UNKNOWN;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] dark = new boolean[height][width];
        int[] rowCount = new int[height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isDark(image.getRGB(x, y))) {
                    dark[y][x] = true;
                    rowCount[y]++;
                }
            }
        }
        for (int y = 0; y < height; y++) {
            if (rowCount[y] > width * 0.55) {
                clearRow(dark, rowCount, y);
                if (y > 0) {
                    clearRow(dark, rowCount, y - 1);
                }
                if (y + 1 < height) {
                    clearRow(dark, rowCount, y + 1);
                }
            }
        }
        int ink = 0;
        int bandHeight = 0;
        int bandMinWidth = Integer.MAX_VALUE;
        int bandMaxWidth = 0;
        boolean signature = false;
        for (int y = 0; y <= height; y++) {
            int count = y == height ? 0 : rowCount[y];
            if (count > 1) {
                bandHeight++;
                bandMinWidth = Math.min(bandMinWidth, count);
                bandMaxWidth = Math.max(bandMaxWidth, count);
            } else if (bandHeight > 0) {
                if (handwriting(bandHeight, bandMinWidth, bandMaxWidth)) {
                    signature = true;
                    ink += bandHeight * bandMaxWidth;
                }
                bandHeight = 0;
                bandMinWidth = Integer.MAX_VALUE;
                bandMaxWidth = 0;
            }
        }
        if (signature && ink > 50) {
            return PRESENT;
        }
        if (signature) {
            return UNKNOWN;
        }
        return ABSENT;
    }

    private static boolean handwriting(int height, int minWidth, int maxWidth) {
        if (height < 28 || minWidth <= 0) {
            return false;
        }
        return maxWidth >= minWidth * 1.8;
    }

    private static void clearRow(boolean[][] dark, int[] rowCount, int y) {
        for (int x = 0; x < dark[y].length; x++) {
            dark[y][x] = false;
        }
        rowCount[y] = 0;
    }

    private static boolean isDark(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return (red + green + blue) / 3 < 145;
    }
}
