package com.fleetride.repository;

import com.fleetride.domain.Address;
import com.fleetride.domain.Attachment;
import com.fleetride.domain.AuditEvent;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Dispute;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.Payment;
import com.fleetride.domain.Role;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.ShareLink;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.Trip;
import com.fleetride.domain.TripStatus;
import com.fleetride.domain.User;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRepositoriesTest {

    private Order buildOrder(String id, String customerId, OrderState state) {
        Address a = new Address("1 Main", "NY", "NY", "10001", null);
        TimeWindow w = new TimeWindow(
                LocalDateTime.of(2026, 3, 27, 14, 0),
                LocalDateTime.of(2026, 3, 27, 15, 0));
        Order o = new Order(id, customerId, a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 1, 1, null, LocalDateTime.of(2026, 3, 27, 13, 0));
        o.setState(state);
        return o;
    }

    @Test
    void customerRepo() {
        InMemoryCustomerRepository r = new InMemoryCustomerRepository();
        Customer c = new Customer("c1", "a", "p", null);
        r.save(c);
        assertEquals(c, r.findById("c1").orElseThrow());
        assertTrue(r.findById("other").isEmpty());
        assertEquals(1, r.findAll().size());
        r.delete("c1");
        assertTrue(r.findAll().isEmpty());
    }

    @Test
    void orderRepo() {
        InMemoryOrderRepository r = new InMemoryOrderRepository();
        r.save(buildOrder("o1", "c1", OrderState.PENDING_MATCH));
        r.save(buildOrder("o2", "c1", OrderState.COMPLETED));
        r.save(buildOrder("o3", "c2", OrderState.PENDING_MATCH));

        assertTrue(r.findById("o1").isPresent());
        assertTrue(r.findById("nope").isEmpty());
        assertEquals(2, r.findByCustomer("c1").size());
        assertEquals(2, r.findByState(OrderState.PENDING_MATCH).size());
        assertEquals(3, r.findAll().size());
        r.delete("o1");
        assertEquals(2, r.findAll().size());
    }

    @Test
    void paymentRepo() {
        InMemoryPaymentRepository r = new InMemoryPaymentRepository();
        Payment p1 = new Payment("p1", "o1", Payment.Tender.CASH, Payment.Kind.DEPOSIT,
                Money.of("1"), LocalDateTime.now(), null);
        Payment p2 = new Payment("p2", "o2", Payment.Tender.CASH, Payment.Kind.DEPOSIT,
                Money.of("1"), LocalDateTime.now(), null);
        r.save(p1);
        r.save(p2);
        assertEquals(p1, r.findById("p1").orElseThrow());
        assertTrue(r.findById("x").isEmpty());
        assertEquals(1, r.findByOrder("o1").size());
        assertEquals(2, r.findAll().size());
    }

    @Test
    void userRepo() {
        InMemoryUserRepository r = new InMemoryUserRepository();
        User u = new User("u1", "alice", "pw", Role.DISPATCHER);
        r.save(u);
        assertEquals(u, r.findById("u1").orElseThrow());
        assertEquals(u, r.findByUsername("alice").orElseThrow());
        assertTrue(r.findByUsername("missing").isEmpty());
        assertTrue(r.findById("nope").isEmpty());
        assertEquals(1, r.findAll().size());
    }

    @Test
    void attachmentRepo() {
        InMemoryAttachmentRepository r = new InMemoryAttachmentRepository();
        Attachment a = new Attachment("a1", "o1", "f.pdf", "/tmp/x",
                "application/pdf", 1, "sha", LocalDateTime.now());
        Attachment b = new Attachment("a2", "o2", "g.pdf", "/tmp/y",
                "application/pdf", 1, "sha", LocalDateTime.now());
        r.save(a);
        r.save(b);
        assertEquals(a, r.findById("a1").orElseThrow());
        assertTrue(r.findById("x").isEmpty());
        assertEquals(1, r.findByOrder("o1").size());
        r.delete("a1");
        assertTrue(r.findById("a1").isEmpty());
    }

    @Test
    void disputeRepo() {
        InMemoryDisputeRepository r = new InMemoryDisputeRepository();
        Dispute d = new Dispute("d1", "o1", "r", LocalDateTime.now());
        Dispute d2 = new Dispute("d2", "o2", "r", LocalDateTime.now());
        r.save(d);
        r.save(d2);
        assertEquals(d, r.findById("d1").orElseThrow());
        assertTrue(r.findById("x").isEmpty());
        assertEquals(1, r.findByOrder("o1").size());
        assertEquals(2, r.findAll().size());
    }

    @Test
    void auditRepo() {
        InMemoryAuditRepository r = new InMemoryAuditRepository();
        r.save(new AuditEvent("e1", "u", "A", "t", "d", LocalDateTime.now()));
        assertEquals(1, r.findAll().size());
    }

    @Test
    void tripRepo() {
        InMemoryTripRepository r = new InMemoryTripRepository();
        TimeWindow w = new TimeWindow(
                LocalDateTime.of(2026, 3, 27, 14, 0),
                LocalDateTime.of(2026, 3, 27, 15, 0));
        Trip t1 = new Trip("t1", VehicleType.STANDARD, 3, w,
                LocalDateTime.of(2026, 3, 27, 13, 0));
        t1.setOwnerUserId("u-1");
        Trip t2 = new Trip("t2", VehicleType.XL, 6, w,
                LocalDateTime.of(2026, 3, 27, 13, 0));
        t2.setStatus(TripStatus.CLOSED);
        t2.setOwnerUserId("u-2");
        r.save(t1);
        r.save(t2);
        assertEquals(t1, r.findById("t1").orElseThrow());
        assertTrue(r.findById("x").isEmpty());
        assertEquals(2, r.findAll().size());
        assertEquals(1, r.findByStatus(TripStatus.PLANNING).size());
        assertEquals(1, r.findByStatus(TripStatus.CLOSED).size());
        assertEquals(1, r.findByOwner("u-1").size());
        assertTrue(r.findByOwner(null).isEmpty());
        r.delete("t1");
        assertEquals(1, r.findAll().size());
    }

    @Test
    void shareLinkRepo() {
        InMemoryShareLinkRepository r = new InMemoryShareLinkRepository();
        LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);
        ShareLink l = new ShareLink("tok", "r", "m", t, t.plusHours(1));
        r.save(l);
        assertEquals(l, r.findByToken("tok").orElseThrow());
        r.delete("tok");
        assertTrue(r.findByToken("tok").isEmpty());
    }
}
