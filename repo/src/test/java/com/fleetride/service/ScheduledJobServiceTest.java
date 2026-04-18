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
import com.fleetride.repository.InMemoryJobRunRepository;
import com.fleetride.repository.InMemoryOrderRepository;
import com.fleetride.repository.InMemoryPaymentRepository;
import com.fleetride.repository.JobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledJobServiceTest {

    private InMemoryCustomerRepository customers;
    private InMemoryOrderRepository orders;
    private ScheduledJobService scheduler;
    private OrderService orderService;
    private PaymentService paymentService;
    private AtomicReference<LocalDateTime> now;

    private final LocalDateTime windowStart = LocalDateTime.of(2026, 3, 27, 14, 0);

    @BeforeEach
    void setup() {
        customers = new InMemoryCustomerRepository();
        orders = new InMemoryOrderRepository();
        InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
        InMemoryDisputeRepository disputes = new InMemoryDisputeRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        now = new AtomicReference<>(windowStart.minusHours(3));

        PricingConfig pricingConfig = new PricingConfig();
        PricingEngine pricing = new PricingEngine(pricingConfig);
        OrderStateMachine fsm = new OrderStateMachine();
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        Clock clock = () -> now.get();
        AuditService audit = new AuditService(audits, ids, clock);

        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);

        customers.save(new Customer("c1", "Alice", "p", null, Money.of("10.00")));
        customers.save(new Customer("c2", "Bob", "p", null));
        orderService = new OrderService(orders, customers, disputes, pricing, fsm, ids, clock, audit, cp);
        paymentService = new PaymentService(payments, orders, ids, clock, audit);
        scheduler = new ScheduledJobService(orderService, paymentService, customers, audit, clock,
                ids, new InMemoryJobRunRepository(), orders, pricingConfig);
    }

    private Order createPending() {
        Address a = new Address("1 Main", "NY", "NY", "10001", null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        Order o = orderService.create("c1", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 3, 10, null);
        orderService.quote(o, null, null);
        return o;
    }

    @Test
    void timeoutJobCancelsStale() {
        Order o = createPending();
        now.set(o.createdAt().plusMinutes(20));
        ScheduledJobService.JobReport r = scheduler.hourlyTimeoutEnforcement();
        assertEquals(1, r.processed());
        assertEquals("hourly-timeout", r.name());
        assertFalse(r.log().isEmpty());
    }

    @Test
    void nightlyOverdueFindsUnpaidCompleted() {
        Order o = createPending();
        orderService.accept(o.id());
        orderService.start(o.id());
        now.set(windowStart.plusMinutes(30));
        orderService.complete(o.id());
        ScheduledJobService.JobReport r = scheduler.nightlyOverdueSweep();
        assertEquals(1, r.processed());
    }

    @Test
    void nightlyOverdueWithZeroFeeConfigSkipsAssessment() {
        Order o = createPending();
        orderService.accept(o.id());
        orderService.start(o.id());
        now.set(windowStart.plusMinutes(30));
        orderService.complete(o.id());
        PricingConfig cfg = new PricingConfig();
        cfg.setOverdueFeePerSweep(Money.ZERO);
        java.util.concurrent.atomic.AtomicInteger idx = new java.util.concurrent.atomic.AtomicInteger();
        ScheduledJobService zero = new ScheduledJobService(
                orderService, paymentService, customers,
                new AuditService(new InMemoryAuditRepository(),
                        () -> "z-" + idx.incrementAndGet(), () -> windowStart),
                () -> windowStart, () -> "z-" + idx.incrementAndGet(),
                new InMemoryJobRunRepository(), orders, cfg);
        zero.nightlyOverdueSweep();
        assertEquals(Money.ZERO, orders.findById(o.id()).orElseThrow().overdueFee());
    }

    @Test
    void nightlyOverduePersistsFees() {
        Order o = createPending();
        orderService.accept(o.id());
        orderService.start(o.id());
        now.set(windowStart.plusMinutes(30));
        orderService.complete(o.id());
        scheduler.nightlyOverdueSweep();
        assertEquals(Money.of("5.00"),
                orders.findById(o.id()).orElseThrow().overdueFee());
        // second sweep keeps assessing additional fees
        scheduler.nightlyOverdueSweep();
        assertEquals(Money.of("10.00"),
                orders.findById(o.id()).orElseThrow().overdueFee());
    }

    @Test
    void nightlyOverdueWithoutOrderRepoDoesNotCrash() {
        InMemoryJobRunRepository runs = new InMemoryJobRunRepository();
        java.util.concurrent.atomic.AtomicInteger idx = new java.util.concurrent.atomic.AtomicInteger();
        ScheduledJobService bare = new ScheduledJobService(
                orderService, paymentService, customers,
                new AuditService(new InMemoryAuditRepository(),
                        () -> "r-" + idx.incrementAndGet(), () -> windowStart),
                () -> windowStart,
                () -> "r-" + idx.incrementAndGet(), runs);
        Order o = createPending();
        orderService.accept(o.id());
        orderService.start(o.id());
        now.set(windowStart.plusMinutes(30));
        orderService.complete(o.id());
        ScheduledJobService.JobReport r = bare.nightlyOverdueSweep();
        assertEquals(1, r.processed());
    }

    @Test
    void nightlyOverdueIgnoresPaid() {
        Order o = createPending();
        orderService.accept(o.id());
        orderService.start(o.id());
        now.set(windowStart.plusMinutes(30));
        orderService.complete(o.id());
        paymentService.recordFinal(o.id(), Payment.Tender.CASH, Money.of("12.40"));
        ScheduledJobService.JobReport r = scheduler.nightlyOverdueSweep();
        assertEquals(0, r.processed());
    }

    @Test
    void quotaReclamation() {
        ScheduledJobService.JobReport r = scheduler.nightlyQuotaReclamation();
        assertEquals(1, r.processed());
        assertEquals(Money.ZERO, customers.findById("c1").orElseThrow().subsidyUsedThisMonth());
    }

    @Test
    void quotaReclamationResetsMonthlyRideUsage() {
        // Simulate ride usage accrued during the month.
        com.fleetride.domain.Customer c1 = customers.findById("c1").orElseThrow();
        c1.setMonthlyRideQuota(10);
        c1.recordRide();
        c1.recordRide();
        c1.recordRide();
        customers.save(c1);

        ScheduledJobService.JobReport r = scheduler.nightlyQuotaReclamation();
        com.fleetride.domain.Customer after = customers.findById("c1").orElseThrow();
        assertEquals(0, after.monthlyRidesUsed());
        assertEquals(10, after.monthlyRideQuota(), "quota cap is not reset, only usage");
        assertTrue(r.processed() >= 1);
    }

    @Test
    void quotaReclamationTouchesOnlyDirtyCustomers() {
        // c2 has no subsidy usage and no rides — should be skipped.
        com.fleetride.domain.Customer c2 = customers.findById("c2").orElseThrow();
        assertEquals(0, c2.monthlyRidesUsed());
        assertEquals(Money.ZERO, c2.subsidyUsedThisMonth());

        ScheduledJobService.JobReport r = scheduler.nightlyQuotaReclamation();
        // c1 has subsidy=10.00 from setup; c2 is clean. Only c1 processed.
        assertEquals(1, r.processed());
    }

    @Test
    void runAllProducesAllReports() {
        var reports = scheduler.runAll();
        assertEquals(3, reports.size());
        assertTrue(reports.containsKey("timeout"));
        assertTrue(reports.containsKey("overdue"));
        assertTrue(reports.containsKey("quota"));
    }

    @Test
    void jobReportAccessors() {
        ScheduledJobService.JobReport r = new ScheduledJobService.JobReport(
                "test", 2, java.util.List.of("a", "b"));
        assertEquals("test", r.name());
        assertEquals(2, r.processed());
        assertEquals(2, r.log().size());
    }

    @Test
    void nowDelegatesToClock() {
        assertEquals(now.get(), scheduler.now());
    }

    @Test
    void jobRunsArePersistedOnSuccess() {
        createPending();
        scheduler.hourlyTimeoutEnforcement();
        var recent = scheduler.recentRuns(5);
        assertEquals(1, recent.size());
        assertEquals(JobRunRepository.Status.SUCCESS, recent.get(0).status());
        assertEquals("hourly-timeout", recent.get(0).jobName());
        assertNotNull(recent.get(0).finishedAt());
    }

    @Test
    void failedJobIsRecorded() {
        InMemoryJobRunRepository runs = new InMemoryJobRunRepository();
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        ScheduledJobService broken = new ScheduledJobService(
                null, null, null, null,
                () -> windowStart,
                () -> "r-" + n.incrementAndGet(), runs);
        assertThrows(RuntimeException.class, broken::hourlyTimeoutEnforcement);
        assertEquals(1, runs.findByStatus(JobRunRepository.Status.FAILED).size());
    }

    @Test
    void startAndStopSchedulerLifecycle() throws InterruptedException {
        scheduler.startScheduler(60, 60, 60);
        assertThrows(IllegalStateException.class, () -> scheduler.startScheduler(60, 60, 60));
        scheduler.stopScheduler();
        // second stop is no-op
        scheduler.stopScheduler();
    }

    @Test
    void jobRunRecordAccessors() {
        JobRunRepository.Record r = new JobRunRepository.Record(
                "id", "job", windowStart, windowStart.plusMinutes(1),
                3, JobRunRepository.Status.SUCCESS, "msg");
        assertEquals("id", r.id());
        assertEquals("job", r.jobName());
        assertEquals(windowStart, r.startedAt());
        assertEquals(windowStart.plusMinutes(1), r.finishedAt());
        assertEquals(3, r.processed());
        assertEquals(JobRunRepository.Status.SUCCESS, r.status());
        assertEquals("msg", r.message());
        assertEquals(3, JobRunRepository.Status.values().length);
    }
}
