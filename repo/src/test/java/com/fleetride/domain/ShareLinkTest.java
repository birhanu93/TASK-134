package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ShareLinkTest {

    @Test
    void validation() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 27, 10, 0);
        LocalDateTime exp = now.plusHours(1);
        assertThrows(IllegalArgumentException.class, () -> new ShareLink(null, "r", "m", now, exp));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink(" ", "r", "m", now, exp));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink("t", null, "m", now, exp));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink("t", " ", "m", now, exp));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink("t", "r", null, now, exp));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink("t", "r", " ", now, exp));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink("t", "r", "m", null, exp));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink("t", "r", "m", now, null));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink("t", "r", "m", now, now));
        assertThrows(IllegalArgumentException.class, () -> new ShareLink("t", "r", "m", now, now.minusMinutes(1)));
    }

    @Test
    void equalsAndHashCode() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 27, 10, 0);
        ShareLink a = new ShareLink("t", "r", "m", now, now.plusHours(1));
        ShareLink b = new ShareLink("t", "other", "m2", now, now.plusHours(1));
        ShareLink c = new ShareLink("t2", "r", "m", now, now.plusHours(1));
        assertEquals(a, a);
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void accessorsAndExpiry() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 27, 10, 0);
        ShareLink l = new ShareLink("t", "r", "m", now, now.plusHours(24));
        assertEquals("t", l.token());
        assertEquals("r", l.resourceId());
        assertEquals("m", l.machineId());
        assertEquals(now, l.createdAt());
        assertEquals(now.plusHours(24), l.expiresAt());
        assertFalse(l.isExpired(now));
        assertFalse(l.isExpired(now.plusHours(23)));
        assertTrue(l.isExpired(now.plusHours(24)));
        assertTrue(l.isExpired(now.plusHours(25)));
    }
}
