package com.fleetride.security;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Money;
import com.fleetride.domain.Role;
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
import com.fleetride.repository.InMemoryUserRepository;
import com.fleetride.service.AuditService;
import com.fleetride.service.AuthService;
import com.fleetride.service.CheckpointService;
import com.fleetride.service.Clock;
import com.fleetride.service.EncryptionService;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.OrderService;
import com.fleetride.service.OrderStateMachine;
import com.fleetride.service.PricingEngine;
import com.fleetride.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SecuredTripServiceTest {

    private AuthService auth;
    private InMemoryCustomerRepository customers;
    private SecuredTripService securedTrips;
    private AtomicReference<LocalDateTime> now;

    private final LocalDateTime windowStart = LocalDateTime.of(2026, 4, 18, 14, 0);
    private final TimeWindow tripWindow = new TimeWindow(windowStart, windowStart.plusHours(1));

    @BeforeEach
    void setup() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryTripRepository trips = new InMemoryTripRepository();
        customers = new InMemoryCustomerRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();

        now = new AtomicReference<>(windowStart.minusHours(2));
        Clock clock = () -> now.get();
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        EncryptionService enc = new EncryptionService("k");
        auth = new AuthService(users, enc, ids);
        Authorizer authz = new Authorizer(auth);

        AuditService audit = new AuditService(audits, ids, clock);
        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);
        PricingEngine pricing = new PricingEngine(new com.fleetride.domain.PricingConfig());

        OrderService orderService = new OrderService(orders, customers,
                new InMemoryDisputeRepository(), pricing, new OrderStateMachine(),
                ids, clock, audit, cp);
        TripService tripService = new TripService(trips, orders, orderService, ids, clock, audit);
        securedTrips = new SecuredTripService(tripService, authz, trips, customers, orders);

        auth.bootstrapAdministrator("admin", "pw");
        auth.login("admin", "pw");
        auth.register("dispA", "pw", Role.DISPATCHER);
        auth.register("dispB", "pw", Role.DISPATCHER);
        auth.register("fin", "pw", Role.FINANCE_CLERK);
        auth.logout();

        customers.save(new Customer("cid1", "Alice", "555", null, Money.ZERO, "x-2"));
    }

    @Test
    void dispatcherCreatesOwnedTrip() {
        auth.login("dispA", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        assertNotNull(t.ownerUserId());
        assertEquals(auth.currentUser().orElseThrow().id(), t.ownerUserId());
    }

    @Test
    void financeCannotCreateTrip() {
        auth.login("fin", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null));
    }

    @Test
    void dispatcherCannotManageOtherDispatchersTrip() {
        auth.login("dispA", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedTrips.dispatch(t.id()));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedTrips.cancel(t.id(), "x"));
    }

    @Test
    void dispatcherOnlySeesOwnTrips() {
        auth.login("dispA", "pw");
        securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        auth.logout();
        auth.login("dispB", "pw");
        Trip tb = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        assertEquals(1, securedTrips.list().size());
        assertEquals(tb.id(), securedTrips.list().get(0).id());
    }

    @Test
    void financeCanListAllTripsButCannotMutate() {
        auth.login("dispA", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        auth.logout();
        auth.login("fin", "pw");
        assertEquals(1, securedTrips.list().size());
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedTrips.cancel(t.id(), "x"));
    }

    @Test
    void adminCanManageAnyTrip() {
        auth.login("dispA", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        auth.logout();
        auth.login("admin", "pw");
        // Admin should be allowed to cancel another user's trip.
        securedTrips.cancel(t.id(), "audit");
        assertEquals(TripStatus.CANCELED, securedTrips.find(t.id()).orElseThrow().status());
    }

    @Test
    void addRiderRequiresOwnership() {
        auth.login("dispA", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedTrips.addRiderOrder(t.id(), "cid1",
                        new Address("1", "NY", null, null, null),
                        new Address("2", "NY", null, null, null),
                        1, ServicePriority.NORMAL, 1, 1, null, null, null));
    }

    @Test
    void addRiderRejectsOtherDispatchersCustomer() {
        // dispB owns a trip but tries to use cid1 which belongs to dispA (owner "x-2").
        auth.login("dispB", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        Authorizer.ForbiddenException ex = assertThrows(Authorizer.ForbiddenException.class,
                () -> securedTrips.addRiderOrder(t.id(), "cid1",
                        new Address("1", "NY", null, null, null),
                        new Address("2", "NY", null, null, null),
                        1, ServicePriority.NORMAL, 1, 1, null, null, null));
        assertTrue(ex.getMessage().contains("not visible"),
                "expected visibility error: " + ex.getMessage());
    }

    @Test
    void addRiderRejectsUnknownCustomer() {
        auth.login("dispA", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        assertThrows(com.fleetride.service.TripService.TripException.class,
                () -> securedTrips.addRiderOrder(t.id(), "ghost",
                        new Address("1", "NY", null, null, null),
                        new Address("2", "NY", null, null, null),
                        1, ServicePriority.NORMAL, 1, 1, null, null, null));
    }

    @Test
    void adminCanAddRiderForAnyCustomer() {
        auth.login("admin", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        // cid1 is owned by dispA; admin has canSeeAll.
        com.fleetride.domain.Order o = securedTrips.addRiderOrder(t.id(), "cid1",
                new Address("1", "NY", null, null, null),
                new Address("2", "NY", null, null, null),
                1, ServicePriority.NORMAL, 1, 1, null, null, null);
        assertNotNull(o.id());
    }

    @Test
    void lockedSessionBlocksAll() {
        auth.login("dispA", "pw");
        Trip t = securedTrips.create(VehicleType.STANDARD, 4, tripWindow, null);
        auth.lock();
        assertThrows(Authorizer.ForbiddenException.class, () -> securedTrips.list());
        assertThrows(Authorizer.ForbiddenException.class, () -> securedTrips.dispatch(t.id()));
    }
}
