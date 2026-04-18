package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Coupon;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryCheckpointRepository;
import com.fleetride.repository.InMemoryCouponRepository;
import com.fleetride.repository.InMemoryCustomerRepository;
import com.fleetride.repository.InMemoryDisputeRepository;
import com.fleetride.repository.InMemoryOrderRepository;
import com.fleetride.repository.InMemorySubsidyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceQuoteResolvingTest {

    private OrderService svc;
    private CouponService coupons;
    private SubsidyService subsidies;
    private InMemoryCustomerRepository customers;
    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    @BeforeEach
    void setup() {
        customers = new InMemoryCustomerRepository();
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        Clock clock = () -> t;
        AuditService audit = new AuditService(new InMemoryAuditRepository(), ids, clock);
        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);
        PricingConfig pricing = new PricingConfig();
        customers.save(new Customer("cid", "A", "p", null));
        svc = new OrderService(new InMemoryOrderRepository(), customers,
                new InMemoryDisputeRepository(), new PricingEngine(pricing),
                new OrderStateMachine(), ids, clock, audit, cp);
        coupons = new CouponService(new InMemoryCouponRepository(), audit);
        subsidies = new SubsidyService(new InMemorySubsidyRepository(), audit);
    }

    private Order newOrder(String couponCode) {
        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(t.plusHours(1), t.plusHours(2));
        return svc.create("cid", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 30, 30, couponCode);
    }

    @Test
    void quoteResolvingAppliesLookedUpCouponAndSubsidy() {
        coupons.createPercent("PCT10", new BigDecimal("0.10"), Money.of("25.00"));
        subsidies.assign("cid", Money.of("50.00"));
        svc.attachLookupServices(coupons, subsidies);
        Order o = newOrder("PCT10");
        var fare = svc.quoteResolving(o);
        assertTrue(fare.couponDiscount().greaterThan(Money.ZERO));
        assertTrue(fare.subsidyApplied().greaterThan(Money.ZERO));
    }

    @Test
    void quoteResolvingWithoutServicesFallsBackToNulls() {
        Order o = newOrder(null);
        var fare = svc.quoteResolving(o);
        assertEquals(Money.ZERO, fare.couponDiscount());
        assertEquals(Money.ZERO, fare.subsidyApplied());
    }

    @Test
    void quoteResolvingUnknownCouponFails() {
        svc.attachLookupServices(coupons, subsidies);
        Order o = newOrder("NOPE");
        assertThrows(OrderService.OrderException.class, () -> svc.quoteResolving(o));
    }

    @Test
    void quoteResolvingSkipsSubsidyWhenAbsent() {
        svc.attachLookupServices(coupons, subsidies);
        Order o = newOrder(null);
        var fare = svc.quoteResolving(o);
        assertEquals(Money.ZERO, fare.subsidyApplied());
    }

    @Test
    void quoteResolvingIgnoresCouponCodeWhenServiceUnattached() {
        // couponService is never attached
        Order o = newOrder("PCT10");
        var fare = svc.quoteResolving(o);
        assertEquals(Money.ZERO, fare.couponDiscount());
    }
}
