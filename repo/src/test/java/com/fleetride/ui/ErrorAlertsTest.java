package com.fleetride.ui;

import javafx.scene.control.Alert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorAlertsTest {

    @AfterEach
    void resetPresenter() {
        ErrorAlerts.setPresenter((type, message) -> { /* swallow */ });
    }

    @Test
    void errorAndInfoRecordLastSeenMessageAndForwardToPresenter() {
        List<String> seen = new ArrayList<>();
        ErrorAlerts.setPresenter((type, message) -> seen.add(type + ":" + message));

        ErrorAlerts.reset();
        ErrorAlerts.error("boom");
        ErrorAlerts.info("ok");

        assertEquals("boom", ErrorAlerts.lastError());
        assertEquals("ok", ErrorAlerts.lastInfo());
        assertEquals(List.of("ERROR:boom", "INFORMATION:ok"), seen);
    }

    @Test
    void resetClearsLastSeenMessages() {
        ErrorAlerts.setPresenter((type, message) -> {});
        ErrorAlerts.error("x");
        ErrorAlerts.info("y");
        ErrorAlerts.reset();
        assertNull(ErrorAlerts.lastError());
        assertNull(ErrorAlerts.lastInfo());
    }

    @Test
    void presenterCanBeSwappedBetweenCalls() {
        List<Alert.AlertType> types = new ArrayList<>();
        ErrorAlerts.setPresenter((type, message) -> types.add(type));
        ErrorAlerts.error("one");
        ErrorAlerts.setPresenter((type, message) -> types.add(Alert.AlertType.NONE));
        ErrorAlerts.info("two");
        assertEquals(List.of(Alert.AlertType.ERROR, Alert.AlertType.NONE), types);
    }
}
