package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DisputeTest {

    @Test
    void validation() {
        LocalDateTime t = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> new Dispute(null, "o", "r", t));
        assertThrows(IllegalArgumentException.class, () -> new Dispute(" ", "o", "r", t));
        assertThrows(IllegalArgumentException.class, () -> new Dispute("id", null, "r", t));
        assertThrows(IllegalArgumentException.class, () -> new Dispute("id", "", "r", t));
        assertThrows(IllegalArgumentException.class, () -> new Dispute("id", "o", null, t));
        assertThrows(IllegalArgumentException.class, () -> new Dispute("id", "o", " ", t));
        assertThrows(IllegalArgumentException.class, () -> new Dispute("id", "o", "r", null));
    }

    @Test
    void initialState() {
        Dispute d = new Dispute("id", "o", "r", LocalDateTime.now());
        assertEquals(Dispute.Status.OPEN, d.status());
        assertNull(d.resolution());
        assertNull(d.resolvedAt());
    }

    @Test
    void resolveAndReject() {
        LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);
        Dispute d = new Dispute("id", "o", "r", t);
        d.resolve("accepted", t.plusDays(1));
        assertEquals(Dispute.Status.RESOLVED, d.status());
        assertEquals("accepted", d.resolution());
        assertEquals(t.plusDays(1), d.resolvedAt());

        Dispute d2 = new Dispute("id2", "o", "r", t);
        d2.reject("denied", t.plusDays(2));
        assertEquals(Dispute.Status.REJECTED, d2.status());
        assertEquals("denied", d2.resolution());
    }

    @Test
    void accessors() {
        LocalDateTime t = LocalDateTime.now();
        Dispute d = new Dispute("id", "o", "r", t);
        assertEquals("id", d.id());
        assertEquals("o", d.orderId());
        assertEquals("r", d.reason());
        assertEquals(t, d.openedAt());
    }

    @Test
    void equalsAndHash() {
        LocalDateTime t = LocalDateTime.now();
        Dispute a = new Dispute("id", "o", "r", t);
        Dispute b = new Dispute("id", "other", "x", t);
        Dispute c = new Dispute("id2", "o", "r", t);
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
