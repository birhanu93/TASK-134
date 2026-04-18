package com.fleetride.ui;

import com.fleetride.domain.Address;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderSearchFilterTest {

    private Order mkOrder(String id, String customerId, String pickupLine, String dropLine,
                          String pickupNotes, String dropNotes, String couponCode, OrderState state) {
        Address p = new Address(pickupLine, "Anywhere", null, null, 1);
        Address d = new Address(dropLine, "Elsewhere", null, null, 1);
        TimeWindow w = new TimeWindow(LocalDateTime.of(2026, 1, 1, 14, 0),
                LocalDateTime.of(2026, 1, 1, 15, 0));
        Order o = new Order(id, customerId, p, d, 2, w,
                VehicleType.STANDARD, ServicePriority.NORMAL,
                3.0, 10, couponCode, LocalDateTime.of(2026, 1, 1, 13, 0));
        if (pickupNotes != null) o.setPickupFloorNotes(pickupNotes);
        if (dropNotes != null) o.setDropoffFloorNotes(dropNotes);
        o.setState(state);
        return o;
    }

    @Test
    void nullQueryReturnsCopyOfInput() {
        List<Order> input = List.of(mkOrder("id1", "c1", "1 Main St", "2 Oak St",
                null, null, null, OrderState.PENDING_MATCH));
        List<Order> out = OrderSearchFilter.apply(input, null);
        assertEquals(1, out.size());
        assertThrows(UnsupportedOperationException.class, () -> out.add(null));
    }

    @Test
    void blankQueryReturnsAllRows() {
        List<Order> input = List.of(
                mkOrder("id1", "c1", "1 Main St", "2 Oak St", null, null, null, OrderState.PENDING_MATCH),
                mkOrder("id2", "c2", "3 Pine St", "4 Elm St", null, null, null, OrderState.COMPLETED));
        assertEquals(2, OrderSearchFilter.apply(input, "").size());
        assertEquals(2, OrderSearchFilter.apply(input, "   ").size());
    }

    @Test
    void matchesById() {
        List<Order> input = List.of(
                mkOrder("abc-123", "c1", "x", "y", null, null, null, OrderState.PENDING_MATCH),
                mkOrder("def-456", "c2", "x", "y", null, null, null, OrderState.PENDING_MATCH));
        List<Order> out = OrderSearchFilter.apply(input, "abc");
        assertEquals(1, out.size());
        assertEquals("abc-123", out.get(0).id());
    }

    @Test
    void matchesByCustomerId() {
        List<Order> input = List.of(
                mkOrder("o1", "alice", "x", "y", null, null, null, OrderState.PENDING_MATCH),
                mkOrder("o2", "bob", "x", "y", null, null, null, OrderState.PENDING_MATCH));
        assertEquals("o2", OrderSearchFilter.apply(input, "bob").get(0).id());
    }

    @Test
    void matchesByStateNameCaseInsensitive() {
        List<Order> input = List.of(
                mkOrder("o1", "c1", "x", "y", null, null, null, OrderState.PENDING_MATCH),
                mkOrder("o2", "c1", "x", "y", null, null, null, OrderState.COMPLETED));
        List<Order> out = OrderSearchFilter.apply(input, "completed");
        assertEquals(1, out.size());
        assertEquals("o2", out.get(0).id());
    }

    @Test
    void matchesByPickupAddress() {
        List<Order> input = List.of(
                mkOrder("o1", "c1", "42 Baker Street", "y", null, null, null, OrderState.PENDING_MATCH),
                mkOrder("o2", "c1", "7 Downing Street", "y", null, null, null, OrderState.PENDING_MATCH));
        assertEquals("o1", OrderSearchFilter.apply(input, "BAKER").get(0).id());
    }

    @Test
    void matchesByDropoffAddress() {
        List<Order> input = List.of(
                mkOrder("o1", "c1", "x", "Airport Terminal 1", null, null, null, OrderState.PENDING_MATCH));
        assertEquals(1, OrderSearchFilter.apply(input, "airport").size());
    }

    @Test
    void matchesByFloorNotes() {
        List<Order> input = List.of(
                mkOrder("o1", "c1", "x", "y", "loading dock", null, null, OrderState.PENDING_MATCH),
                mkOrder("o2", "c1", "x", "y", null, "ring bell", null, OrderState.PENDING_MATCH));
        assertEquals("o1", OrderSearchFilter.apply(input, "loading").get(0).id());
        assertEquals("o2", OrderSearchFilter.apply(input, "bell").get(0).id());
    }

    @Test
    void matchesByCouponCode() {
        List<Order> input = List.of(
                mkOrder("o1", "c1", "x", "y", null, null, "WELCOME10", OrderState.PENDING_MATCH),
                mkOrder("o2", "c1", "x", "y", null, null, "SPRING", OrderState.PENDING_MATCH));
        assertEquals("o1", OrderSearchFilter.apply(input, "welcome").get(0).id());
    }

    @Test
    void nullFieldsDoNotMatch() {
        List<Order> input = List.of(
                mkOrder("o1", "c1", "x", "y", null, null, null, OrderState.PENDING_MATCH));
        assertTrue(OrderSearchFilter.apply(input, "zzzzz-not-there").isEmpty());
    }

    @Test
    void matchesHelperDirectlyForNonMatch() {
        Order o = mkOrder("o1", "c1", "1 Main", "2 Oak", null, null, null, OrderState.PENDING_MATCH);
        assertFalse(OrderSearchFilter.matches(o, "zzz"));
        assertTrue(OrderSearchFilter.matches(o, "main"));
    }
}
