package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.Payment;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryCheckpointRepository;
import com.fleetride.repository.InMemoryCustomerRepository;
import com.fleetride.repository.InMemoryDisputeRepository;
import com.fleetride.repository.InMemoryOrderRepository;
import com.fleetride.repository.InMemoryPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationServiceTest {

    private final LocalDateTime windowStart = LocalDateTime.of(2026, 3, 27, 14, 0);

    private static final class Fixture {
        final InMemoryOrderRepository orders = new InMemoryOrderRepository();
        final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
        final InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
        final InMemoryDisputeRepository disputes = new InMemoryDisputeRepository();
        final InMemoryAuditRepository audits = new InMemoryAuditRepository();
        final AtomicReference<LocalDateTime> now = new AtomicReference<>();
        final OrderService orderService;
        final PaymentService paymentService;
        final ReconciliationService recon;

        Fixture(IdGenerator ids, LocalDateTime initial) {
            this.now.set(initial);
            PricingEngine pricing = new PricingEngine(new PricingConfig());
            OrderStateMachine fsm = new OrderStateMachine();
            Clock clock = () -> now.get();
            AuditService audit = new AuditService(audits, ids, clock);
            CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);
            customers.save(new Customer("cid", "A", "p", null));
            orderService = new OrderService(orders, customers, disputes, pricing, fsm, ids, clock, audit, cp);
            paymentService = new PaymentService(payments, orders, ids, clock, audit);
            recon = new ReconciliationService(orders, payments, paymentService);
        }

        Order createOrder(LocalDateTime windowStart) {
            Address a = new Address("1 Main", "NY", "NY", "10001", null);
            TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
            Order o = orderService.create("cid", a, a, 1, w, VehicleType.STANDARD,
                    ServicePriority.NORMAL, 3, 10, null);
            orderService.quote(o, null, null);
            return o;
        }
    }

    @Test
    void exportCsvWithCleanIds(@TempDir Path dir) throws Exception {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        Fixture f = new Fixture(() -> "clean" + n.incrementAndGet(), windowStart.minusHours(2));
        Order o = f.createOrder(windowStart);
        f.paymentService.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("3.00"));
        Path file = f.recon.exportCsv(dir.resolve("out/recon.csv"));
        List<String> lines = Files.readAllLines(file);
        assertEquals("order_id,state,fare_total,cancel_fee,total_paid,balance", lines.get(0));
        assertTrue(lines.get(1).startsWith("clean"));
    }

    @Test
    void exportCsvWithOrderMissingFare(@TempDir Path dir) throws Exception {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        Fixture f = new Fixture(() -> "c" + n.incrementAndGet(), windowStart.minusHours(2));
        Address a = new Address("1 Main", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        f.orderService.create("cid", a, a, 1, w, VehicleType.STANDARD, ServicePriority.NORMAL, 1, 1, null);
        Path file = f.recon.exportCsv(dir.resolve("r.csv"));
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.get(1).contains(",0.00,"));
    }

    @Test
    void exportCsvEscapesComma(@TempDir Path dir) throws Exception {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        Fixture f = new Fixture(() -> "x," + n.incrementAndGet(), windowStart.minusHours(2));
        Order o = f.createOrder(windowStart);
        f.paymentService.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("3.00"));
        Path file = f.recon.exportCsv(dir.resolve("r.csv"));
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.get(1).startsWith("\"x,"));
    }

    @Test
    void exportPaymentsCsvEscapesQuote(@TempDir Path dir) throws Exception {
        // payment IDs with quotes exercise the OR's right side
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        Fixture f = new Fixture(() -> {
            int i = n.incrementAndGet();
            return (i == 3) ? "id\"with\"quote" : "clean" + i;
        }, windowStart.minusHours(2));
        Order o = f.createOrder(windowStart);
        f.paymentService.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("3.00"));
        Path file = f.recon.exportPaymentsCsv(dir.resolve("p.csv"));
        List<String> lines = Files.readAllLines(file);
        assertEquals("payment_id,order_id,kind,tender,amount,recorded_at", lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.contains("\"\"")));
    }

    @Test
    void exportPaymentsCsvPlain(@TempDir Path dir) throws Exception {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        Fixture f = new Fixture(() -> "ok" + n.incrementAndGet(), windowStart.minusHours(2));
        Order o = f.createOrder(windowStart);
        f.paymentService.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("2.00"));
        Path file = f.recon.exportPaymentsCsv(dir.resolve("p.csv"));
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.get(1).contains("DEPOSIT"));
    }
}
