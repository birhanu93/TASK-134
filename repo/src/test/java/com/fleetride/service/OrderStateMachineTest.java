package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    private final LocalDateTime t0 = LocalDateTime.of(2026, 3, 27, 13, 0);

    private Order newOrder() {
        Address a = new Address("1 Main", "NY", "NY", "10001", null);
        TimeWindow w = new TimeWindow(t0.plusHours(1), t0.plusHours(2));
        return new Order("id", "cid", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 1, 1, null, t0);
    }

    @Test
    void happyPath() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        fsm.accept(o, t0.plusMinutes(1));
        assertEquals(OrderState.ACCEPTED, o.state());
        assertEquals(t0.plusMinutes(1), o.acceptedAt());
        fsm.start(o, t0.plusMinutes(2));
        assertEquals(OrderState.IN_PROGRESS, o.state());
        fsm.complete(o, t0.plusMinutes(30));
        assertEquals(OrderState.COMPLETED, o.state());
    }

    @Test
    void acceptFromWrongState() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        o.setState(OrderState.ACCEPTED);
        assertThrows(OrderStateMachine.InvalidTransitionException.class, () -> fsm.accept(o, t0));
    }

    @Test
    void startFromWrongState() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        assertThrows(OrderStateMachine.InvalidTransitionException.class, () -> fsm.start(o, t0));
    }

    @Test
    void completeFromWrongState() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        assertThrows(OrderStateMachine.InvalidTransitionException.class, () -> fsm.complete(o, t0));
    }

    @Test
    void cancelFromPending() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        fsm.cancel(o, t0);
        assertEquals(OrderState.CANCELED, o.state());
    }

    @Test
    void cancelFromAccepted() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        fsm.accept(o, t0);
        fsm.cancel(o, t0);
        assertEquals(OrderState.CANCELED, o.state());
    }

    @Test
    void cancelFromCompletedThrows() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        o.setState(OrderState.COMPLETED);
        assertThrows(OrderStateMachine.InvalidTransitionException.class, () -> fsm.cancel(o, t0));
    }

    @Test
    void cancelFromCanceledThrows() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        o.setState(OrderState.CANCELED);
        assertThrows(OrderStateMachine.InvalidTransitionException.class, () -> fsm.cancel(o, t0));
    }

    @Test
    void cancelFromDisputeThrows() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        o.setState(OrderState.IN_DISPUTE);
        assertThrows(OrderStateMachine.InvalidTransitionException.class, () -> fsm.cancel(o, t0));
    }

    @Test
    void openDisputeWithinWindow() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        o.setState(OrderState.COMPLETED);
        o.setCompletedAt(t0);
        fsm.openDispute(o, t0.plusDays(5), 7);
        assertEquals(OrderState.IN_DISPUTE, o.state());
        assertEquals(t0.plusDays(5), o.disputedAt());
    }

    @Test
    void openDisputeOutsideWindowThrows() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        o.setState(OrderState.COMPLETED);
        o.setCompletedAt(t0);
        assertThrows(OrderStateMachine.InvalidTransitionException.class,
                () -> fsm.openDispute(o, t0.plusDays(8), 7));
    }

    @Test
    void openDisputeFromNonCompletedThrows() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        assertThrows(OrderStateMachine.InvalidTransitionException.class,
                () -> fsm.openDispute(o, t0, 7));
    }

    @Test
    void shouldAutoCancelBranches() {
        OrderStateMachine fsm = new OrderStateMachine();
        Order o = newOrder();
        assertFalse(fsm.shouldAutoCancel(o, t0.plusMinutes(5), 15));
        assertTrue(fsm.shouldAutoCancel(o, t0.plusMinutes(15), 15));
        o.setState(OrderState.ACCEPTED);
        assertFalse(fsm.shouldAutoCancel(o, t0.plusHours(1), 15));
    }
}
