package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static Address addr(Integer floor) {
        return new Address("1 Main", "NY", "NY", "10001", floor);
    }

    private static TimeWindow window() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 27, 14, 0);
        return new TimeWindow(start, start.plusHours(1));
    }

    private static Order valid() {
        return new Order("oid", "cid", addr(2), addr(null), 2, window(),
                VehicleType.STANDARD, ServicePriority.NORMAL, 3.0, 12,
                null, LocalDateTime.of(2026, 3, 27, 13, 0));
    }

    @Test
    void validation() {
        LocalDateTime now = LocalDateTime.now();
        Address a = addr(null);
        TimeWindow w = window();

        assertThrows(IllegalArgumentException.class, () -> new Order(null, "c", a, a, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("", "c", a, a, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", null, a, a, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", " ", a, a, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", null, a, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, null, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, a, 0, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, a, 7, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, a, 1, null,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, a, 1, w,
                null, ServicePriority.NORMAL, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, a, 1, w,
                VehicleType.STANDARD, null, 1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, a, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, -1, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, a, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, -1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new Order("o", "c", a, a, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, null));
    }

    @Test
    void accessorsAndMutators() {
        Order o = valid();
        assertEquals("oid", o.id());
        assertEquals("cid", o.customerId());
        assertEquals(2, o.riderCount());
        assertEquals(VehicleType.STANDARD, o.vehicleType());
        assertEquals(ServicePriority.NORMAL, o.priority());
        assertEquals(3.0, o.miles());
        assertEquals(12, o.durationMinutes());
        assertNull(o.couponCode());
        assertNotNull(o.pickup());
        assertNotNull(o.dropoff());
        assertNotNull(o.window());
        assertNotNull(o.createdAt());
        assertEquals(OrderState.PENDING_MATCH, o.state());
        assertEquals(Money.ZERO, o.cancellationFee());

        LocalDateTime t = LocalDateTime.of(2026, 3, 27, 15, 0);
        o.setState(OrderState.ACCEPTED);
        o.setAcceptedAt(t);
        o.setStartedAt(t);
        o.setCompletedAt(t);
        o.setCanceledAt(t);
        o.setDisputedAt(t);
        Fare fare = new Fare(Money.of("3.50"), Money.of("5.40"), Money.of("3.50"),
                Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
                Money.of("12.40"), Money.of("12.40"), Money.of("2.48"));
        o.setFare(fare);
        o.setCancellationFee(Money.of("5.00"));

        assertEquals(OrderState.ACCEPTED, o.state());
        assertEquals(t, o.acceptedAt());
        assertEquals(t, o.startedAt());
        assertEquals(t, o.completedAt());
        assertEquals(t, o.canceledAt());
        assertEquals(t, o.disputedAt());
        assertEquals(fare, o.fare());
        assertEquals(Money.of("5.00"), o.cancellationFee());
    }

    @Test
    void equalsAndHash() {
        Order a = valid();
        Order b = valid();
        Order c = new Order("other", "cid", addr(null), addr(null), 1, window(),
                VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null, LocalDateTime.now());
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
