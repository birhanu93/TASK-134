package com.fleetride.repository.sqlite;

import com.fleetride.domain.Address;
import com.fleetride.domain.Fare;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.OrderRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public final class SqliteOrderRepository implements OrderRepository {
    private final Database db;

    public SqliteOrderRepository(Database db) { this.db = db; }

    @Override
    public void save(Order o) {
        db.update(
                "INSERT INTO orders(id, customer_id, pickup_line1, pickup_city, pickup_state, pickup_zip, pickup_floor, pickup_floor_notes, " +
                        "dropoff_line1, dropoff_city, dropoff_state, dropoff_zip, dropoff_floor, dropoff_floor_notes, " +
                        "rider_count, window_start, window_end, vehicle_type, priority, miles, duration_minutes, coupon_code, " +
                        "created_at, state, accepted_at, started_at, completed_at, canceled_at, disputed_at, " +
                        "fare_total_cents, fare_subtotal_cents, fare_deposit_cents, fare_coupon_cents, fare_subsidy_cents, " +
                        "cancel_fee_cents, overdue_fee_cents, owner_user_id, trip_id) VALUES " +
                        "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(id) DO UPDATE SET " +
                        "state=excluded.state, accepted_at=excluded.accepted_at, started_at=excluded.started_at, " +
                        "completed_at=excluded.completed_at, canceled_at=excluded.canceled_at, disputed_at=excluded.disputed_at, " +
                        "fare_total_cents=excluded.fare_total_cents, fare_subtotal_cents=excluded.fare_subtotal_cents, " +
                        "fare_deposit_cents=excluded.fare_deposit_cents, fare_coupon_cents=excluded.fare_coupon_cents, " +
                        "fare_subsidy_cents=excluded.fare_subsidy_cents, cancel_fee_cents=excluded.cancel_fee_cents, " +
                        "overdue_fee_cents=excluded.overdue_fee_cents, owner_user_id=excluded.owner_user_id, " +
                        "pickup_floor_notes=excluded.pickup_floor_notes, dropoff_floor_notes=excluded.dropoff_floor_notes, " +
                        "trip_id=excluded.trip_id",
                ps -> {
                    int i = 1;
                    ps.setString(i++, o.id());
                    ps.setString(i++, o.customerId());
                    ps.setString(i++, o.pickup().line1());
                    ps.setString(i++, o.pickup().city());
                    ps.setString(i++, o.pickup().state());
                    ps.setString(i++, o.pickup().zip());
                    setNullableInt(ps, i++, o.pickup().floor());
                    ps.setString(i++, o.pickupFloorNotes());
                    ps.setString(i++, o.dropoff().line1());
                    ps.setString(i++, o.dropoff().city());
                    ps.setString(i++, o.dropoff().state());
                    ps.setString(i++, o.dropoff().zip());
                    setNullableInt(ps, i++, o.dropoff().floor());
                    ps.setString(i++, o.dropoffFloorNotes());
                    ps.setInt(i++, o.riderCount());
                    ps.setString(i++, SqlSupport.dt(o.window().start()));
                    ps.setString(i++, SqlSupport.dt(o.window().end()));
                    ps.setString(i++, o.vehicleType().name());
                    ps.setString(i++, o.priority().name());
                    ps.setDouble(i++, o.miles());
                    ps.setInt(i++, o.durationMinutes());
                    ps.setString(i++, o.couponCode());
                    ps.setString(i++, SqlSupport.dt(o.createdAt()));
                    ps.setString(i++, o.state().name());
                    ps.setString(i++, SqlSupport.dt(o.acceptedAt()));
                    ps.setString(i++, SqlSupport.dt(o.startedAt()));
                    ps.setString(i++, SqlSupport.dt(o.completedAt()));
                    ps.setString(i++, SqlSupport.dt(o.canceledAt()));
                    ps.setString(i++, SqlSupport.dt(o.disputedAt()));
                    setNullableCents(ps, i++, o.fare() == null ? null : o.fare().total());
                    setNullableCents(ps, i++, o.fare() == null ? null : o.fare().subtotal());
                    setNullableCents(ps, i++, o.fare() == null ? null : o.fare().deposit());
                    setNullableCents(ps, i++, o.fare() == null ? null : o.fare().couponDiscount());
                    setNullableCents(ps, i++, o.fare() == null ? null : o.fare().subsidyApplied());
                    ps.setLong(i++, SqlSupport.moneyToCents(o.cancellationFee()));
                    ps.setLong(i++, SqlSupport.moneyToCents(o.overdueFee()));
                    ps.setString(i++, o.ownerUserId());
                    ps.setString(i, o.tripId());
                });
    }

    @Override
    public Optional<Order> findById(String id) {
        List<Order> rows = db.query("SELECT * FROM orders WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Order> findByCustomer(String customerId) {
        return db.query("SELECT * FROM orders WHERE customer_id = ? ORDER BY created_at",
                ps -> ps.setString(1, customerId), this::map);
    }

    @Override
    public List<Order> findByState(OrderState state) {
        return db.query("SELECT * FROM orders WHERE state = ? ORDER BY created_at",
                ps -> ps.setString(1, state.name()), this::map);
    }

    public List<Order> findByOwner(String ownerUserId) {
        return db.query("SELECT * FROM orders WHERE owner_user_id = ? ORDER BY created_at",
                ps -> ps.setString(1, ownerUserId), this::map);
    }

    @Override
    public List<Order> findAll() {
        return db.query("SELECT * FROM orders ORDER BY created_at", ps -> {}, this::map);
    }

    @Override
    public void delete(String id) {
        db.update("DELETE FROM orders WHERE id = ?", ps -> ps.setString(1, id));
    }

    private Order map(ResultSet rs) throws SQLException {
        Address pickup = new Address(rs.getString("pickup_line1"), rs.getString("pickup_city"),
                rs.getString("pickup_state"), rs.getString("pickup_zip"),
                SqlSupport.getNullableInt(rs, "pickup_floor"));
        Address dropoff = new Address(rs.getString("dropoff_line1"), rs.getString("dropoff_city"),
                rs.getString("dropoff_state"), rs.getString("dropoff_zip"),
                SqlSupport.getNullableInt(rs, "dropoff_floor"));
        TimeWindow window = new TimeWindow(
                SqlSupport.parseDt(rs.getString("window_start")),
                SqlSupport.parseDt(rs.getString("window_end")));
        Order o = new Order(rs.getString("id"), rs.getString("customer_id"), pickup, dropoff,
                rs.getInt("rider_count"), window, VehicleType.valueOf(rs.getString("vehicle_type")),
                ServicePriority.valueOf(rs.getString("priority")), rs.getDouble("miles"),
                rs.getInt("duration_minutes"), rs.getString("coupon_code"),
                SqlSupport.parseDt(rs.getString("created_at")));
        o.setState(OrderState.valueOf(rs.getString("state")));
        o.setAcceptedAt(SqlSupport.parseDt(rs.getString("accepted_at")));
        o.setStartedAt(SqlSupport.parseDt(rs.getString("started_at")));
        o.setCompletedAt(SqlSupport.parseDt(rs.getString("completed_at")));
        o.setCanceledAt(SqlSupport.parseDt(rs.getString("canceled_at")));
        o.setDisputedAt(SqlSupport.parseDt(rs.getString("disputed_at")));
        Long total = SqlSupport.getNullableLong(rs, "fare_total_cents");
        if (total != null) {
            Money tot = SqlSupport.centsToMoney(total);
            Money sub = SqlSupport.centsToMoney(rs.getLong("fare_subtotal_cents"));
            Money dep = SqlSupport.centsToMoney(rs.getLong("fare_deposit_cents"));
            Money cou = SqlSupport.centsToMoney(rs.getLong("fare_coupon_cents"));
            Money sbs = SqlSupport.centsToMoney(rs.getLong("fare_subsidy_cents"));
            o.setFare(new Fare(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
                    cou, sbs, sub, tot, dep));
        }
        o.setCancellationFee(SqlSupport.centsToMoney(rs.getLong("cancel_fee_cents")));
        o.setOverdueFee(SqlSupport.centsToMoney(rs.getLong("overdue_fee_cents")));
        o.setOwnerUserId(rs.getString("owner_user_id"));
        o.setPickupFloorNotes(rs.getString("pickup_floor_notes"));
        o.setDropoffFloorNotes(rs.getString("dropoff_floor_notes"));
        o.setTripId(rs.getString("trip_id"));
        return o;
    }

    public List<Order> findByTrip(String tripId) {
        return db.query("SELECT * FROM orders WHERE trip_id = ? ORDER BY created_at",
                ps -> ps.setString(1, tripId), this::map);
    }

    private static void setNullableInt(java.sql.PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, v);
    }

    private static void setNullableCents(java.sql.PreparedStatement ps, int idx, Money m) throws SQLException {
        if (m == null) ps.setNull(idx, Types.BIGINT);
        else ps.setLong(idx, SqlSupport.moneyToCents(m));
    }
}
