package com.fleetride.service;

public final class MaskingUtil {
    private MaskingUtil() {}

    public static String maskLast4(String s) {
        if (s == null) return null;
        if (s.length() <= 4) {
            return "*".repeat(s.length());
        }
        return "*".repeat(s.length() - 4) + s.substring(s.length() - 4);
    }

    public static String maskPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) return "*".repeat(digits.length());
        return "***-***-" + digits.substring(digits.length() - 4);
    }
}
