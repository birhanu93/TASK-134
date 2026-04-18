package com.fleetride.repository.sqlite;

import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.Trip;
import com.fleetride.domain.TripStatus;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.TripRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SqliteTripRepository implements TripRepository {
    private final Database db;

    public SqliteTripRepository(Database db) { this.db = db; }

    @Override
    public void save(Trip t) {
        db.update(
                "INSERT INTO trips(id, vehicle_type, capacity, window_start, window_end, created_at, " +
                        "driver_placeholder, status, owner_user_id, dispatched_at, closed_at, canceled_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(id) DO UPDATE SET " +
                        "driver_placeholder=excluded.driver_placeholder, status=excluded.status, " +
                        "owner_user_id=excluded.owner_user_id, dispatched_at=excluded.dispatched_at, " +
                        "closed_at=excluded.closed_at, canceled_at=excluded.canceled_at",
                ps -> {
                    int i = 1;
                    ps.setString(i++, t.id());
                    ps.setString(i++, t.vehicleType().name());
                    ps.setInt(i++, t.capacity());
                    ps.setString(i++, SqlSupport.dt(t.scheduledWindow().start()));
                    ps.setString(i++, SqlSupport.dt(t.scheduledWindow().end()));
                    ps.setString(i++, SqlSupport.dt(t.createdAt()));
                    ps.setString(i++, t.driverPlaceholder());
                    ps.setString(i++, t.status().name());
                    ps.setString(i++, t.ownerUserId());
                    ps.setString(i++, SqlSupport.dt(t.dispatchedAt()));
                    ps.setString(i++, SqlSupport.dt(t.closedAt()));
                    ps.setString(i, SqlSupport.dt(t.canceledAt()));
                });
    }

    @Override
    public Optional<Trip> findById(String id) {
        List<Trip> rows = db.query("SELECT * FROM trips WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Trip> findAll() {
        return db.query("SELECT * FROM trips ORDER BY created_at", ps -> {}, this::map);
    }

    @Override
    public List<Trip> findByStatus(TripStatus status) {
        return db.query("SELECT * FROM trips WHERE status = ? ORDER BY created_at",
                ps -> ps.setString(1, status.name()), this::map);
    }

    @Override
    public List<Trip> findByOwner(String ownerUserId) {
        return db.query("SELECT * FROM trips WHERE owner_user_id = ? ORDER BY created_at",
                ps -> ps.setString(1, ownerUserId), this::map);
    }

    @Override
    public void delete(String id) {
        db.update("DELETE FROM trips WHERE id = ?", ps -> ps.setString(1, id));
    }

    private Trip map(ResultSet rs) throws SQLException {
        TimeWindow window = new TimeWindow(
                SqlSupport.parseDt(rs.getString("window_start")),
                SqlSupport.parseDt(rs.getString("window_end")));
        Trip t = new Trip(rs.getString("id"),
                VehicleType.valueOf(rs.getString("vehicle_type")),
                rs.getInt("capacity"),
                window,
                SqlSupport.parseDt(rs.getString("created_at")));
        t.setDriverPlaceholder(rs.getString("driver_placeholder"));
        t.setStatus(TripStatus.valueOf(rs.getString("status")));
        t.setOwnerUserId(rs.getString("owner_user_id"));
        t.setDispatchedAt(SqlSupport.parseDt(rs.getString("dispatched_at")));
        t.setClosedAt(SqlSupport.parseDt(rs.getString("closed_at")));
        t.setCanceledAt(SqlSupport.parseDt(rs.getString("canceled_at")));
        return t;
    }
}
