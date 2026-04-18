package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.Trip;
import com.fleetride.domain.TripStatus;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryCheckpointRepository;
import com.fleetride.repository.InMemoryCustomerRepository;
import com.fleetride.repository.InMemoryDisputeRepository;
import com.fleetride.repository.InMemoryOrderRepository;
import com.fleetride.repository.InMemoryTripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TripServiceTest {

    private InMemoryOrderRepository orders;
    private InMemoryTripRepository trips;
    private InMemoryCustomerRepository customers;
    private OrderService orderService;
    private TripService svc;
    private AtomicReference<LocalDateTime> now;
    private final LocalDateTime windowStart = LocalDateTime.of(2026, 4, 18, 14, 0);
    private final TimeWindow tripWindow = new TimeWindow(windowStart, windowStart.plusHours(1));

    @BeforeEach
    void setup() {
        orders = new InMemoryOrderRepository();
        trips = new InMemoryTripRepository();
        customers = new InMemoryCustomerRepository();
        InMemoryDisputeRepository disputes = new InMemoryDisputeRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        now = new AtomicReference<>(windowStart.minusHours(2));
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        Clock clock = () -> now.get();
        AuditService audit = new AuditService(audits, ids, clock);
        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);
        PricingEngine pricing = new PricingEngine(new PricingConfig());

        customers.save(new Customer("cid1", "Alice", "555", null));
        customers.save(new Customer("cid2", "Bob", "555", null));

        orderService = new OrderService(orders, customers, disputes, pricing,
                new OrderStateMachine(), ids, clock, audit, cp);
        svc = new TripService(trips, orders, orderService, ids, clock, audit);
    }

    @Test
    void createTripStartsInPlanning() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, "driver-1");
        assertEquals(TripStatus.PLANNING, t.status());
        assertEquals("driver-1", t.driverPlaceholder());
        assertEquals(tripWindow, t.scheduledWindow());
    }

    @Test
    void addRiderOrderBindsToTripAndSharesWindow() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Order o = svc.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                2, ServicePriority.NORMAL, 3, 10, null, null, null);
        assertEquals(t.id(), o.tripId());
        assertEquals(tripWindow, o.window());
        assertEquals(VehicleType.STANDARD, o.vehicleType());
        assertEquals(1, svc.riderOrders(t.id()).size());
    }

    @Test
    void capacityEnforcedAcrossRiderCounts() {
        Trip t = svc.create(VehicleType.STANDARD, 3, tripWindow, null);
        svc.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                2, ServicePriority.NORMAL, 1, 1, null, null, null);
        // 2 seats used; adding 2 more would exceed capacity 3.
        TripService.TripException ex = assertThrows(TripService.TripException.class,
                () -> svc.addRiderOrder(t.id(), "cid2",
                        new Address("1", "NY", null, null, null),
                        new Address("2", "NY", null, null, null),
                        2, ServicePriority.NORMAL, 1, 1, null, null, null));
        assertTrue(ex.getMessage().contains("capacity"));
    }

    @Test
    void cannotAddRidersAfterDispatch() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        svc.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 1, 1, null, null, null);
        svc.dispatch(t.id());
        assertThrows(TripService.TripException.class,
                () -> svc.addRiderOrder(t.id(), "cid2",
                        new Address("1", "NY", null, null, null),
                        new Address("2", "NY", null, null, null),
                        1, ServicePriority.NORMAL, 1, 1, null, null, null));
    }

    @Test
    void cannotDispatchEmptyTrip() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        assertThrows(TripService.TripException.class, () -> svc.dispatch(t.id()));
    }

    @Test
    void closeRequiresAllRidersTerminal() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Order o = svc.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 1, 1, null, null, null);
        svc.dispatch(t.id());
        assertThrows(TripService.TripException.class, () -> svc.close(t.id()));
        orderService.accept(o.id());
        orderService.start(o.id());
        now.set(windowStart.plusMinutes(30));
        orderService.complete(o.id());
        svc.close(t.id());
        assertEquals(TripStatus.CLOSED, trips.findById(t.id()).orElseThrow().status());
    }

    @Test
    void cancelCascadesToNonTerminalRiders() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Order a = svc.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 1, 1, null, null, null);
        Order b = svc.addRiderOrder(t.id(), "cid2",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 1, 1, null, null, null);
        svc.cancel(t.id(), "driver unavailable");
        assertEquals(TripStatus.CANCELED, trips.findById(t.id()).orElseThrow().status());
        assertEquals(OrderState.CANCELED, orders.findById(a.id()).orElseThrow().state());
        assertEquals(OrderState.CANCELED, orders.findById(b.id()).orElseThrow().state());
    }

    @Test
    void cannotCancelClosedTrip() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Order o = svc.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 1, 1, null, null, null);
        svc.dispatch(t.id());
        orderService.accept(o.id());
        orderService.start(o.id());
        now.set(windowStart.plusMinutes(30));
        orderService.complete(o.id());
        svc.close(t.id());
        assertThrows(TripService.TripException.class, () -> svc.cancel(t.id(), "too late"));
    }

    @Test
    void attachExistingOrderMatchesWindowAndVehicle() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Order standalone = orderService.create("cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, tripWindow, VehicleType.STANDARD, ServicePriority.NORMAL,
                1, 1, null);
        svc.attachExistingOrder(t.id(), standalone.id());
        assertEquals(t.id(), orders.findById(standalone.id()).orElseThrow().tripId());
    }

    @Test
    void attachRejectsVehicleMismatch() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Order standalone = orderService.create("cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, tripWindow, VehicleType.XL, ServicePriority.NORMAL,
                1, 1, null);
        assertThrows(TripService.TripException.class,
                () -> svc.attachExistingOrder(t.id(), standalone.id()));
    }

    @Test
    void attachRejectsWindowMismatch() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        TimeWindow different = new TimeWindow(windowStart.plusHours(3),
                windowStart.plusHours(4));
        Order standalone = orderService.create("cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, different, VehicleType.STANDARD, ServicePriority.NORMAL,
                1, 1, null);
        assertThrows(TripService.TripException.class,
                () -> svc.attachExistingOrder(t.id(), standalone.id()));
    }

    @Test
    void attachRejectsAlreadyOnTrip() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Order o = svc.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 1, 1, null, null, null);
        Trip other = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        assertThrows(TripService.TripException.class,
                () -> svc.attachExistingOrder(other.id(), o.id()));
    }

    @Test
    void totalTripFareSumsRiderTotals() {
        Trip t = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Order a = svc.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 3, 10, null, null, null);
        Order b = svc.addRiderOrder(t.id(), "cid2",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 3, 10, null, null, null);
        orderService.quote(a, null, null);
        orderService.quote(b, null, null);
        Money total = svc.totalTripFare(t.id());
        // each: 3.50 + 5.40 + 3.50 = 12.40; two riders = 24.80
        assertEquals(Money.of("24.80"), total);
    }

    @Test
    void listByStatusFiltersCorrectly() {
        Trip planning = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        Trip forDispatch = svc.create(VehicleType.STANDARD, 4, tripWindow, null);
        svc.addRiderOrder(forDispatch.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 1, 1, null, null, null);
        svc.dispatch(forDispatch.id());
        assertEquals(1, svc.listByStatus(TripStatus.PLANNING).size());
        assertEquals(planning.id(), svc.listByStatus(TripStatus.PLANNING).get(0).id());
        assertEquals(1, svc.listByStatus(TripStatus.DISPATCHED).size());
    }

    @Test
    void unknownTripThrows() {
        assertThrows(TripService.TripException.class, () -> svc.dispatch("nope"));
        assertThrows(TripService.TripException.class, () -> svc.close("nope"));
        assertThrows(TripService.TripException.class, () -> svc.cancel("nope", "x"));
        assertThrows(TripService.TripException.class,
                () -> svc.addRiderOrder("nope", "cid1",
                        new Address("1", "NY", null, null, null),
                        new Address("2", "NY", null, null, null),
                        1, ServicePriority.NORMAL, 1, 1, null, null, null));
    }
}
