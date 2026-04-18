package com.fleetride.repository.sqlite;

import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Migrations {
    static final List<String> ORDERS_COLUMNS = List.of(
            "id", "customer_id",
            "pickup_line1", "pickup_city", "pickup_state", "pickup_zip",
            "pickup_floor", "pickup_floor_notes",
            "dropoff_line1", "dropoff_city", "dropoff_state", "dropoff_zip",
            "dropoff_floor", "dropoff_floor_notes",
            "rider_count", "window_start", "window_end",
            "vehicle_type", "priority", "miles", "duration_minutes",
            "coupon_code", "created_at", "state",
            "accepted_at", "started_at", "completed_at", "canceled_at", "disputed_at",
            "fare_total_cents", "fare_subtotal_cents", "fare_deposit_cents",
            "fare_coupon_cents", "fare_subsidy_cents",
            "cancel_fee_cents", "overdue_fee_cents",
            "owner_user_id", "trip_id"
    );

    static final String ORDERS_TABLE_DDL =
            "CREATE TABLE IF NOT EXISTS orders (" +
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
                    "FOREIGN KEY(customer_id) REFERENCES customers(id)," +
                    "FOREIGN KEY(trip_id) REFERENCES trips(id) ON DELETE SET NULL)";

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS users (" +
                    "id TEXT PRIMARY KEY," +
                    "username TEXT UNIQUE NOT NULL," +
                    "encrypted_password TEXT NOT NULL," +
                    "role TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS customers (" +
                    "id TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "phone TEXT NOT NULL," +
                    "encrypted_payment_token TEXT," +
                    "subsidy_used_cents INTEGER NOT NULL DEFAULT 0," +
                    "owner_user_id TEXT," +
                    "monthly_ride_quota INTEGER NOT NULL DEFAULT 60," +
                    "monthly_rides_used INTEGER NOT NULL DEFAULT 0)",
            ORDERS_TABLE_DDL,
            "CREATE TABLE IF NOT EXISTS trips (" +
                    "id TEXT PRIMARY KEY," +
                    "vehicle_type TEXT NOT NULL," +
                    "capacity INTEGER NOT NULL," +
                    "window_start TEXT NOT NULL," +
                    "window_end TEXT NOT NULL," +
                    "created_at TEXT NOT NULL," +
                    "driver_placeholder TEXT," +
                    "status TEXT NOT NULL," +
                    "owner_user_id TEXT," +
                    "dispatched_at TEXT," +
                    "closed_at TEXT," +
                    "canceled_at TEXT)",
            "CREATE TABLE IF NOT EXISTS payments (" +
                    "id TEXT PRIMARY KEY," +
                    "order_id TEXT NOT NULL," +
                    "tender TEXT NOT NULL," +
                    "kind TEXT NOT NULL," +
                    "amount_cents INTEGER NOT NULL," +
                    "recorded_at TEXT NOT NULL," +
                    "notes TEXT," +
                    "FOREIGN KEY(order_id) REFERENCES orders(id))",
            "CREATE TABLE IF NOT EXISTS attachments (" +
                    "id TEXT PRIMARY KEY," +
                    "order_id TEXT NOT NULL," +
                    "filename TEXT NOT NULL," +
                    "stored_path TEXT NOT NULL," +
                    "mime_type TEXT NOT NULL," +
                    "size_bytes INTEGER NOT NULL," +
                    "sha256 TEXT NOT NULL," +
                    "uploaded_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS disputes (" +
                    "id TEXT PRIMARY KEY," +
                    "order_id TEXT NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "opened_at TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "resolution TEXT," +
                    "resolved_at TEXT," +
                    "FOREIGN KEY(order_id) REFERENCES orders(id))",
            "CREATE TABLE IF NOT EXISTS audit_events (" +
                    "id TEXT PRIMARY KEY," +
                    "actor TEXT NOT NULL," +
                    "action TEXT NOT NULL," +
                    "target TEXT," +
                    "details TEXT," +
                    "at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS share_links (" +
                    "token TEXT PRIMARY KEY," +
                    "resource_id TEXT NOT NULL," +
                    "machine_id TEXT NOT NULL," +
                    "created_at TEXT NOT NULL," +
                    "expires_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS checkpoints (" +
                    "operation_id TEXT PRIMARY KEY," +
                    "detail TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "created_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS job_runs (" +
                    "id TEXT PRIMARY KEY," +
                    "job_name TEXT NOT NULL," +
                    "started_at TEXT NOT NULL," +
                    "finished_at TEXT," +
                    "processed INTEGER," +
                    "status TEXT NOT NULL," +
                    "message TEXT)",
            "CREATE TABLE IF NOT EXISTS update_history (" +
                    "seq INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "version TEXT NOT NULL," +
                    "package_path TEXT NOT NULL," +
                    "installed_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS update_state (" +
                    "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                    "current_version TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS settings (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT)",
            "CREATE TABLE IF NOT EXISTS dictionaries (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT)",
            "CREATE TABLE IF NOT EXISTS templates (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT)",
            "CREATE TABLE IF NOT EXISTS invoices (" +
                    "id TEXT PRIMARY KEY," +
                    "order_id TEXT NOT NULL," +
                    "customer_id TEXT NOT NULL," +
                    "amount_cents INTEGER NOT NULL," +
                    "status TEXT NOT NULL," +
                    "issued_at TEXT NOT NULL," +
                    "paid_at TEXT," +
                    "canceled_at TEXT," +
                    "notes TEXT)",
            "CREATE TABLE IF NOT EXISTS coupons (" +
                    "code TEXT PRIMARY KEY," +
                    "type TEXT NOT NULL," +
                    "percent_bp INTEGER," +
                    "fixed_cents INTEGER," +
                    "minimum_order_cents INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS subsidies (" +
                    "customer_id TEXT PRIMARY KEY," +
                    "monthly_cap_cents INTEGER NOT NULL)"
    );

    public void applyAll(Database db) {
        for (String ddl : DDL) {
            db.execute(ddl);
        }
        addColumnIfMissing(db, "orders", "pickup_floor_notes", "TEXT");
        addColumnIfMissing(db, "orders", "dropoff_floor_notes", "TEXT");
        addColumnIfMissing(db, "orders", "trip_id", "TEXT");
        addColumnIfMissing(db, "customers", "monthly_ride_quota", "INTEGER NOT NULL DEFAULT 60");
        addColumnIfMissing(db, "customers", "monthly_rides_used", "INTEGER NOT NULL DEFAULT 0");
        renameColumnIfPresent(db, "users", "password_hash", "encrypted_password");
        ensureOrdersTripFk(db);
    }

    // Older databases created the orders table before trip_id existed and added the column
    // via ALTER TABLE — SQLite cannot add a foreign key through ALTER, so the link was
    // unenforced. Detect and rebuild the table so orders.trip_id references trips(id). Any
    // trip_id pointing at a now-missing trip is cleared (ON DELETE SET NULL semantics) so
    // the integrity check passes on the rebuilt table.
    static void ensureOrdersTripFk(Database db) {
        if (hasForeignKey(db, "orders", "trip_id", "trips")) return;
        db.execute("PRAGMA foreign_keys = OFF");
        try {
            db.execute(
                    "UPDATE orders SET trip_id = NULL " +
                            "WHERE trip_id IS NOT NULL " +
                            "AND trip_id NOT IN (SELECT id FROM trips)");
            db.execute(ORDERS_TABLE_DDL.replace(
                    "CREATE TABLE IF NOT EXISTS orders (",
                    "CREATE TABLE orders__new ("));
            String cols = String.join(", ", ORDERS_COLUMNS);
            db.execute("INSERT INTO orders__new (" + cols + ") SELECT " + cols + " FROM orders");
            db.execute("DROP TABLE orders");
            db.execute("ALTER TABLE orders__new RENAME TO orders");
        } finally {
            db.execute("PRAGMA foreign_keys = ON");
        }
    }

    private static boolean hasForeignKey(Database db, String table, String from, String refTable) {
        List<String> fks = db.query(
                "PRAGMA foreign_key_list(" + table + ")",
                ps -> {},
                (ResultSet rs) -> rs.getString("table") + ":" + rs.getString("from"));
        return fks.contains(refTable + ":" + from);
    }

    private static void addColumnIfMissing(Database db, String table, String column, String type) {
        if (hasColumn(db, table, column)) return;
        db.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }

    private static void renameColumnIfPresent(Database db, String table, String from, String to) {
        if (!hasColumn(db, table, from)) return;
        if (hasColumn(db, table, to)) return;
        db.execute("ALTER TABLE " + table + " RENAME COLUMN " + from + " TO " + to);
    }

    private static boolean hasColumn(Database db, String table, String column) {
        Set<String> cols = new HashSet<>(db.query(
                "PRAGMA table_info(" + table + ")",
                ps -> {},
                (ResultSet rs) -> rs.getString("name")));
        return cols.contains(column);
    }

}
