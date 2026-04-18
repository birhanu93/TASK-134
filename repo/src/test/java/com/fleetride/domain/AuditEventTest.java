package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AuditEventTest {

    @Test
    void validation() {
        LocalDateTime t = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent(null, "a", "A", "t", "d", t));
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent(" ", "a", "A", "t", "d", t));
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent("id", null, "A", "t", "d", t));
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent("id", "a", null, "t", "d", t));
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent("id", "a", " ", "t", "d", t));
        assertThrows(IllegalArgumentException.class, () -> new AuditEvent("id", "a", "A", "t", "d", null));
    }

    @Test
    void accessors() {
        LocalDateTime t = LocalDateTime.now();
        AuditEvent e = new AuditEvent("id", "a", "A", "t", "d", t);
        assertEquals("id", e.id());
        assertEquals("a", e.actor());
        assertEquals("A", e.action());
        assertEquals("t", e.target());
        assertEquals("d", e.details());
        assertEquals(t, e.at());
    }
}
