package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Dispute;
import com.fleetride.domain.Fare;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryCheckpointRepository;
import com.fleetride.repository.InMemoryCustomerRepository;
import com.fleetride.repository.InMemoryDisputeRepository;
import com.fleetride.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private InMemoryOrderRepository orders;
    private InMemoryCustomerRepository customers;
    private InMemoryDisputeRepository disputes;
    private InMemoryAuditRepository audits;
    private OrderService svc;
    private AtomicReference<LocalDateTime> now;

    private final LocalDateTime windowStart = LocalDateTime.of(2026, 3, 27, 14, 0);

    @BeforeEach
    void setup() {
        orders = new InMemoryOrderRepository();
        customers = new InMemoryCustomerRepository();
        disputes = new InMemoryDisputeRepository();
        audits = new InMemoryAuditRepository();
        now = new AtomicReference<>(windowStart.minusHours(2));

        PricingEngine pricing = new PricingEngine(new PricingConfig());
        OrderStateMachine fsm = new OrderStateMachine();
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        Clock clock = () -> now.get();
        AuditService audit = new AuditService(audits, ids, clock);
        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);

        customers.save(new Customer("cid", "Alice", "p", null));

        svc = new OrderService(orders, customers, disputes, pricing, fsm, ids, clock, audit, cp);
    }

    private Order createOrder() {
        Address a = new Address("1 Main", "NY", "NY", "10001", null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        return svc.create("cid", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 3.0, 10, null);
    }

    @Test
    void createUnknownCustomer() {
        Address a = new Address("1 Main", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        assertThrows(OrderService.OrderException.class,
                () -> svc.create("nope", a, a, 1, w, VehicleType.STANDARD,
                        ServicePriority.NORMAL, 1, 1, null));
    }

    @Test
    void createAndQuote() {
        Order o = createOrder();
        assertEquals(OrderState.PENDING_MATCH, o.state());
        Fare f = svc.quote(o, null, null);
        assertNotNull(f);
        assertEquals(f, o.fare());
    }

    @Test
    void quoteUnknownCustomer() {
        Order o = createOrder();
        customers.delete("cid");
        assertThrows(OrderService.OrderException.class, () -> svc.quote(o, null, null));
    }

    @Test
    void acceptStartComplete() {
        Order o = createOrder();
        svc.accept(o.id());
        svc.start(o.id());
        svc.complete(o.id());
        assertEquals(OrderState.COMPLETED, svc.find(o.id()).orElseThrow().state());
    }

    @Test
    void acceptUnknown() {
        assertThrows(OrderService.OrderException.class, () -> svc.accept("nope"));
    }

    @Test
    void startUnknown() {
        assertThrows(OrderService.OrderException.class, () -> svc.start("nope"));
    }

    @Test
    void completeUnknown() {
        assertThrows(OrderService.OrderException.class, () -> svc.complete("nope"));
    }

    @Test
    void cancelUnknown() {
        assertThrows(OrderService.OrderException.class, () -> svc.cancel("nope"));
    }

    @Test
    void openDisputeUnknown() {
        assertThrows(OrderService.OrderException.class, () -> svc.openDispute("nope", "r"));
    }

    @Test
    void cancelPendingWithinLateWindowAppliesFee() {
        Order o = createOrder();
        now.set(windowStart.minusMinutes(5));
        svc.cancel(o.id());
        assertEquals(OrderState.CANCELED, svc.find(o.id()).orElseThrow().state());
        assertEquals(Money.of("5.00"), svc.find(o.id()).orElseThrow().cancellationFee());
    }

    @Test
    void cancelPendingOutsideLateWindowNoFee() {
        Order o = createOrder();
        now.set(windowStart.minusHours(1));
        svc.cancel(o.id());
        assertEquals(OrderState.CANCELED, svc.find(o.id()).orElseThrow().state());
        assertEquals(Money.ZERO, svc.find(o.id()).orElseThrow().cancellationFee());
    }

    @Test
    void cancelAfterAcceptWithinLateWindow() {
        Order o = createOrder();
        svc.accept(o.id());
        now.set(windowStart.minusMinutes(5));
        svc.cancel(o.id());
        assertEquals(Money.of("5.00"), svc.find(o.id()).orElseThrow().cancellationFee());
    }

    @Test
    void cancelAfterAcceptOutsideWindow() {
        Order o = createOrder();
        svc.accept(o.id());
        now.set(windowStart.minusHours(3));
        svc.cancel(o.id());
        assertEquals(Money.ZERO, svc.find(o.id()).orElseThrow().cancellationFee());
    }

    @Test
    void completePersistsSubsidyUsage() {
        Order o = createOrder();
        com.fleetride.domain.Subsidy subsidy = new com.fleetride.domain.Subsidy("cid", Money.of("50.00"));
        svc.quote(o, null, subsidy);
        svc.accept(o.id());
        svc.start(o.id());
        now.set(windowStart.plusMinutes(30));
        svc.complete(o.id());
        assertTrue(customers.findById("cid").orElseThrow()
                .subsidyUsedThisMonth().greaterThan(Money.ZERO));
    }

    @Test
    void openDisputeAfterCompletion() {
        Order o = createOrder();
        svc.accept(o.id());
        svc.start(o.id());
        now.set(windowStart.plusMinutes(30));
        svc.complete(o.id());
        now.set(windowStart.plusDays(1));
        Dispute d = svc.openDispute(o.id(), "wrong fare");
        assertEquals(OrderState.IN_DISPUTE, svc.find(o.id()).orElseThrow().state());
        assertEquals("wrong fare", d.reason());
    }

    @Test
    void autoCancelStale() {
        Order o = createOrder();
        now.set(o.createdAt().plusMinutes(20));
        int canceled = svc.autoCancelStale();
        assertEquals(1, canceled);
        assertEquals(OrderState.CANCELED, svc.find(o.id()).orElseThrow().state());
    }

    @Test
    void autoCancelLeavesFreshAlone() {
        Order o = createOrder();
        now.set(o.createdAt().plusMinutes(5));
        assertEquals(0, svc.autoCancelStale());
        assertEquals(OrderState.PENDING_MATCH, svc.find(o.id()).orElseThrow().state());
    }

    @Test
    void createIncrementsMonthlyRideQuotaUsage() {
        int before = customers.findById("cid").orElseThrow().monthlyRidesUsed();
        createOrder();
        createOrder();
        assertEquals(before + 2, customers.findById("cid").orElseThrow().monthlyRidesUsed());
    }

    @Test
    void createRejectedWhenCustomerOverQuota() {
        Customer c = customers.findById("cid").orElseThrow();
        c.setMonthlyRideQuota(1);
        c.recordRide();
        customers.save(c);

        OrderService.OrderException ex = assertThrows(OrderService.OrderException.class,
                this::createOrder);
        assertTrue(ex.getMessage().contains("quota"),
                "error should mention the quota: " + ex.getMessage());
    }

    @Test
    void createAllowedWhenQuotaZeroMeansUnlimited() {
        Customer c = customers.findById("cid").orElseThrow();
        c.setMonthlyRideQuota(0);
        customers.save(c);
        // Should not throw even after many rides, because zero means unlimited.
        for (int i = 0; i < 3; i++) createOrder();
        assertEquals(3, customers.findById("cid").orElseThrow().monthlyRidesUsed());
    }

    @Test
    void listAndListByState() {
        Order o = createOrder();
        assertEquals(1, svc.list().size());
        assertEquals(1, svc.listByState(OrderState.PENDING_MATCH).size());
        svc.accept(o.id());
        assertEquals(0, svc.listByState(OrderState.PENDING_MATCH).size());
        assertEquals(1, svc.listByState(OrderState.ACCEPTED).size());
    }
}
