package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Fare;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {

    private InMemoryPaymentRepository payments;
    private InMemoryOrderRepository orders;
    private PaymentService svc;
    private OrderService orderService;
    private AtomicReference<LocalDateTime> now;
    private final LocalDateTime windowStart = LocalDateTime.of(2026, 3, 27, 14, 0);

    @BeforeEach
    void setup() {
        payments = new InMemoryPaymentRepository();
        orders = new InMemoryOrderRepository();
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
        InMemoryDisputeRepository disputes = new InMemoryDisputeRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        now = new AtomicReference<>(windowStart.minusHours(2));

        PricingEngine pricing = new PricingEngine(new PricingConfig());
        OrderStateMachine fsm = new OrderStateMachine();
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        Clock clock = () -> now.get();
        AuditService audit = new AuditService(audits, ids, clock);

        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);

        customers.save(new Customer("cid", "Alice", "p", null));
        orderService = new OrderService(orders, customers, disputes, pricing, fsm, ids, clock, audit, cp);
        svc = new PaymentService(payments, orders, ids, clock, audit);
    }

    private Order anOrder() {
        Address a = new Address("1 Main", "NY", "NY", "10001", null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        Order o = orderService.create("cid", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 3, 10, null);
        orderService.quote(o, null, null);
        return o;
    }

    @Test
    void recordDepositAndFinal() {
        Order o = anOrder();
        svc.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("3.00"));
        svc.recordFinal(o.id(), Payment.Tender.CASH, Money.of("9.40"));
        assertEquals(Money.of("12.40"), svc.totalPaid(o.id()));
        assertEquals(Money.ZERO, svc.balanceFor(o));
        assertFalse(svc.isOverdue(o));
    }

    @Test
    void balanceOrderWithoutFare() {
        Order o = anOrder();
        o.setFare(null);
        orders.save(o);
        assertEquals(Money.ZERO, svc.balanceFor(o));
    }

    @Test
    void refundPartial() {
        Order o = anOrder();
        svc.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("12.40"));
        svc.refund(o.id(), Payment.Tender.CASH, Money.of("2.00"), "customer credit");
        assertEquals(Money.of("10.40"), svc.totalPaid(o.id()));
    }

    @Test
    void refundExceedingPaidThrows() {
        Order o = anOrder();
        svc.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("5.00"));
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.refund(o.id(), Payment.Tender.CASH, Money.of("6.00"), "x"));
    }

    @Test
    void recordCancelFee() {
        Order o = anOrder();
        o.setCancellationFee(Money.of("5.00"));
        orders.save(o);
        svc.recordCancelFee(o.id(), Payment.Tender.CASH, Money.of("5.00"));
        assertEquals(Money.of("5.00"), svc.totalPaid(o.id()));
    }

    @Test
    void unknownOrderThrows() {
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordDeposit("nope", Payment.Tender.CASH, Money.of("1")));
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordFinal("nope", Payment.Tender.CASH, Money.of("1")));
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordCancelFee("nope", Payment.Tender.CASH, Money.of("1")));
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.refund("nope", Payment.Tender.CASH, Money.of("1"), "r"));
    }

    @Test
    void isOverdueWhenBalancePositive() {
        Order o = anOrder();
        assertTrue(svc.isOverdue(o));
    }

    @Test
    void depositRejectsZero() {
        Order o = anOrder();
        PaymentService.PaymentException ex = assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordDeposit(o.id(), Payment.Tender.CASH, Money.ZERO));
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    void depositRejectsNegative() {
        Order o = anOrder();
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("-1.00")));
    }

    @Test
    void finalRejectsNonPositive() {
        Order o = anOrder();
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordFinal(o.id(), Payment.Tender.CASH, Money.ZERO));
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordFinal(o.id(), Payment.Tender.CASH, Money.of("-5.00")));
    }

    @Test
    void cancelFeeRejectsNonPositive() {
        Order o = anOrder();
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordCancelFee(o.id(), Payment.Tender.CASH, Money.ZERO));
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordCancelFee(o.id(), Payment.Tender.CASH, Money.of("-2.00")));
    }

    @Test
    void refundRejectsNonPositive() {
        Order o = anOrder();
        svc.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("10.00"));
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.refund(o.id(), Payment.Tender.CASH, Money.ZERO, "r"));
        // A negative refund would otherwise sign-flip into an extra deposit.
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.refund(o.id(), Payment.Tender.CASH, Money.of("-3.00"), "r"));
    }

    @Test
    void depositRejectsNullAmount() {
        Order o = anOrder();
        assertThrows(PaymentService.PaymentException.class,
                () -> svc.recordDeposit(o.id(), Payment.Tender.CASH, null));
    }

    @Test
    void listForOrderAndAll() {
        Order o = anOrder();
        svc.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("2"));
        svc.recordFinal(o.id(), Payment.Tender.CARD_ON_FILE, Money.of("3"));
        assertEquals(2, svc.listForOrder(o.id()).size());
        assertEquals(2, svc.listAll().size());
    }
}
