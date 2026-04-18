package com.fleetride.integration;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.sqlite.Database;
import com.fleetride.repository.sqlite.SqliteAuditRepository;
import com.fleetride.repository.sqlite.SqliteCheckpointRepository;
import com.fleetride.repository.sqlite.SqliteCustomerRepository;
import com.fleetride.repository.sqlite.SqliteDisputeRepository;
import com.fleetride.repository.sqlite.SqliteOrderRepository;
import com.fleetride.service.AuditService;
import com.fleetride.service.CheckpointService;
import com.fleetride.service.Clock;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.OrderService;
import com.fleetride.service.OrderStateMachine;
import com.fleetride.service.PricingEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RestartIdempotencyTest {

    private OrderService buildService(Database db, LocalDateTime now, String idPrefix) {
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> idPrefix + "-" + n.incrementAndGet();
        Clock clock = () -> now;
        AuditService audit = new AuditService(new SqliteAuditRepository(db), ids, clock);
        CheckpointService cp = new CheckpointService(new SqliteCheckpointRepository(db), clock);
        SqliteCustomerRepository customers = new SqliteCustomerRepository(db);
        if (customers.findById("cid").isEmpty()) {
            customers.save(new Customer("cid", "A", "555", null));
        }
        return new OrderService(new SqliteOrderRepository(db), customers,
                new SqliteDisputeRepository(db),
                new PricingEngine(new PricingConfig()),
                new OrderStateMachine(), ids, clock, audit, cp);
    }

    @Test
    void stateTransitionIsIdempotentAcrossRestart(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("fleet.db");
        LocalDateTime now = LocalDateTime.of(2026, 3, 27, 13, 0);

        String orderId;
        try (Database db = new Database(url)) {
            OrderService svc = buildService(db, now, "a");
            Address a = new Address("1", "NY", null, null, null);
            TimeWindow w = new TimeWindow(now.plusHours(1), now.plusHours(2));
            Order o = svc.create("cid", a, a, 1, w, VehicleType.STANDARD,
                    ServicePriority.NORMAL, 1, 1, null);
            svc.accept(o.id());
            orderId = o.id();
        }

        // simulate process restart - reopen DB, reuse same order id
        try (Database db = new Database(url)) {
            OrderService svc = buildService(db, now.plusMinutes(1), "b");
            // a repeat of ACCEPT should be rejected because checkpoint says done
            assertThrows(CheckpointService.CheckpointException.class, () -> svc.accept(orderId));
            assertEquals(OrderState.ACCEPTED, svc.find(orderId).orElseThrow().state());
        }
    }

    @Test
    void autoCancelIdempotentAcrossRestart(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("fleet.db");
        LocalDateTime create = LocalDateTime.of(2026, 3, 27, 12, 0);

        String orderId;
        try (Database db = new Database(url)) {
            OrderService svc = buildService(db, create, "c");
            Address a = new Address("1", "NY", null, null, null);
            TimeWindow w = new TimeWindow(create.plusHours(1), create.plusHours(2));
            Order o = svc.create("cid", a, a, 1, w, VehicleType.STANDARD,
                    ServicePriority.NORMAL, 1, 1, null);
            orderId = o.id();
        }

        // past the auto-cancel window, run sweep
        try (Database db = new Database(url)) {
            OrderService svc = buildService(db, create.plusMinutes(20), "d");
            assertEquals(1, svc.autoCancelStale());
            assertEquals(OrderState.CANCELED, svc.find(orderId).orElseThrow().state());
        }

        // second run (same conceptual restart) should not re-cancel
        try (Database db = new Database(url)) {
            OrderService svc = buildService(db, create.plusMinutes(30), "e");
            assertEquals(0, svc.autoCancelStale());
        }
    }

    @Test
    void pendingCheckpointPersistsAcrossRestart(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("fleet.db");
        LocalDateTime now = LocalDateTime.of(2026, 3, 27, 10, 0);
        try (Database db = new Database(url)) {
            CheckpointService cp = new CheckpointService(new SqliteCheckpointRepository(db), () -> now);
            cp.begin("op-x", "started work");
        }
        try (Database db = new Database(url)) {
            CheckpointService cp = new CheckpointService(new SqliteCheckpointRepository(db), () -> now);
            assertEquals(1, cp.allPending().size());
            assertEquals("started work", cp.pending("op-x").orElseThrow());
        }
    }
}
