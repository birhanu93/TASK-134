package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Coupon;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Dispute;
import com.fleetride.domain.Fare;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.Subsidy;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.CustomerRepository;
import com.fleetride.repository.DisputeRepository;
import com.fleetride.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class OrderService {
    public static final class OrderException extends RuntimeException {
        public OrderException(String msg) { super(msg); }
    }

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final DisputeRepository disputes;
    private final PricingEngine pricing;
    private final OrderStateMachine fsm;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditService audit;
    private final CheckpointService checkpoints;
    private CouponService couponService;
    private SubsidyService subsidyService;

    public OrderService(OrderRepository orders, CustomerRepository customers, DisputeRepository disputes,
                        PricingEngine pricing, OrderStateMachine fsm, IdGenerator ids, Clock clock,
                        AuditService audit, CheckpointService checkpoints) {
        this.orders = orders;
        this.customers = customers;
        this.disputes = disputes;
        this.pricing = pricing;
        this.fsm = fsm;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
        this.checkpoints = checkpoints;
    }

    public Order create(String customerId, Address pickup, Address dropoff, int riderCount,
                        TimeWindow window, VehicleType vehicleType, ServicePriority priority,
                        double miles, int durationMinutes, String couponCode) {
        return create(customerId, pickup, dropoff, riderCount, window, vehicleType, priority,
                miles, durationMinutes, couponCode, null, null);
    }

    public Order create(String customerId, Address pickup, Address dropoff, int riderCount,
                        TimeWindow window, VehicleType vehicleType, ServicePriority priority,
                        double miles, int durationMinutes, String couponCode,
                        String pickupFloorNotes, String dropoffFloorNotes) {
        Customer c = customers.findById(customerId)
                .orElseThrow(() -> new OrderException("unknown customer"));
        if (c.isOverQuota()) {
            throw new OrderException("customer " + c.id() + " over monthly ride quota ("
                    + c.monthlyRidesUsed() + "/" + c.monthlyRideQuota()
                    + "); wait for nightly quota reclamation or raise the cap");
        }
        Order order = new Order(ids.next(), c.id(), pickup, dropoff, riderCount, window,
                vehicleType, priority, miles, durationMinutes, couponCode, clock.now());
        order.setPickupFloorNotes(pickupFloorNotes);
        order.setDropoffFloorNotes(dropoffFloorNotes);
        orders.save(order);
        c.recordRide();
        customers.save(c);
        audit.record("system", "ORDER_CREATE", order.id(),
                "rides=" + c.monthlyRidesUsed() + "/" + c.monthlyRideQuota());
        return order;
    }

    public Fare quote(Order order, Coupon coupon, Subsidy subsidy) {
        Customer c = customers.findById(order.customerId())
                .orElseThrow(() -> new OrderException("unknown customer"));
        Fare fare = pricing.quote(order, c, coupon, subsidy);
        order.setFare(fare);
        orders.save(order);
        return fare;
    }

    /**
     * Quote resolving coupon/subsidy from persistent repositories. The coupon code
     * recorded on the order is looked up via {@link CouponService}; the customer's
     * configured subsidy (if any) is looked up via {@link SubsidyService}.
     */
    public Fare quoteResolving(Order order) {
        Coupon c = order.couponCode() == null ? null
                : couponService == null ? null
                : couponService.find(order.couponCode()).orElseThrow(
                        () -> new OrderException("unknown coupon " + order.couponCode()));
        Subsidy s = subsidyService == null ? null
                : subsidyService.find(order.customerId()).orElse(null);
        return quote(order, c, s);
    }

    public void attachLookupServices(CouponService coupons, SubsidyService subsidies) {
        this.couponService = coupons;
        this.subsidyService = subsidies;
    }

    public void accept(String orderId) {
        Order o = require(orderId);
        runTransition(o, "ACCEPT", () -> {
            fsm.accept(o, clock.now());
            orders.save(o);
            audit.record("system", "ORDER_ACCEPT", o.id(), null);
        });
    }

    public void start(String orderId) {
        Order o = require(orderId);
        runTransition(o, "START", () -> {
            fsm.start(o, clock.now());
            orders.save(o);
            audit.record("system", "ORDER_START", o.id(), null);
        });
    }

    public void complete(String orderId) {
        Order o = require(orderId);
        runTransition(o, "COMPLETE", () -> {
            fsm.complete(o, clock.now());
            orders.save(o);
            persistSubsidyUsage(o);
            audit.record("system", "ORDER_COMPLETE", o.id(), null);
        });
    }

    private void persistSubsidyUsage(Order o) {
        if (o.fare() == null) return;
        Money applied = o.fare().subsidyApplied();
        if (applied.isZero()) return;
        Customer c = customers.findById(o.customerId())
                .orElseThrow(() -> new OrderException("unknown customer"));
        c.recordSubsidyUsage(applied);
        customers.save(c);
    }

    public void cancel(String orderId) {
        Order o = require(orderId);
        runTransition(o, "CANCEL", () -> {
            LocalDateTime now = clock.now();
            Money fee = pricing.computeCancellationFee(o, now);
            fsm.cancel(o, now);
            o.setCancellationFee(fee);
            orders.save(o);
            audit.record("system", "ORDER_CANCEL", o.id(), fee.toString());
        });
    }

    public Dispute openDispute(String orderId, String reason) {
        Order o = require(orderId);
        Dispute d = new Dispute(ids.next(), o.id(), reason, clock.now());
        String opId = "order:" + o.id() + ":DISPUTE:" + d.id();
        checkpoints.runIdempotentVoid(opId, "dispute " + o.id(), () -> {
            fsm.openDispute(o, clock.now(), pricing.config().disputeWindowDays());
            orders.save(o);
            disputes.save(d);
            audit.record("system", "ORDER_DISPUTE", o.id(), reason);
        });
        return d;
    }

    public int autoCancelStale() {
        LocalDateTime now = clock.now();
        int count = 0;
        for (Order o : orders.findByState(OrderState.PENDING_MATCH)) {
            if (fsm.shouldAutoCancel(o, now, pricing.config().autoCancelMinutes())) {
                String opId = "order:" + o.id() + ":AUTO_CANCEL";
                checkpoints.clear(opId);
                checkpoints.runIdempotentVoid(opId, "auto-cancel " + o.id(), () -> {
                    fsm.cancel(o, now);
                    orders.save(o);
                    audit.record("system", "ORDER_AUTO_CANCEL", o.id(), null);
                });
                count++;
            }
        }
        return count;
    }

    public Optional<Order> find(String id) { return orders.findById(id); }
    public List<Order> list() { return orders.findAll(); }
    public List<Order> listByState(OrderState s) { return orders.findByState(s); }

    private void runTransition(Order order, String transition, Runnable work) {
        String opId = "order:" + order.id() + ":" + transition;
        checkpoints.runIdempotentVoid(opId, transition + " " + order.id(), work);
    }

    private Order require(String orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderException("unknown order"));
    }
}
