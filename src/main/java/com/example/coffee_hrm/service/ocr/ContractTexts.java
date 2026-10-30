package com.example.coffee_hrm.service.ocr;

import java.text.Normalizer;
import java.util.Locale;

final class ContractTexts {

    private ContractTexts() {
    }

    static String fold(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }

    static boolean sameName(String left, String right) {
        String a = fold(left).replaceAll("[^a-z0-9]", "");
        String b = fold(right).replaceAll("[^a-z0-9]", "");
        return !a.isEmpty() && a.equals(b);
    }
}
