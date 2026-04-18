package com.fleetride.ui;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public final class ClipboardHelper {
    private ClipboardHelper() {}

    public static void copy(String text) {
        if (text == null) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
