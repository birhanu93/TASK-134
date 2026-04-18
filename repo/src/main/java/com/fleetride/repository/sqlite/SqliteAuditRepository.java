package com.fleetride.repository.sqlite;

import com.fleetride.domain.AuditEvent;
import com.fleetride.repository.AuditRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class SqliteAuditRepository implements AuditRepository {
    private final Database db;

    public SqliteAuditRepository(Database db) { this.db = db; }

    @Override
    public void save(AuditEvent e) {
        db.update(
                "INSERT INTO audit_events(id, actor, action, target, details, at) VALUES(?,?,?,?,?,?)",
                ps -> {
                    ps.setString(1, e.id());
                    ps.setString(2, e.actor());
                    ps.setString(3, e.action());
                    ps.setString(4, e.target());
                    ps.setString(5, e.details());
                    ps.setString(6, SqlSupport.dt(e.at()));
                });
    }

    @Override
    public List<AuditEvent> findAll() {
        return db.query("SELECT * FROM audit_events ORDER BY at", ps -> {}, this::map);
    }

    private AuditEvent map(ResultSet rs) throws SQLException {
        return new AuditEvent(rs.getString("id"), rs.getString("actor"),
                rs.getString("action"), rs.getString("target"), rs.getString("details"),
                SqlSupport.parseDt(rs.getString("at")));
    }
}
