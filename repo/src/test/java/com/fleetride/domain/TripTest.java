package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TripTest {

    private static TimeWindow window() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 27, 14, 0);
        return new TimeWindow(start, start.plusHours(1));
    }

    @Test
    void validation() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 27, 13, 0);
        TimeWindow w = window();
        assertThrows(IllegalArgumentException.class,
                () -> new Trip(null, VehicleType.STANDARD, 3, w, now));
        assertThrows(IllegalArgumentException.class,
                () -> new Trip(" ", VehicleType.STANDARD, 3, w, now));
        assertThrows(IllegalArgumentException.class,
                () -> new Trip("t", null, 3, w, now));
        assertThrows(IllegalArgumentException.class,
                () -> new Trip("t", VehicleType.STANDARD, 0, w, now));
        assertThrows(IllegalArgumentException.class,
                () -> new Trip("t", VehicleType.STANDARD, 7, w, now));
        assertThrows(IllegalArgumentException.class,
                () -> new Trip("t", VehicleType.STANDARD, 3, null, now));
        assertThrows(IllegalArgumentException.class,
                () -> new Trip("t", VehicleType.STANDARD, 3, w, null));
    }

    @Test
    void defaultStatusIsPlanning() {
        Trip t = new Trip("t1", VehicleType.STANDARD, 4, window(),
                LocalDateTime.of(2026, 3, 27, 13, 0));
        assertEquals(TripStatus.PLANNING, t.status());
        assertEquals(4, t.capacity());
        assertEquals(VehicleType.STANDARD, t.vehicleType());
    }

    @Test
    void accessorsAndMutators() {
        Trip t = new Trip("t1", VehicleType.XL, 6, window(),
                LocalDateTime.of(2026, 3, 27, 13, 0));
        t.setDriverPlaceholder("driver-7");
        t.setStatus(TripStatus.DISPATCHED);
        t.setOwnerUserId("u-1");
        LocalDateTime tt = LocalDateTime.of(2026, 3, 27, 14, 0);
        t.setDispatchedAt(tt);
        t.setClosedAt(tt.plusHours(1));
        t.setCanceledAt(tt.plusHours(2));
        assertEquals("driver-7", t.driverPlaceholder());
        assertEquals(TripStatus.DISPATCHED, t.status());
        assertEquals("u-1", t.ownerUserId());
        assertEquals(tt, t.dispatchedAt());
        assertEquals(tt.plusHours(1), t.closedAt());
        assertEquals(tt.plusHours(2), t.canceledAt());
    }

    @Test
    void equalsAndHashUseIdOnly() {
        Trip a = new Trip("t1", VehicleType.STANDARD, 3, window(),
                LocalDateTime.of(2026, 3, 27, 13, 0));
        Trip b = new Trip("t1", VehicleType.XL, 5, window(),
                LocalDateTime.of(2026, 3, 27, 12, 0));
        Trip c = new Trip("t2", VehicleType.STANDARD, 3, window(),
                LocalDateTime.of(2026, 3, 27, 13, 0));
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
