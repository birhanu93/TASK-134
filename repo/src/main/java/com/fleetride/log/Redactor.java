package com.fleetride.log;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class Redactor {
    public static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "pwd", "token", "payment_token", "card", "cardnumber",
            "phone", "ssn", "secret", "authorization", "apikey");

    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+?\\d{1,2}[\\s-]?)?(?:\\d{3}[\\s-]?){2}\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

    private Redactor() {}

    public static String redactValue(String key, String value) {
        if (value == null) return null;
        if (isSensitiveKey(key)) {
            return "***";
        }
        return scrubPatterns(value);
    }

    public static String scrubPatterns(String value) {
        if (value == null) return null;
        String out = CARD.matcher(value).replaceAll("****-****-****-####");
        out = PHONE.matcher(out).replaceAll("***-***-####");
        out = EMAIL.matcher(out).replaceAll("***@***");
        return out;
    }

    public static Map<String, String> redactFields(Map<String, String> fields) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            out.put(e.getKey(), redactValue(e.getKey(), e.getValue()));
        }
        return out;
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        for (String s : SENSITIVE_KEYS) {
            if (lower.contains(s)) return true;
        }
        return false;
    }
}
