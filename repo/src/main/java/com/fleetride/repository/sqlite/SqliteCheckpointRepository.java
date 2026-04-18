package com.fleetride.repository.sqlite;

import com.fleetride.repository.CheckpointRepository;

import java.util.List;
import java.util.Optional;

public final class SqliteCheckpointRepository implements CheckpointRepository {
    private final Database db;

    public SqliteCheckpointRepository(Database db) { this.db = db; }

    @Override
    public void upsert(Record r) {
        db.update(
                "INSERT INTO checkpoints(operation_id, detail, status, created_at) VALUES(?,?,?,?) " +
                        "ON CONFLICT(operation_id) DO UPDATE SET detail=excluded.detail, " +
                        "status=excluded.status, created_at=excluded.created_at",
                ps -> {
                    ps.setString(1, r.operationId());
                    ps.setString(2, r.detail());
                    ps.setString(3, r.status().name());
                    ps.setString(4, SqlSupport.dt(r.createdAt()));
                });
    }

    @Override
    public Optional<Record> find(String operationId) {
        List<Record> rows = db.query("SELECT * FROM checkpoints WHERE operation_id = ?",
                ps -> ps.setString(1, operationId), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Record> findPending() {
        return db.query("SELECT * FROM checkpoints WHERE status = 'PENDING' ORDER BY created_at",
                ps -> {}, this::map);
    }

    @Override
    public void delete(String operationId) {
        db.update("DELETE FROM checkpoints WHERE operation_id = ?", ps -> ps.setString(1, operationId));
    }

    private Record map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Record(rs.getString("operation_id"), rs.getString("detail"),
                Status.valueOf(rs.getString("status")),
                SqlSupport.parseDt(rs.getString("created_at")));
    }
}
