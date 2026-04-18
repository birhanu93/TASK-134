package com.fleetride.repository.sqlite;

import com.fleetride.domain.Dispute;
import com.fleetride.repository.DisputeRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SqliteDisputeRepository implements DisputeRepository {
    private final Database db;

    public SqliteDisputeRepository(Database db) { this.db = db; }

    @Override
    public void save(Dispute d) {
        db.update(
                "INSERT INTO disputes(id, order_id, reason, opened_at, status, resolution, resolved_at) " +
                        "VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET " +
                        "status=excluded.status, resolution=excluded.resolution, resolved_at=excluded.resolved_at",
                ps -> {
                    ps.setString(1, d.id());
                    ps.setString(2, d.orderId());
                    ps.setString(3, d.reason());
                    ps.setString(4, SqlSupport.dt(d.openedAt()));
                    ps.setString(5, d.status().name());
                    ps.setString(6, d.resolution());
                    ps.setString(7, SqlSupport.dt(d.resolvedAt()));
                });
    }

    @Override
    public Optional<Dispute> findById(String id) {
        List<Dispute> rows = db.query("SELECT * FROM disputes WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Dispute> findByOrder(String orderId) {
        return db.query("SELECT * FROM disputes WHERE order_id = ? ORDER BY opened_at",
                ps -> ps.setString(1, orderId), this::map);
    }

    @Override
    public List<Dispute> findAll() {
        return db.query("SELECT * FROM disputes ORDER BY opened_at", ps -> {}, this::map);
    }

    private Dispute map(ResultSet rs) throws SQLException {
        Dispute d = new Dispute(rs.getString("id"), rs.getString("order_id"),
                rs.getString("reason"), SqlSupport.parseDt(rs.getString("opened_at")));
        String status = rs.getString("status");
        String resolvedAt = rs.getString("resolved_at");
        String resolution = rs.getString("resolution");
        if ("RESOLVED".equals(status)) {
            d.resolve(resolution, SqlSupport.parseDt(resolvedAt));
        } else if ("REJECTED".equals(status)) {
            d.reject(resolution, SqlSupport.parseDt(resolvedAt));
        }
        return d;
    }
}
