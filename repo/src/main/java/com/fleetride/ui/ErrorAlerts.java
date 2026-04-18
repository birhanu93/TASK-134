package com.fleetride.ui;

import javafx.scene.control.Alert;

import java.util.function.BiConsumer;

/**
 * Thin seam between the UI windows and {@link Alert}. Production uses the
 * default presenter which opens a modal dialog; tests install a no-op
 * presenter via {@link #setPresenter} so headless runs never block on
 * {@code showAndWait()}.
 */
public final class ErrorAlerts {
    private static volatile BiConsumer<Alert.AlertType, String> presenter =
            (type, message) -> new Alert(type, message).showAndWait();

    private static volatile String lastError;
    private static volatile String lastInfo;

    private ErrorAlerts() {}

    public static void error(String message) {
        lastError = message;
        presenter.accept(Alert.AlertType.ERROR, message);
    }

    public static void info(String message) {
        lastInfo = message;
        presenter.accept(Alert.AlertType.INFORMATION, message);
    }

    public static String lastError() { return lastError; }
    public static String lastInfo() { return lastInfo; }

    public static void reset() {
        lastError = null;
        lastInfo = null;
    }

    public static void setPresenter(BiConsumer<Alert.AlertType, String> p) {
        presenter = p;
    }
}
