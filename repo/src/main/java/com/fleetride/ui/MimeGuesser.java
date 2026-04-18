package com.fleetride.ui;

import java.util.Locale;

public final class MimeGuesser {
    private MimeGuesser() {}

    public static String guess(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }
}
