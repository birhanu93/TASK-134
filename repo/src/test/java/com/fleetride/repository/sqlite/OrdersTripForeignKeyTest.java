package com.fleetride.repository.sqlite;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.Trip;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrdersTripForeignKeyTest {

    private Database db;
    private final LocalDateTime t = LocalDateTime.of(2026, 4, 18, 10, 0);

    @BeforeEach
    void setup() { db = Database.inMemory(); }

    @AfterEach
    void teardown() { db.close(); }

    private Order buildOrder(String id, String tripId) {
        Address p = new Address("1 Main", "NY", "NY", "10001", null);
        Address d = new Address("2 Main", "NY", "NY", "10001", null);
        TimeWindow w = new TimeWindow(t, t.plusHours(1));
        Order o = new Order(id, "cust", p, d, 1, w, VehicleType.STANDARD,
                ServicePriority.NORMAL, 1.0, 10, null, t);
        o.setTripId(tripId);
        return o;
    }

    private void seedCustomer() {
        new SqliteCustomerRepository(db).save(new Customer("cust", "A", "555", null));
    }

    private void seedTrip(String id) {
        TimeWindow w = new TimeWindow(t, t.plusHours(1));
        new SqliteTripRepository(db).save(new Trip(id, VehicleType.STANDARD, 4, w, t));
    }

    @Test
    void insertingOrphanTripIdIsRejected() {
        seedCustomer();
        SqliteOrderRepository r = new SqliteOrderRepository(db);
        Database.DbException ex = assertThrows(Database.DbException.class,
                () -> r.save(buildOrder("o1", "trip-missing")));
        assertTrue(ex.getMessage().toLowerCase().contains("update failed"));
    }

    @Test
    void insertingValidTripIdIsAccepted() {
        seedCustomer();
        seedTrip("trip-ok");
        SqliteOrderRepository r = new SqliteOrderRepository(db);
        r.save(buildOrder("o1", "trip-ok"));
        assertEquals("trip-ok", r.findById("o1").orElseThrow().tripId());
    }

    @Test
    void deletingTripClearsOrdersTripLink() {
        seedCustomer();
        seedTrip("trip-go");
        SqliteOrderRepository r = new SqliteOrderRepository(db);
        r.save(buildOrder("o1", "trip-go"));

        new SqliteTripRepository(db).delete("trip-go");

        assertNull(r.findById("o1").orElseThrow().tripId(),
                "ON DELETE SET NULL should clear the dangling trip link");
    }

    @Test
    void legacySchemaIsRebuiltAndOrphansAreRepaired() {
        // Pretend we have an older DB whose orders table was created before trip_id
        // was a foreign key (SQLite cannot add a FK through ALTER TABLE, so the
        // column existed as a loose reference). Insert a valid row and an orphan row,
        // then run the migration and confirm the orphan is cleared and the FK is now
        // enforced.
        seedCustomer();
        seedTrip("trip-ok");

        db.execute("PRAGMA foreign_keys = OFF");
        db.execute("DROP TABLE orders");
        db.execute("CREATE TABLE orders (" +
                "id TEXT PRIMARY KEY," +
                "customer_id TEXT NOT NULL," +
                "pickup_line1 TEXT NOT NULL," +
                "pickup_city TEXT NOT NULL," +
                "pickup_state TEXT," +
                "pickup_zip TEXT," +
                "pickup_floor INTEGER," +
                "pickup_floor_notes TEXT," +
                "dropoff_line1 TEXT NOT NULL," +
                "dropoff_city TEXT NOT NULL," +
                "dropoff_state TEXT," +
                "dropoff_zip TEXT," +
                "dropoff_floor INTEGER," +
                "dropoff_floor_notes TEXT," +
                "rider_count INTEGER NOT NULL," +
                "window_start TEXT NOT NULL," +
                "window_end TEXT NOT NULL," +
                "vehicle_type TEXT NOT NULL," +
                "priority TEXT NOT NULL," +
                "miles REAL NOT NULL," +
                "duration_minutes INTEGER NOT NULL," +
                "coupon_code TEXT," +
                "created_at TEXT NOT NULL," +
                "state TEXT NOT NULL," +
                "accepted_at TEXT," +
                "started_at TEXT," +
                "completed_at TEXT," +
                "canceled_at TEXT," +
                "disputed_at TEXT," +
                "fare_total_cents INTEGER," +
                "fare_subtotal_cents INTEGER," +
                "fare_deposit_cents INTEGER," +
                "fare_coupon_cents INTEGER," +
                "fare_subsidy_cents INTEGER," +
                "cancel_fee_cents INTEGER NOT NULL DEFAULT 0," +
                "overdue_fee_cents INTEGER NOT NULL DEFAULT 0," +
                "owner_user_id TEXT," +
                "trip_id TEXT," +
                "FOREIGN KEY(customer_id) REFERENCES customers(id))");

        SqliteOrderRepository r = new SqliteOrderRepository(db);
        r.save(buildOrder("o-valid", "trip-ok"));
        r.save(buildOrder("o-orphan", "trip-ghost"));
        db.execute("PRAGMA foreign_keys = ON");

        new Migrations().applyAll(db);

        assertEquals("trip-ok", r.findById("o-valid").orElseThrow().tripId());
        assertNull(r.findById("o-orphan").orElseThrow().tripId(),
                "orphan trip_id must be cleared by the rebuild");

        seedCustomer();
        assertThrows(Database.DbException.class,
                () -> r.save(buildOrder("o-new-orphan", "trip-gone")),
                "rebuilt table must reject fresh orphan inserts");
    }

    @Test
    void idempotentWhenForeignKeyAlreadyPresent() {
        // Fresh DB already has the FK; rerunning the migration must be a no-op and
        // must leave the table fully functional.
        new Migrations().applyAll(db);
        seedCustomer();
        seedTrip("t1");
        SqliteOrderRepository r = new SqliteOrderRepository(db);
        r.save(buildOrder("o1", "t1"));
        assertEquals("t1", r.findById("o1").orElseThrow().tripId());
    }
}
