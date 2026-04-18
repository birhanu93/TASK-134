package com.fleetride.security;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.Payment;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.Role;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.InMemoryAttachmentRepository;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryCheckpointRepository;
import com.fleetride.repository.InMemoryCustomerRepository;
import com.fleetride.repository.InMemoryDisputeRepository;
import com.fleetride.repository.InMemoryOrderRepository;
import com.fleetride.repository.InMemoryPaymentRepository;
import com.fleetride.repository.InMemoryUserRepository;
import com.fleetride.service.AttachmentService;
import com.fleetride.service.AuditService;
import com.fleetride.service.AuthService;
import com.fleetride.service.CheckpointService;
import com.fleetride.service.Clock;
import com.fleetride.service.ConfigService;
import com.fleetride.service.CustomerService;
import com.fleetride.service.EncryptionService;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.OrderService;
import com.fleetride.service.OrderStateMachine;
import com.fleetride.service.PaymentService;
import com.fleetride.service.PricingEngine;
import com.fleetride.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SecuredServicesTest {

    private AuthService auth;
    private Authorizer authz;
    private OrderService orderService;
    private CustomerService customerService;
    private PaymentService paymentService;
    private ReconciliationService recon;
    private AttachmentService attachmentService;
    private ConfigService configService;
    private InMemoryOrderRepository orders;
    private InMemoryCustomerRepository customers;
    private AtomicReference<LocalDateTime> now;
    private final LocalDateTime windowStart = LocalDateTime.of(2026, 3, 27, 14, 0);

    private SecuredOrderService securedOrders;
    private SecuredCustomerService securedCustomers;
    private SecuredPaymentService securedPayments;
    private SecuredReconciliationService securedRecon;
    private SecuredAttachmentService securedAttachments;
    private SecuredConfigService securedConfig;

    @BeforeEach
    void setup(@TempDir Path tmp) {
        InMemoryUserRepository users = new InMemoryUserRepository();
        orders = new InMemoryOrderRepository();
        customers = new InMemoryCustomerRepository();
        InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
        InMemoryDisputeRepository disputes = new InMemoryDisputeRepository();
        InMemoryAttachmentRepository atts = new InMemoryAttachmentRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();

        now = new AtomicReference<>(windowStart.minusHours(3));
        Clock clock = () -> now.get();
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();

        EncryptionService enc = new EncryptionService("k");
        auth = new AuthService(users, enc, ids);
        authz = new Authorizer(auth);

        PricingConfig pricing = new PricingConfig();
        AuditService audit = new AuditService(audits, ids, clock);
        CheckpointService cp = new CheckpointService(new InMemoryCheckpointRepository(), clock);

        customerService = new CustomerService(customers, enc, ids);
        orderService = new OrderService(orders, customers, disputes, new PricingEngine(pricing),
                new OrderStateMachine(), ids, clock, audit, cp);
        paymentService = new PaymentService(payments, orders, ids, clock, audit);
        recon = new ReconciliationService(orders, payments, paymentService);
        attachmentService = new AttachmentService(atts, tmp, ids, clock);
        configService = new ConfigService(pricing, new com.fleetride.repository.InMemoryConfigRepository());
        com.fleetride.service.MachineIdProvider mid =
                new com.fleetride.service.MachineIdProvider(tmp.resolve("machine-id"));
        com.fleetride.service.ShareLinkService shareLinks =
                new com.fleetride.service.ShareLinkService(
                        new com.fleetride.repository.InMemoryShareLinkRepository(), clock, mid);

        securedOrders = new SecuredOrderService(orderService, authz, orders, customers);
        securedCustomers = new SecuredCustomerService(customerService, authz, customers);
        securedPayments = new SecuredPaymentService(paymentService, authz);
        securedRecon = new SecuredReconciliationService(recon, authz);
        securedAttachments = new SecuredAttachmentService(attachmentService, authz, shareLinks, orders);
        securedConfig = new SecuredConfigService(configService, authz);

        auth.bootstrapAdministrator("admin", "pw");
        auth.login("admin", "pw");
        auth.register("dispA", "pw", Role.DISPATCHER);
        auth.register("dispB", "pw", Role.DISPATCHER);
        auth.register("fin", "pw", Role.FINANCE_CLERK);
        auth.logout();
    }

    private Customer someCustomer(String dispatcher) {
        auth.logout();
        auth.login(dispatcher, "pw");
        return securedCustomers.create("C " + dispatcher, "555", "tok");
    }

    private Order someOrder(String dispatcher, Customer c) {
        auth.logout();
        auth.login(dispatcher, "pw");
        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        return securedOrders.create(c.id(), a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 3, 10, null);
    }

    @Test
    void dispatcherCanonlySeeTheirOwnOrders() {
        Customer cA = someCustomer("dispA");
        Order oA = someOrder("dispA", cA);
        Customer cB = someCustomer("dispB");
        Order oB = someOrder("dispB", cB);

        auth.logout();
        auth.login("dispA", "pw");
        assertEquals(1, securedOrders.list().size());
        assertEquals(oA, securedOrders.list().get(0));
        assertTrue(securedOrders.find(oA.id()).isPresent());
        assertTrue(securedOrders.find(oB.id()).isEmpty(), "dispA cannot see dispB's order");
    }

    @Test
    void adminSeesAllOrders() {
        Customer cA = someCustomer("dispA");
        someOrder("dispA", cA);
        Customer cB = someCustomer("dispB");
        someOrder("dispB", cB);

        auth.logout();
        auth.login("admin", "pw");
        assertEquals(2, securedOrders.list().size());
    }

    @Test
    void adminCanAcceptAnyOrder() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        auth.login("admin", "pw");
        securedOrders.accept(o.id());
        assertEquals(OrderState.ACCEPTED, orders.findById(o.id()).orElseThrow().state());
    }

    @Test
    void adminListByState() {
        Customer c = someCustomer("dispA");
        someOrder("dispA", c);
        auth.logout();
        auth.login("admin", "pw");
        assertEquals(1, securedOrders.listByState(OrderState.PENDING_MATCH).size());
    }

    @Test
    void dispatcherListByStateFiltersForeign() {
        Customer cA = someCustomer("dispA");
        someOrder("dispA", cA);
        Customer cB = someCustomer("dispB");
        someOrder("dispB", cB);
        auth.logout();
        auth.login("dispA", "pw");
        assertEquals(1, securedOrders.listByState(OrderState.PENDING_MATCH).size());
    }

    @Test
    void financeSeesAllOrdersButCannotAccept() {
        Customer cA = someCustomer("dispA");
        Order oA = someOrder("dispA", cA);

        auth.logout();
        auth.login("fin", "pw");
        assertEquals(1, securedOrders.list().size());
        assertThrows(Authorizer.ForbiddenException.class, () -> securedOrders.accept(oA.id()));
    }

    @Test
    void dispatcherCannotCreateOrderForOtherDispatchersCustomer() {
        Customer cA = someCustomer("dispA");
        auth.logout();
        auth.login("dispB", "pw");
        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        Authorizer.ForbiddenException ex = assertThrows(Authorizer.ForbiddenException.class,
                () -> securedOrders.create(cA.id(), a, a, 1, w, VehicleType.STANDARD,
                        ServicePriority.NORMAL, 3, 10, null));
        assertTrue(ex.getMessage().contains("not visible"), "error should name the visibility failure: " + ex.getMessage());
    }

    @Test
    void dispatcherCannotCreateOrderForUnknownCustomerId() {
        auth.login("dispA", "pw");
        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        assertThrows(OrderService.OrderException.class,
                () -> securedOrders.create("ghost", a, a, 1, w, VehicleType.STANDARD,
                        ServicePriority.NORMAL, 3, 10, null));
    }

    @Test
    void financeCanSeeAllCustomersButCannotCreateOrder() {
        // Finance has CUSTOMER_READ across the board via canSeeAll, but no ORDER_CREATE.
        Customer cA = someCustomer("dispA");
        auth.logout();
        auth.login("fin", "pw");
        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedOrders.create(cA.id(), a, a, 1, w, VehicleType.STANDARD,
                        ServicePriority.NORMAL, 3, 10, null));
    }

    @Test
    void adminCanCreateOrderForAnyCustomer() {
        Customer cA = someCustomer("dispA");
        auth.logout();
        auth.login("admin", "pw");
        Address a = new Address("1", "NY", null, null, null);
        TimeWindow w = new TimeWindow(windowStart, windowStart.plusHours(1));
        // canSeeAll includes ADMINISTRATOR, so this should succeed.
        Order o = securedOrders.create(cA.id(), a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 3, 10, null);
        assertNotNull(o.id());
    }

    @Test
    void dispatcherCannotActOnOtherDispatchersOrder() {
        Customer cA = someCustomer("dispA");
        Order oA = someOrder("dispA", cA);

        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedOrders.accept(oA.id()));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedOrders.cancel(oA.id()));
    }

    @Test
    void dispatcherCanCancelOwnOrder() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        securedOrders.quote(o, null, null);
        securedOrders.cancel(o.id());
        assertEquals(OrderState.CANCELED, orders.findById(o.id()).orElseThrow().state());
    }

    @Test
    void orderLifecycleWithAuth() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        securedOrders.quote(o, null, null);
        securedOrders.accept(o.id());
        securedOrders.start(o.id());
        now.set(windowStart.plusMinutes(30));
        securedOrders.complete(o.id());
        now.set(windowStart.plusDays(1));
        assertNotNull(securedOrders.openDispute(o.id(), "issue"));
        assertEquals(1, securedOrders.listByState(OrderState.IN_DISPUTE).size());
    }

    @Test
    void quoteResolvingRespectsOwnership() {
        Customer cA = someCustomer("dispA");
        Order oA = someOrder("dispA", cA);
        auth.logout();
        auth.login("dispA", "pw");
        assertNotNull(securedOrders.quoteResolving(oA));
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedOrders.quoteResolving(oA));
    }

    @Test
    void quoteRequiresReadOnOwner() {
        Customer cA = someCustomer("dispA");
        Order oA = someOrder("dispA", cA);
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedOrders.quote(oA, null, null));
    }

    @Test
    void unknownOrderThrows() {
        auth.login("dispA", "pw");
        assertThrows(OrderService.OrderException.class, () -> securedOrders.accept("nope"));
    }

    @Test
    void schedulerPermissionRequiredForAutoCancel() {
        auth.login("dispA", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedOrders.autoCancelStale());
    }

    @Test
    void adminCanAutoCancel() {
        auth.login("admin", "pw");
        assertEquals(0, securedOrders.autoCancelStale());
    }

    @Test
    void findUnknownReturnsEmpty() {
        auth.login("admin", "pw");
        assertTrue(securedOrders.find("nope").isEmpty());
    }

    @Test
    void adminCanFindDispatcherOrder() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        auth.login("admin", "pw");
        assertTrue(securedOrders.find(o.id()).isPresent());
    }

    @Test
    void financeCanFindAnyOrder() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        auth.login("fin", "pw");
        assertTrue(securedOrders.find(o.id()).isPresent());
    }

    @Test
    void nullOwnerOrdersHiddenFromDispatchers() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        o.setOwnerUserId(null);
        orders.save(o);
        auth.logout();
        auth.login("dispB", "pw");
        assertTrue(securedOrders.find(o.id()).isEmpty());
        assertTrue(securedOrders.list().isEmpty());
        assertTrue(securedOrders.listByState(OrderState.PENDING_MATCH).isEmpty());
    }

    @Test
    void dispatcherCannotActOnNullOwnerOrder() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        o.setOwnerUserId(null);
        orders.save(o);
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedOrders.accept(o.id()));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedOrders.quote(o, null, null));
    }

    @Test
    void adminStillActsOnNullOwnerOrder() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        o.setOwnerUserId(null);
        orders.save(o);
        auth.logout();
        auth.login("admin", "pw");
        assertTrue(securedOrders.find(o.id()).isPresent());
        securedOrders.accept(o.id());
        assertEquals(OrderState.ACCEPTED, orders.findById(o.id()).orElseThrow().state());
    }

    @Test
    void financeSeesButCannotActOnNullOwnerOrder() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        o.setOwnerUserId(null);
        orders.save(o);
        auth.logout();
        auth.login("fin", "pw");
        assertTrue(securedOrders.find(o.id()).isPresent());
        assertThrows(Authorizer.ForbiddenException.class, () -> securedOrders.accept(o.id()));
    }

    @Test
    void customerVisibility() {
        Customer cA = someCustomer("dispA");
        Customer cB = someCustomer("dispB");
        auth.logout();
        auth.login("dispA", "pw");
        assertEquals(1, securedCustomers.list().size());
        assertTrue(securedCustomers.find(cA.id()).isPresent());
        assertTrue(securedCustomers.find(cB.id()).isEmpty());
    }

    @Test
    void customerCrossDispatcherDeleteForbidden() {
        Customer cA = someCustomer("dispA");
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedCustomers.delete(cA.id()));
    }

    @Test
    void adminCanDeleteAnyCustomer() {
        Customer cA = someCustomer("dispA");
        auth.logout();
        auth.login("admin", "pw");
        securedCustomers.delete(cA.id());
        assertTrue(customers.findById(cA.id()).isEmpty());
    }

    @Test
    void dispatcherCanDeleteOwnCustomer() {
        Customer c = someCustomer("dispA");
        auth.logout();
        auth.login("dispA", "pw");
        securedCustomers.delete(c.id());
        assertTrue(customers.findById(c.id()).isEmpty());
    }

    @Test
    void financeClerkCannotDeleteCustomer() {
        Customer c = someCustomer("dispA");
        auth.logout();
        auth.login("fin", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedCustomers.delete(c.id()));
    }

    @Test
    void customerDeleteUnknownThrows() {
        auth.login("admin", "pw");
        assertThrows(IllegalArgumentException.class, () -> securedCustomers.delete("nope"));
    }

    @Test
    void findUnknownCustomerReturnsEmpty() {
        auth.login("admin", "pw");
        assertTrue(securedCustomers.find("nope").isEmpty());
    }

    @Test
    void maskedTokenDeniedForNonOwnerDispatcher() {
        Customer cA = someCustomer("dispA");
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedCustomers.maskedPaymentToken(cA));
    }

    @Test
    void maskedTokenIgnoresForgedOwnerOnDetachedCopy() {
        Customer cA = someCustomer("dispA");
        // Simulate a caller passing a tampered snapshot whose ownerUserId claims dispB.
        Customer forged = new Customer(cA.id(), cA.name(), cA.phone(),
                cA.encryptedPaymentToken(), cA.subsidyUsedThisMonth(),
                "u-forged-owner");
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedCustomers.maskedPaymentToken(forged));
    }

    @Test
    void maskedTokenAllowedForOwnerAndAdminAndFinance() {
        Customer c = someCustomer("dispA");
        // owner
        auth.logout();
        auth.login("dispA", "pw");
        assertNotNull(securedCustomers.maskedPaymentToken(c));
        // finance (canSeeAll)
        auth.logout();
        auth.login("fin", "pw");
        assertNotNull(securedCustomers.maskedPaymentToken(c));
        // admin (canSeeAll)
        auth.logout();
        auth.login("admin", "pw");
        assertNotNull(securedCustomers.maskedPaymentToken(c));
    }

    @Test
    void maskedTokenRejectsNullOrUnknown() {
        auth.login("admin", "pw");
        assertThrows(IllegalArgumentException.class,
                () -> securedCustomers.maskedPaymentToken(null));
        Customer ghost = new Customer("ghost", "n", "p", null, Money.ZERO, "admin");
        assertThrows(IllegalArgumentException.class,
                () -> securedCustomers.maskedPaymentToken(ghost));
    }

    @Test
    void adminListAllCustomers() {
        someCustomer("dispA");
        someCustomer("dispB");
        auth.logout();
        auth.login("admin", "pw");
        assertEquals(2, securedCustomers.list().size());
    }

    @Test
    void financeListAllCustomers() {
        someCustomer("dispA");
        auth.logout();
        auth.login("fin", "pw");
        assertEquals(1, securedCustomers.list().size());
    }

    @Test
    void adminCanFindAnyCustomer() {
        Customer cA = someCustomer("dispA");
        auth.logout();
        auth.login("admin", "pw");
        assertTrue(securedCustomers.find(cA.id()).isPresent());
    }

    @Test
    void financeCanFindAnyCustomer() {
        Customer cA = someCustomer("dispA");
        auth.logout();
        auth.login("fin", "pw");
        assertTrue(securedCustomers.find(cA.id()).isPresent());
    }

    @Test
    void nullOwnerCustomerHiddenFromDispatchers() {
        Customer c = someCustomer("dispA");
        c.setOwnerUserId(null);
        customers.save(c);
        auth.logout();
        auth.login("dispB", "pw");
        assertTrue(securedCustomers.find(c.id()).isEmpty());
        assertTrue(securedCustomers.list().isEmpty());
    }

    @Test
    void dispatcherCannotDeleteNullOwnerCustomer() {
        Customer c = someCustomer("dispA");
        c.setOwnerUserId(null);
        customers.save(c);
        auth.logout();
        auth.login("dispA", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedCustomers.delete(c.id()));
    }

    @Test
    void adminCanDeleteNullOwnerCustomer() {
        Customer c = someCustomer("dispA");
        c.setOwnerUserId(null);
        customers.save(c);
        auth.logout();
        auth.login("admin", "pw");
        securedCustomers.delete(c.id());
        assertTrue(customers.findById(c.id()).isEmpty());
    }

    @Test
    void maskedPaymentTokenRequiresCustomerRead() {
        Customer c = someCustomer("dispA");
        auth.logout();
        auth.login("dispA", "pw");
        String masked = securedCustomers.maskedPaymentToken(c);
        assertNotNull(masked);
    }

    @Test
    void financeCanRecordPayments() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        securedOrders.quote(o, null, null);
        auth.logout();
        auth.login("fin", "pw");
        Payment p = securedPayments.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("3.00"));
        assertNotNull(p);
        assertEquals(Money.of("3.00"), securedPayments.totalPaid(o.id()));
    }

    @Test
    void dispatcherCannotRecordPayments() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        securedOrders.quote(o, null, null);
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedPayments.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("3")));
    }

    @Test
    void dispatcherCannotReadPayments() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        securedOrders.quote(o, null, null);
        assertThrows(Authorizer.ForbiddenException.class, () -> securedPayments.totalPaid(o.id()));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedPayments.balanceFor(o));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedPayments.isOverdue(o));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedPayments.listForOrder(o.id()));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedPayments.listAll());
    }

    @Test
    void finCanReadPaymentMetadata() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        securedOrders.quote(o, null, null);
        auth.logout();
        auth.login("fin", "pw");
        securedPayments.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("2"));
        assertEquals(Money.of("2"), securedPayments.totalPaid(o.id()));
        assertNotNull(securedPayments.balanceFor(o));
        assertTrue(securedPayments.isOverdue(o));
        assertEquals(1, securedPayments.listForOrder(o.id()).size());
        assertFalse(securedPayments.listAll().isEmpty());
    }

    @Test
    void finOperatesAllPaymentKinds() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        securedOrders.quote(o, null, null);
        auth.logout();
        auth.login("fin", "pw");
        securedPayments.recordDeposit(o.id(), Payment.Tender.CASH, Money.of("5"));
        securedPayments.recordFinal(o.id(), Payment.Tender.CASH, Money.of("5"));
        securedPayments.recordCancelFee(o.id(), Payment.Tender.CASH, Money.of("1"));
        securedPayments.refund(o.id(), Payment.Tender.CASH, Money.of("1"), "x");
        assertEquals(4, securedPayments.listForOrder(o.id()).size());
    }

    @Test
    void reconExportRequiresFinanceOrAdmin(@TempDir Path dir) throws Exception {
        auth.login("dispA", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedRecon.exportCsv(dir.resolve("x.csv")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedRecon.exportPaymentsCsv(dir.resolve("p.csv")));
        auth.logout();
        auth.login("fin", "pw");
        securedRecon.exportCsv(dir.resolve("x.csv"));
        securedRecon.exportPaymentsCsv(dir.resolve("p.csv"));
    }

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    @Test
    void attachmentsRequireAuth() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedAttachments.upload(o.id(), "f.pdf", "application/pdf",
                        new ByteArrayInputStream(PDF), PDF.length));
        auth.login("dispA", "pw");
        var a = securedAttachments.upload(o.id(), "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        assertNotNull(securedAttachments.find(a.id()));
        assertEquals(1, securedAttachments.listForOrder(o.id()).size());
        assertThrows(Authorizer.ForbiddenException.class, () -> securedAttachments.delete(a.id()));
        auth.logout();
        auth.login("admin", "pw");
        securedAttachments.delete(a.id());
    }

    @Test
    void dispatcherCannotSeeOtherDispatcherAttachments() {
        Customer cA = someCustomer("dispA");
        Order oA = someOrder("dispA", cA);
        auth.logout();
        auth.login("dispA", "pw");
        var a = securedAttachments.upload(oA.id(), "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        auth.logout();
        auth.login("dispB", "pw");
        assertTrue(securedAttachments.find(a.id()).isEmpty());
        assertTrue(securedAttachments.listForOrder(oA.id()).isEmpty());
        assertThrows(SecuredAttachmentService.AccessDeniedException.class,
                () -> securedAttachments.upload(oA.id(), "g.pdf", "application/pdf",
                        new ByteArrayInputStream(PDF), PDF.length));
    }

    @Test
    void financeCanReadAttachmentsAcrossOwners() {
        Customer cA = someCustomer("dispA");
        Order oA = someOrder("dispA", cA);
        auth.logout();
        auth.login("dispA", "pw");
        var a = securedAttachments.upload(oA.id(), "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        auth.logout();
        auth.login("fin", "pw");
        assertTrue(securedAttachments.find(a.id()).isPresent());
        assertEquals(1, securedAttachments.listForOrder(oA.id()).size());
    }

    @Test
    void attachmentTokenFlow() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        auth.login("dispA", "pw");
        var a = securedAttachments.upload(o.id(), "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        String tok = securedAttachments.issueShareToken(a.id(), 2);
        auth.logout();
        var resolved = securedAttachments.resolveByToken(tok);
        assertEquals(a.id(), resolved.id());
    }

    @Test
    void issueShareTokenRequiresOrderAccess() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        auth.login("dispA", "pw");
        var a = securedAttachments.upload(o.id(), "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        auth.logout();
        auth.login("dispB", "pw");
        assertThrows(SecuredAttachmentService.AccessDeniedException.class,
                () -> securedAttachments.issueShareToken(a.id(), 1));
    }

    @Test
    void issueShareTokenRequiresPermission() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        auth.login("dispA", "pw");
        var a = securedAttachments.upload(o.id(), "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        auth.logout();
        auth.login("fin", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedAttachments.issueShareToken(a.id(), 1));
    }

    @Test
    void issueShareTokenForUnknownAttachmentFails() {
        auth.login("dispA", "pw");
        assertThrows(SecuredAttachmentService.AccessDeniedException.class,
                () -> securedAttachments.issueShareToken("nope", 1));
    }

    @Test
    void deleteUnknownAttachmentFails() {
        auth.login("admin", "pw");
        assertThrows(SecuredAttachmentService.AccessDeniedException.class,
                () -> securedAttachments.delete("nope"));
    }

    @Test
    void findUnknownAttachmentReturnsEmpty() {
        auth.login("admin", "pw");
        assertTrue(securedAttachments.find("nope").isEmpty());
    }

    @Test
    void dispatcherCannotUploadToUnknownOrder() {
        auth.login("dispA", "pw");
        assertThrows(SecuredAttachmentService.AccessDeniedException.class,
                () -> securedAttachments.upload("unknown-order", "f.pdf", "application/pdf",
                        new ByteArrayInputStream(PDF), PDF.length));
    }

    @Test
    void dispatcherCannotAccessAttachmentForNullOwnerOrder() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        auth.login("dispA", "pw");
        var a = securedAttachments.upload(o.id(), "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        // admin clears ownership
        o.setOwnerUserId(null);
        orders.save(o);
        auth.logout();
        auth.login("dispB", "pw");
        assertTrue(securedAttachments.find(a.id()).isEmpty());
    }

    @Test
    void resolveByTokenRejectsUnknownToken() {
        assertThrows(com.fleetride.service.ShareLinkService.ShareLinkException.class,
                () -> securedAttachments.resolveByToken("not-a-real-token"));
    }

    @Test
    void resolveByTokenRejectsWrongScope(@TempDir Path dir) {
        com.fleetride.service.MachineIdProvider mid =
                new com.fleetride.service.MachineIdProvider(dir.resolve("tmp-machine-id"));
        com.fleetride.repository.InMemoryShareLinkRepository linkRepo =
                new com.fleetride.repository.InMemoryShareLinkRepository();
        com.fleetride.service.ShareLinkService link = new com.fleetride.service.ShareLinkService(
                linkRepo, () -> windowStart, mid);
        String tok = link.create("other:something").token();
        SecuredAttachmentService svc = new SecuredAttachmentService(
                attachmentService, authz, link, orders);
        assertThrows(SecuredAttachmentService.AccessDeniedException.class,
                () -> svc.resolveByToken(tok));
    }

    @Test
    void resolveByTokenFailsWhenAttachmentDeleted() {
        Customer c = someCustomer("dispA");
        Order o = someOrder("dispA", c);
        auth.logout();
        auth.login("dispA", "pw");
        var a = securedAttachments.upload(o.id(), "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        String tok = securedAttachments.issueShareToken(a.id(), 2);
        auth.logout();
        auth.login("admin", "pw");
        securedAttachments.delete(a.id());
        auth.logout();
        assertThrows(SecuredAttachmentService.AccessDeniedException.class,
                () -> securedAttachments.resolveByToken(tok));
    }

    @Test
    void configPricingSettersAdminOnly() {
        auth.login("dispA", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setBaseFare(Money.of("5")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setPerMile(Money.of("5")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setPerMinute(Money.of("5")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setPriorityMultiplier(new java.math.BigDecimal("1.5")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setLateCancelFee(Money.of("5")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setPerFloorSurcharge(Money.of("1")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setFreeFloorThreshold(4));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setDepositPercent(new java.math.BigDecimal("0.3")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setMonthlySubsidyCap(Money.of("75")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setMaxCouponPercent(new java.math.BigDecimal("0.25")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setCouponMinimumOrder(Money.of("40")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setAutoCancelMinutes(20));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setLateCancelWindowMinutes(12));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setDisputeWindowDays(10));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setOverdueFeePerSweep(Money.of("7")));
        auth.logout();
        auth.login("admin", "pw");
        securedConfig.setBaseFare(Money.of("5.00"));
        securedConfig.setPerMile(Money.of("2.50"));
        securedConfig.setPerMinute(Money.of("0.75"));
        securedConfig.setPriorityMultiplier(new java.math.BigDecimal("1.5"));
        securedConfig.setLateCancelFee(Money.of("7.00"));
        securedConfig.setPerFloorSurcharge(Money.of("1.25"));
        securedConfig.setFreeFloorThreshold(4);
        securedConfig.setDepositPercent(new java.math.BigDecimal("0.30"));
        securedConfig.setMonthlySubsidyCap(Money.of("75"));
        // Coupon percent is spec-capped at 20%.
        securedConfig.setMaxCouponPercent(new java.math.BigDecimal("0.15"));
        securedConfig.setCouponMinimumOrder(Money.of("40"));
        securedConfig.setAutoCancelMinutes(20);
        securedConfig.setLateCancelWindowMinutes(12);
        securedConfig.setDisputeWindowDays(10);
        securedConfig.setOverdueFeePerSweep(Money.of("7"));
        assertEquals(Money.of("5.00"), securedConfig.pricing().baseFare());
    }

    @Test
    void configReadCollections() {
        auth.login("admin", "pw");
        securedConfig.setDictionary("k1", "v1");
        securedConfig.setTemplate("t1", "hello");
        assertEquals(1, securedConfig.allDictionaries().size());
        assertEquals(1, securedConfig.allTemplates().size());
        auth.logout();
        auth.login("dispA", "pw");
        assertEquals(1, securedConfig.allDictionaries().size());
        assertEquals(1, securedConfig.allTemplates().size());
    }

    @Test
    void configRequiresAdminToWrite() {
        auth.login("dispA", "pw");
        assertNotNull(securedConfig.pricing());
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setDictionary("k", "v"));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedConfig.setTemplate("k", "v"));
        assertTrue(securedConfig.dictionary("k").isEmpty());
        assertTrue(securedConfig.template("k").isEmpty());
        auth.logout();
        auth.login("admin", "pw");
        securedConfig.setDictionary("k", "v");
        securedConfig.setTemplate("t", "Hello {name}");
        assertEquals("Hello Alice", securedConfig.render("t", Map.of("name", "Alice")));
    }
}
