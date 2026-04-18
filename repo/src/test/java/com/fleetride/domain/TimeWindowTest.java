package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeWindowTest {

    @Test
    void rejectsNullStart() {
        LocalDateTime end = LocalDateTime.of(2026, 3, 27, 15, 0);
        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(null, end));
    }

    @Test
    void rejectsNullEnd() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 27, 14, 0);
        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(start, null));
    }

    @Test
    void rejectsEndNotAfterStart() {
        LocalDateTime t = LocalDateTime.of(2026, 3, 27, 14, 0);
        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(t, t));
        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(t, t.minusHours(1)));
    }

    @Test
    void accessorsAndDuration() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 27, 14, 0);
        LocalDateTime end = start.plusHours(1);
        TimeWindow w = new TimeWindow(start, end);
        assertEquals(start, w.start());
        assertEquals(end, w.end());
        assertEquals(Duration.ofHours(1), w.duration());
    }

    @Test
    void contains() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 27, 14, 0);
        LocalDateTime end = start.plusHours(1);
        TimeWindow w = new TimeWindow(start, end);
        assertTrue(w.contains(start));
        assertTrue(w.contains(end));
        assertTrue(w.contains(start.plusMinutes(30)));
        assertFalse(w.contains(start.minusMinutes(1)));
        assertFalse(w.contains(end.plusMinutes(1)));
    }

    @Test
    void equalsAndHashCode() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 27, 14, 0);
        TimeWindow a = new TimeWindow(start, start.plusHours(1));
        TimeWindow b = new TimeWindow(start, start.plusHours(1));
        TimeWindow c = new TimeWindow(start, start.plusHours(2));
        TimeWindow d = new TimeWindow(start.plusMinutes(5), start.plusHours(1));
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
