package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Invoice;
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
import com.fleetride.repository.InMemoryInvoiceRepository;
import com.fleetride.repository.InMemoryOrderRepository;
import com.fleetride.repository.InMemoryPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceServiceTest {

    private InMemoryOrderRepository orders;
    private InMemoryInvoiceRepository invoices;
    private OrderService orderService;
    private PaymentService paymentService;
    private InvoiceService svc;
    private AtomicReference<LocalDateTime> now;
    private final LocalDateTime windowStart = LocalDateTime.of(2026, 3, 27, 14, 0);

    @BeforeEach
    void setup() {
        orders = new InMemoryOrderRepository();
        invoices = new InMemoryInvoiceRepository();
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
        InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
        InMemoryDisputeRepository disputes = new InMemoryDisputeRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        now = new AtomicReference<>(windowStart.minusHours(2));
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        Clock clock = now::get;
        AuditService audit = new AuditService(audits, ids, clock);
        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);
        customers.save(new Customer("cid", "A", "p", null));
        orderService = new OrderService(orders, customers, disputes,
                new PricingEngine(new PricingConfig()), new OrderStateMachine(),
                ids, clock, audit, cp);
        paymentService = new PaymentService(payments, orders, ids, clock, audit);
        svc = new InvoiceService(invoices, orders, paymentService, ids, clock, audit);
    }

    private Order anOrder() {
        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        Order o = orderService.create("cid", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 3, 10, null);
        orderService.quote(o, null, null);
        return o;
    }

    @Test
    void issueAndListAndMarkPaid() {
        Order o = anOrder();
        Invoice i = svc.issueFor(o.id(), "first month");
        assertEquals(Invoice.Status.ISSUED, i.status());
        assertEquals(1, svc.listForOrder(o.id()).size());
        assertEquals(1, svc.listByStatus(Invoice.Status.ISSUED).size());
        assertEquals(1, svc.listAll().size());
        assertTrue(svc.find(i.id()).isPresent());
        svc.markPaid(i.id());
        assertEquals(Invoice.Status.PAID, invoices.findById(i.id()).orElseThrow().status());
    }

    @Test
    void cancelTransition() {
        Order o = anOrder();
        Invoice i = svc.issueFor(o.id(), null);
        svc.cancel(i.id());
        assertEquals(Invoice.Status.CANCELED, invoices.findById(i.id()).orElseThrow().status());
    }

    @Test
    void issueRejectsUnknownOrder() {
        assertThrows(InvoiceService.InvoiceException.class,
                () -> svc.issueFor("nope", null));
    }

    @Test
    void issueRejectsZeroBalance() {
        Order o = anOrder();
        paymentService.recordFinal(o.id(), Payment.Tender.CASH, Money.of("12.40"));
        assertThrows(InvoiceService.InvoiceException.class, () -> svc.issueFor(o.id(), null));
    }

    @Test
    void markPaidUnknownThrows() {
        assertThrows(InvoiceService.InvoiceException.class, () -> svc.markPaid("nope"));
    }

    @Test
    void cancelUnknownThrows() {
        assertThrows(InvoiceService.InvoiceException.class, () -> svc.cancel("nope"));
    }
}
