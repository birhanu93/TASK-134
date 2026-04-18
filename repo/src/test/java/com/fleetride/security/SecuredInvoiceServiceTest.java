package com.fleetride.security;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Invoice;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.Payment;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.Role;
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
import com.fleetride.repository.InMemoryUserRepository;
import com.fleetride.service.AuditService;
import com.fleetride.service.AuthService;
import com.fleetride.service.CheckpointService;
import com.fleetride.service.Clock;
import com.fleetride.service.EncryptionService;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.InvoiceService;
import com.fleetride.service.OrderService;
import com.fleetride.service.OrderStateMachine;
import com.fleetride.service.PaymentService;
import com.fleetride.service.PricingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SecuredInvoiceServiceTest {

    private AuthService auth;
    private SecuredInvoiceService svc;
    private InvoiceService delegate;
    private OrderService orderService;
    private AtomicReference<LocalDateTime> now;
    private final LocalDateTime windowStart = LocalDateTime.of(2026, 3, 27, 14, 0);

    @BeforeEach
    void setup() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        EncryptionService enc = new EncryptionService("k");
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        now = new AtomicReference<>(windowStart.minusHours(2));
        Clock clock = now::get;

        auth = new AuthService(users, enc, ids);
        Authorizer authz = new Authorizer(auth);
        auth.bootstrapAdministrator("admin", "pw");
        auth.login("admin", "pw");
        auth.register("disp", "pw", Role.DISPATCHER);
        auth.register("fin", "pw", Role.FINANCE_CLERK);
        auth.logout();

        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
        InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
        InMemoryDisputeRepository disputes = new InMemoryDisputeRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        InMemoryInvoiceRepository invoices = new InMemoryInvoiceRepository();
        AuditService audit = new AuditService(audits, ids, clock);
        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);
        customers.save(new Customer("cid", "A", "p", null));
        orderService = new OrderService(orders, customers, disputes,
                new PricingEngine(new PricingConfig()), new OrderStateMachine(),
                ids, clock, audit, cp);
        PaymentService payment = new PaymentService(payments, orders, ids, clock, audit);
        delegate = new InvoiceService(invoices, orders, payment, ids, clock, audit);
        svc = new SecuredInvoiceService(delegate, authz);

        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        Order o = orderService.create("cid", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 3, 10, null);
        orderService.quote(o, null, null);
        auth.login("fin", "pw");
        Invoice i = svc.issueFor(o.id(), null);
        auth.logout();
        this.orderIdHolder = o.id();
        this.invoiceIdHolder = i.id();
    }

    private String orderIdHolder;
    private String invoiceIdHolder;

    @Test
    void dispatcherCannotTouchInvoices() {
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> svc.issueFor(orderIdHolder, null));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> svc.markPaid(invoiceIdHolder));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> svc.cancel(invoiceIdHolder));
        assertThrows(Authorizer.ForbiddenException.class, () -> svc.listAll());
        assertThrows(Authorizer.ForbiddenException.class, () -> svc.find(invoiceIdHolder));
        assertThrows(Authorizer.ForbiddenException.class, () -> svc.listForOrder(orderIdHolder));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> svc.listByStatus(Invoice.Status.ISSUED));
    }

    @Test
    void financeCanManageInvoices() {
        auth.login("fin", "pw");
        assertTrue(svc.find(invoiceIdHolder).isPresent());
        assertEquals(1, svc.listAll().size());
        assertEquals(1, svc.listForOrder(orderIdHolder).size());
        assertEquals(1, svc.listByStatus(Invoice.Status.ISSUED).size());
        svc.markPaid(invoiceIdHolder);
        assertEquals(Invoice.Status.PAID,
                delegate.find(invoiceIdHolder).orElseThrow().status());
    }

    @Test
    void financeCanCancelInvoice() {
        auth.login("fin", "pw");
        svc.cancel(invoiceIdHolder);
        assertEquals(Invoice.Status.CANCELED,
                delegate.find(invoiceIdHolder).orElseThrow().status());
    }
}
