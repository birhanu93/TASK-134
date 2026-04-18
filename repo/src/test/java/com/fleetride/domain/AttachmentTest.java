package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentTest {

    @Test
    void validation() {
        LocalDateTime t = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment(null, "o", "f", "p", "m", 1, "s", t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment(" ", "o", "f", "p", "m", 1, "s", t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment("id", null, "f", "p", "m", 1, "s", t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment("id", "", "f", "p", "m", 1, "s", t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment("id", "o", null, "p", "m", 1, "s", t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment("id", "o", " ", "p", "m", 1, "s", t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment("id", "o", "f", null, "m", 1, "s", t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment("id", "o", "f", "p", null, 1, "s", t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment("id", "o", "f", "p", "m", 1, null, t));
        assertThrows(IllegalArgumentException.class,
                () -> new Attachment("id", "o", "f", "p", "m", 1, "s", null));
    }

    @Test
    void accessors() {
        LocalDateTime t = LocalDateTime.now();
        Attachment a = new Attachment("id", "o", "f.pdf", "/tmp/x", "application/pdf",
                1024, "sha", t);
        assertEquals("id", a.id());
        assertEquals("o", a.orderId());
        assertEquals("f.pdf", a.filename());
        assertEquals("/tmp/x", a.storedPath());
        assertEquals("application/pdf", a.mimeType());
        assertEquals(1024, a.sizeBytes());
        assertEquals("sha", a.sha256());
        assertEquals(t, a.uploadedAt());
    }

    @Test
    void equalsAndHash() {
        LocalDateTime t = LocalDateTime.now();
        Attachment a = new Attachment("id", "o", "f", "p", "m", 1, "s", t);
        Attachment b = new Attachment("id", "other", "other", "other", "other", 999, "s2", t);
        Attachment c = new Attachment("id2", "o", "f", "p", "m", 1, "s", t);
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
