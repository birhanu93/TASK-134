package com.fleetride.repository.sqlite;

import com.fleetride.domain.Address;
import com.fleetride.domain.Attachment;
import com.fleetride.domain.AuditEvent;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Dispute;
import com.fleetride.domain.Fare;
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
import com.fleetride.repository.CheckpointRepository;
import com.fleetride.repository.JobRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SqliteRepositoriesTest {

    private Database db;
    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    @BeforeEach
    void setup() { db = Database.inMemory(); }

    @AfterEach
    void teardown() { db.close(); }

    private Order buildOrder(String id) {
        Address p = new Address("1 Main", "NY", "NY", "10001", 5);
        Address d = new Address("2 Main", "NY", "NY", "10001", null);
        TimeWindow w = new TimeWindow(t, t.plusHours(1));
        Order o = new Order(id, "cust", p, d, 2, w, VehicleType.STANDARD,
                ServicePriority.PRIORITY, 3.0, 10, "COUPON", t);
        o.setAcceptedAt(t.plusMinutes(1));
        o.setStartedAt(t.plusMinutes(2));
        o.setCompletedAt(t.plusMinutes(30));
        o.setCanceledAt(t.plusMinutes(45));
        o.setDisputedAt(t.plusDays(1));
        o.setFare(new Fare(Money.of("3.50"), Money.of("5.40"), Money.of("3.50"),
                Money.of("1.00"), Money.of("2.00"), Money.of("1.00"),
                Money.of("0.50"), Money.of("15.40"), Money.of("13.90"), Money.of("2.78")));
        o.setCancellationFee(Money.of("5.00"));
        o.setOwnerUserId("owner-1");
        return o;
    }

    @Test
    void userRepoRoundTrip() {
        SqliteUserRepository r = new SqliteUserRepository(db);
        User u = new User("u1", "alice", "hash", Role.DISPATCHER);
        r.save(u);
        assertEquals(u, r.findById("u1").orElseThrow());
        assertEquals(u, r.findByUsername("alice").orElseThrow());
        assertTrue(r.findById("no").isEmpty());
        assertTrue(r.findByUsername("no").isEmpty());
        assertEquals(1, r.findAll().size());
        r.save(new User("u1", "alice2", "hash2", Role.ADMINISTRATOR));
        assertEquals("alice2", r.findById("u1").orElseThrow().username());
    }

    @Test
    void customerRepoRoundTripAndOwnerFilter() {
        SqliteCustomerRepository r = new SqliteCustomerRepository(db);
        Customer c1 = new Customer("c1", "Alice", "555", "tok", Money.of("10.00"), "owner-1");
        Customer c2 = new Customer("c2", "Bob", "555", null, Money.ZERO, "owner-2");
        r.save(c1);
        r.save(c2);
        Customer loaded = r.findById("c1").orElseThrow();
        assertEquals("Alice", loaded.name());
        assertEquals("tok", loaded.encryptedPaymentToken());
        assertEquals(Money.of("10.00"), loaded.subsidyUsedThisMonth());
        assertEquals("owner-1", loaded.ownerUserId());
        assertEquals(1, r.findByOwner("owner-1").size());
        assertEquals(2, r.findAll().size());
        r.delete("c1");
        assertTrue(r.findById("c1").isEmpty());
    }

    @Test
    void orderRepoFullRoundTrip() {
        new SqliteCustomerRepository(db).save(new Customer("cust", "A", "555", null));
        SqliteOrderRepository r = new SqliteOrderRepository(db);
        Order o = buildOrder("o1");
        r.save(o);
        Order loaded = r.findById("o1").orElseThrow();
        assertEquals(OrderState.PENDING_MATCH, loaded.state());
        assertEquals(5, loaded.pickup().floor());
        assertNull(loaded.dropoff().floor());
        assertEquals(ServicePriority.PRIORITY, loaded.priority());
        assertNotNull(loaded.fare());
        assertEquals(Money.of("13.90"), loaded.fare().total());
        assertEquals("owner-1", loaded.ownerUserId());
        assertEquals(Money.of("5.00"), loaded.cancellationFee());

        o.setState(OrderState.COMPLETED);
        r.save(o);
        assertEquals(OrderState.COMPLETED, r.findById("o1").orElseThrow().state());

        assertEquals(1, r.findByCustomer("cust").size());
        assertEquals(1, r.findByState(OrderState.COMPLETED).size());
        assertEquals(1, r.findByOwner("owner-1").size());
        assertEquals(1, r.findAll().size());
        r.delete("o1");
        assertTrue(r.findById("o1").isEmpty());
    }

    @Test
    void orderRepoWithoutFare() {
        new SqliteCustomerRepository(db).save(new Customer("cust", "A", "555", null));
        SqliteOrderRepository r = new SqliteOrderRepository(db);
        Address a = new Address("1 Main", "NY", null, null, null);
        TimeWindow w = new TimeWindow(t, t.plusHours(1));
        Order o = new Order("o2", "cust", a, a, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 1, 1, null, t);
        r.save(o);
        Order loaded = r.findById("o2").orElseThrow();
        assertNull(loaded.fare());
    }

    @Test
    void tripRepoRoundTrip() {
        SqliteTripRepository r = new SqliteTripRepository(db);
        TimeWindow w = new TimeWindow(t, t.plusHours(1));
        Trip trip = new Trip("tr1", VehicleType.STANDARD, 4, w, t);
        trip.setDriverPlaceholder("drv-1");
        trip.setOwnerUserId("owner-1");
        r.save(trip);

        Trip loaded = r.findById("tr1").orElseThrow();
        assertEquals(VehicleType.STANDARD, loaded.vehicleType());
        assertEquals(4, loaded.capacity());
        assertEquals("drv-1", loaded.driverPlaceholder());
        assertEquals("owner-1", loaded.ownerUserId());
        assertEquals(TripStatus.PLANNING, loaded.status());

        // Update path: change status and persist.
        loaded.setStatus(TripStatus.DISPATCHED);
        loaded.setDispatchedAt(t.plusMinutes(5));
        r.save(loaded);
        Trip reloaded = r.findById("tr1").orElseThrow();
        assertEquals(TripStatus.DISPATCHED, reloaded.status());
        assertEquals(t.plusMinutes(5), reloaded.dispatchedAt());

        assertEquals(1, r.findByStatus(TripStatus.DISPATCHED).size());
        assertEquals(0, r.findByStatus(TripStatus.PLANNING).size());
        assertEquals(1, r.findByOwner("owner-1").size());
        assertEquals(1, r.findAll().size());
        r.delete("tr1");
        assertTrue(r.findById("tr1").isEmpty());
    }

    @Test
    void orderRepoBindsTripId() {
        new SqliteCustomerRepository(db).save(new Customer("cust", "A", "555", null));
        TimeWindow tw = new TimeWindow(t, t.plusHours(1));
        new SqliteTripRepository(db).save(new Trip("trip-A", VehicleType.STANDARD, 4, tw, t));
        SqliteOrderRepository r = new SqliteOrderRepository(db);
        Order o = buildOrder("o1");
        o.setTripId("trip-A");
        r.save(o);
        assertEquals("trip-A", r.findById("o1").orElseThrow().tripId());
        assertEquals(1, r.findByTrip("trip-A").size());
        assertEquals(0, r.findByTrip("trip-Z").size());
    }

    @Test
    void paymentRepoRoundTrip() {
        new SqliteCustomerRepository(db).save(new Customer("cust", "A", "555", null));
        new SqliteOrderRepository(db).save(buildOrder("o1"));
        SqlitePaymentRepository r = new SqlitePaymentRepository(db);
        Payment p = new Payment("p1", "o1", Payment.Tender.CASH, Payment.Kind.DEPOSIT,
                Money.of("12.40"), t, "note");
        r.save(p);
        r.save(p); // idempotent by on conflict do nothing
        Payment loaded = r.findById("p1").orElseThrow();
        assertEquals(Money.of("12.40"), loaded.amount());
        assertEquals("note", loaded.notes());
        assertEquals(1, r.findByOrder("o1").size());
        assertEquals(1, r.findAll().size());
        assertTrue(r.findById("nope").isEmpty());
    }

    @Test
    void attachmentRepoRoundTrip() {
        SqliteAttachmentRepository r = new SqliteAttachmentRepository(db);
        Attachment a = new Attachment("a1", "o1", "f.pdf", "/tmp/x",
                "application/pdf", 100, "sha", t);
        r.save(a);
        assertEquals(a, r.findById("a1").orElseThrow());
        assertTrue(r.findById("no").isEmpty());
        assertEquals(1, r.findByOrder("o1").size());
        r.delete("a1");
        assertTrue(r.findById("a1").isEmpty());
    }

    @Test
    void disputeRepoAllStates() {
        new SqliteCustomerRepository(db).save(new Customer("cust", "A", "555", null));
        new SqliteOrderRepository(db).save(buildOrder("o1"));
        SqliteDisputeRepository r = new SqliteDisputeRepository(db);
        Dispute open = new Dispute("d1", "o1", "wrong", t);
        r.save(open);
        assertEquals(Dispute.Status.OPEN, r.findById("d1").orElseThrow().status());

        Dispute resolved = new Dispute("d2", "o1", "wrong", t);
        resolved.resolve("fixed", t.plusDays(1));
        r.save(resolved);
        Dispute loadedResolved = r.findById("d2").orElseThrow();
        assertEquals(Dispute.Status.RESOLVED, loadedResolved.status());
        assertEquals("fixed", loadedResolved.resolution());

        Dispute rejected = new Dispute("d3", "o1", "wrong", t);
        rejected.reject("no", t.plusDays(2));
        r.save(rejected);
        assertEquals(Dispute.Status.REJECTED, r.findById("d3").orElseThrow().status());

        assertEquals(3, r.findByOrder("o1").size());
        assertEquals(3, r.findAll().size());
        assertTrue(r.findById("no").isEmpty());
    }

    @Test
    void auditRepoRoundTrip() {
        SqliteAuditRepository r = new SqliteAuditRepository(db);
        r.save(new AuditEvent("e1", "u", "A", "t", "d", t));
        assertEquals(1, r.findAll().size());
        assertEquals("u", r.findAll().get(0).actor());
    }

    @Test
    void shareLinkRepoRoundTrip() {
        SqliteShareLinkRepository r = new SqliteShareLinkRepository(db);
        ShareLink l = new ShareLink("tok", "r", "m", t, t.plusHours(1));
        r.save(l);
        r.save(l); // idempotent
        assertEquals(l, r.findByToken("tok").orElseThrow());
        assertTrue(r.findByToken("nope").isEmpty());
        r.delete("tok");
        assertTrue(r.findByToken("tok").isEmpty());
    }

    @Test
    void checkpointRepoRoundTrip() {
        SqliteCheckpointRepository r = new SqliteCheckpointRepository(db);
        CheckpointRepository.Record rec = new CheckpointRepository.Record(
                "op1", "details", CheckpointRepository.Status.PENDING, t);
        r.upsert(rec);
        assertEquals("details", r.find("op1").orElseThrow().detail());
        assertEquals(1, r.findPending().size());

        r.upsert(new CheckpointRepository.Record("op1", "details",
                CheckpointRepository.Status.COMMITTED, t));
        assertEquals(CheckpointRepository.Status.COMMITTED, r.find("op1").orElseThrow().status());
        assertEquals(0, r.findPending().size());
        assertTrue(r.find("nope").isEmpty());
        r.delete("op1");
        assertTrue(r.find("op1").isEmpty());
    }

    @Test
    void jobRunRepoRoundTrip() {
        SqliteJobRunRepository r = new SqliteJobRunRepository(db);
        r.upsert(new JobRunRepository.Record("j1", "name", t, null, null,
                JobRunRepository.Status.RUNNING, null));
        assertEquals(JobRunRepository.Status.RUNNING, r.find("j1").orElseThrow().status());
        r.upsert(new JobRunRepository.Record("j1", "name", t, t.plusMinutes(1), 5,
                JobRunRepository.Status.SUCCESS, "ok"));
        assertEquals(5, r.find("j1").orElseThrow().processed());
        assertEquals(1, r.findRecent(10).size());
        assertEquals(1, r.findByStatus(JobRunRepository.Status.SUCCESS).size());
        assertTrue(r.find("nope").isEmpty());

        r.upsert(new JobRunRepository.Record("j2", "other", t.plusMinutes(5), null, null,
                JobRunRepository.Status.RUNNING, null));
        assertEquals(2, r.findRecent(10).size());
        // first sorted should be the newer one
        assertEquals("j2", r.findRecent(10).get(0).id());
    }
}
