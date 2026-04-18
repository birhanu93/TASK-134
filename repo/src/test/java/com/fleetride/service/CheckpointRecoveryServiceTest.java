package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.CheckpointRepository;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryCheckpointRepository;
import com.fleetride.repository.InMemoryCustomerRepository;
import com.fleetride.repository.InMemoryDisputeRepository;
import com.fleetride.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointRecoveryServiceTest {

    private InMemoryOrderRepository orders;
    private InMemoryCheckpointRepository checkpointRepo;
    private CheckpointService checkpoints;
    private CheckpointRecoveryService recovery;
    private OrderService orderService;
    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    @BeforeEach
    void setup() {
        orders = new InMemoryOrderRepository();
        checkpointRepo = new InMemoryCheckpointRepository();
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        Clock clock = () -> t;
        AuditService audit = new AuditService(new InMemoryAuditRepository(), ids, clock);
        checkpoints = new CheckpointService(checkpointRepo, clock);
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
        customers.save(new Customer("cid", "A", "p", null));
        orderService = new OrderService(orders, customers,
                new InMemoryDisputeRepository(), new PricingEngine(new PricingConfig()),
                new OrderStateMachine(), ids, clock, audit, checkpoints);
        recovery = new CheckpointRecoveryService(checkpointRepo, checkpoints, orders, audit);
    }

    private Order newOrder() {
        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(t.plusHours(1), t.plusHours(2));
        return orderService.create("cid", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 1, 1, null);
    }

    @Test
    void recoverCommitsWhenStateAlreadyMatches() {
        Order o = newOrder();
        // Order is PENDING_MATCH; create a PENDING checkpoint mimicking an accepted
        // transition that crashed just before commit, but whose side effects landed.
        o.setState(OrderState.ACCEPTED);
        orders.save(o);
        checkpointRepo.upsert(new CheckpointRepository.Record(
                "order:" + o.id() + ":ACCEPT", "accept",
                CheckpointRepository.Status.PENDING, t));
        var report = recovery.recover();
        assertEquals(1, report.inspected());
        assertEquals(1, report.committed());
        assertEquals(0, report.cleared());
        assertTrue(checkpoints.isCompleted("order:" + o.id() + ":ACCEPT"));
        assertEquals(1, report.notes().size());
    }

    @Test
    void recoverClearsPendingWithoutMatchingState() {
        Order o = newOrder();
        // Order still in PENDING_MATCH, pending ACCEPT checkpoint → clear it
        checkpointRepo.upsert(new CheckpointRepository.Record(
                "order:" + o.id() + ":ACCEPT", "accept",
                CheckpointRepository.Status.PENDING, t));
        var report = recovery.recover();
        assertEquals(1, report.cleared());
        assertFalse(checkpoints.isCompleted("order:" + o.id() + ":ACCEPT"));
    }

    @Test
    void recoverCommitsDisputeIfStateMatches() {
        Order o = newOrder();
        o.setState(OrderState.IN_DISPUTE);
        orders.save(o);
        String opId = "order:" + o.id() + ":DISPUTE:someDisputeId";
        checkpointRepo.upsert(new CheckpointRepository.Record(opId, "d",
                CheckpointRepository.Status.PENDING, t));
        var report = recovery.recover();
        assertEquals(1, report.committed());
        assertTrue(checkpoints.isCompleted(opId));
    }

    @Test
    void recoverClearsDisputeIfStateMismatch() {
        Order o = newOrder();
        String opId = "order:" + o.id() + ":DISPUTE:x";
        checkpointRepo.upsert(new CheckpointRepository.Record(opId, "d",
                CheckpointRepository.Status.PENDING, t));
        var report = recovery.recover();
        assertEquals(1, report.cleared());
    }

    @Test
    void recoverClearsUnknownOrderIds() {
        String opId = "order:no-such-id:COMPLETE";
        checkpointRepo.upsert(new CheckpointRepository.Record(opId, "c",
                CheckpointRepository.Status.PENDING, t));
        var report = recovery.recover();
        assertEquals(1, report.cleared());
    }

    @Test
    void recoverClearsMalformedOperationIds() {
        checkpointRepo.upsert(new CheckpointRepository.Record("not-a-known-shape", "x",
                CheckpointRepository.Status.PENDING, t));
        checkpointRepo.upsert(new CheckpointRepository.Record("other:abc:ACCEPT", "x",
                CheckpointRepository.Status.PENDING, t));
        checkpointRepo.upsert(new CheckpointRepository.Record("order:abc:UNKNOWN", "x",
                CheckpointRepository.Status.PENDING, t));
        var report = recovery.recover();
        assertEquals(3, report.cleared());
    }

    @Test
    void recoverClearsDisputeForUnknownOrder() {
        String opId = "order:no-such-order:DISPUTE:x";
        checkpointRepo.upsert(new CheckpointRepository.Record(opId, "d",
                CheckpointRepository.Status.PENDING, t));
        var report = recovery.recover();
        assertEquals(1, report.cleared());
    }

    @Test
    void reportAccessors() {
        var r = new CheckpointRecoveryService.RecoveryReport(3, 1, 2, java.util.List.of("a", "b"));
        assertEquals(3, r.inspected());
        assertEquals(1, r.committed());
        assertEquals(2, r.cleared());
        assertEquals(2, r.notes().size());
    }
}
