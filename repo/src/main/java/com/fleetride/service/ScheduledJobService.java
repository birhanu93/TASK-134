package com.fleetride.service;

import com.fleetride.domain.Customer;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.PricingConfig;
import com.fleetride.repository.CustomerRepository;
import com.fleetride.repository.JobRunRepository;
import com.fleetride.repository.JobRunRepository.Record;
import com.fleetride.repository.JobRunRepository.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ScheduledJobService {
    public static final class JobReport {
        private final String name;
        private final int processed;
        private final List<String> log;

        public JobReport(String name, int processed, List<String> log) {
            this.name = name;
            this.processed = processed;
            this.log = log;
        }

        public String name() { return name; }
        public int processed() { return processed; }
        public List<String> log() { return log; }
    }

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final CustomerRepository customers;
    private final AuditService audit;
    private final Clock clock;
    private final IdGenerator ids;
    private final JobRunRepository runs;
    private final com.fleetride.repository.OrderRepository orders;
    private final com.fleetride.domain.PricingConfig pricing;
    private ScheduledExecutorService scheduler;
    private final List<ScheduledFuture<?>> scheduled = new ArrayList<>();

    public ScheduledJobService(OrderService orderService, PaymentService paymentService,
                               CustomerRepository customers, AuditService audit, Clock clock,
                               IdGenerator ids, JobRunRepository runs) {
        this(orderService, paymentService, customers, audit, clock, ids, runs, null, null);
    }

    public ScheduledJobService(OrderService orderService, PaymentService paymentService,
                               CustomerRepository customers, AuditService audit, Clock clock,
                               IdGenerator ids, JobRunRepository runs,
                               com.fleetride.repository.OrderRepository orders,
                               com.fleetride.domain.PricingConfig pricing) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.customers = customers;
        this.audit = audit;
        this.clock = clock;
        this.ids = ids;
        this.runs = runs;
        this.orders = orders;
        this.pricing = pricing;
    }

    public JobReport hourlyTimeoutEnforcement() {
        return runInstrumented("hourly-timeout", () -> {
            int canceled = orderService.autoCancelStale();
            List<String> log = new ArrayList<>();
            log.add("canceled=" + canceled);
            audit.record("scheduler", "JOB_TIMEOUT", null, "canceled=" + canceled);
            return new JobReport("hourly-timeout", canceled, log);
        });
    }

    public JobReport nightlyOverdueSweep() {
        return runInstrumented("nightly-overdue", () -> {
            int count = 0;
            Money feesAssessed = Money.ZERO;
            List<String> log = new ArrayList<>();
            Money perSweep = pricing == null ? Money.ZERO : pricing.overdueFeePerSweep();
            for (Order o : orderService.listByState(OrderState.COMPLETED)) {
                if (paymentService.isOverdue(o)) {
                    if (orders != null && !perSweep.isZero()) {
                        o.addOverdueFee(perSweep);
                        orders.save(o);
                        feesAssessed = feesAssessed.add(perSweep);
                        log.add(o.id() + " overdue + " + perSweep + " (balance "
                                + paymentService.balanceFor(o) + ")");
                    } else {
                        log.add(o.id() + " overdue " + paymentService.balanceFor(o));
                    }
                    count++;
                }
            }
            audit.record("scheduler", "JOB_OVERDUE_SWEEP", null,
                    "overdue=" + count + " fees=" + feesAssessed);
            return new JobReport("nightly-overdue", count, log);
        });
    }

    public JobReport nightlyQuotaReclamation() {
        return runInstrumented("nightly-quota", () -> {
            int customersTouched = 0;
            int subsidyResets = 0;
            int rideQuotaResets = 0;
            List<String> log = new ArrayList<>();
            for (Customer c : customers.findAll()) {
                boolean subsidyDirty = !c.subsidyUsedThisMonth().isZero();
                boolean ridesDirty = c.monthlyRidesUsed() > 0;
                if (!subsidyDirty && !ridesDirty) continue;

                StringBuilder detail = new StringBuilder(c.id()).append(":");
                if (subsidyDirty) {
                    Money prior = c.subsidyUsedThisMonth();
                    c.resetSubsidyUsage();
                    detail.append(" subsidy ").append(prior).append("->0");
                    subsidyResets++;
                }
                if (ridesDirty) {
                    int prior = c.monthlyRidesUsed();
                    c.resetMonthlyQuota();
                    detail.append(" rides ").append(prior).append("/")
                            .append(c.monthlyRideQuota()).append("->0");
                    rideQuotaResets++;
                }
                customers.save(c);
                log.add(detail.toString());
                customersTouched++;
            }
            audit.record("scheduler", "JOB_QUOTA_RESET", null,
                    "customers=" + customersTouched
                            + " subsidy=" + subsidyResets
                            + " rideQuota=" + rideQuotaResets);
            return new JobReport("nightly-quota", customersTouched, log);
        });
    }

    public Map<String, JobReport> runAll() {
        return Map.of(
                "timeout", hourlyTimeoutEnforcement(),
                "overdue", nightlyOverdueSweep(),
                "quota", nightlyQuotaReclamation()
        );
    }

    public synchronized void startScheduler(long hourlyMinutes, long nightlyMinutes, long quotaMinutes) {
        if (scheduler != null) {
            throw new IllegalStateException("scheduler already started");
        }
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "fleetride-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduled.add(scheduler.scheduleAtFixedRate(
                this::hourlyTimeoutEnforcement, hourlyMinutes, hourlyMinutes, TimeUnit.MINUTES));
        scheduled.add(scheduler.scheduleAtFixedRate(
                this::nightlyOverdueSweep, nightlyMinutes, nightlyMinutes, TimeUnit.MINUTES));
        scheduled.add(scheduler.scheduleAtFixedRate(
                this::nightlyQuotaReclamation, quotaMinutes, quotaMinutes, TimeUnit.MINUTES));
    }

    public synchronized void stopScheduler() {
        if (scheduler == null) return;
        scheduler.shutdownNow();
        scheduled.clear();
        scheduler = null;
    }

    public List<Record> recentRuns(int limit) {
        return runs.findRecent(limit);
    }

    public java.time.LocalDateTime now() { return clock.now(); }

    private JobReport runInstrumented(String jobName, Supplier<JobReport> work) {
        String runId = ids.next();
        java.time.LocalDateTime startedAt = clock.now();
        runs.upsert(new Record(runId, jobName, startedAt, null, null, Status.RUNNING, null));
        try {
            JobReport report = work.get();
            runs.upsert(new Record(runId, jobName, startedAt, clock.now(),
                    report.processed(), Status.SUCCESS, String.join(";", report.log())));
            return report;
        } catch (RuntimeException e) {
            runs.upsert(new Record(runId, jobName, startedAt, clock.now(), null,
                    Status.FAILED, e.getMessage()));
            throw e;
        }
    }
}
