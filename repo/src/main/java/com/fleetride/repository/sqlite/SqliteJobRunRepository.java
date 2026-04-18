package com.fleetride.repository.sqlite;

import com.fleetride.repository.JobRunRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public final class SqliteJobRunRepository implements JobRunRepository {
    private final Database db;

    public SqliteJobRunRepository(Database db) { this.db = db; }

    @Override
    public void upsert(Record r) {
        db.update(
                "INSERT INTO job_runs(id, job_name, started_at, finished_at, processed, status, message) " +
                        "VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET " +
                        "finished_at=excluded.finished_at, processed=excluded.processed, " +
                        "status=excluded.status, message=excluded.message",
                ps -> {
                    ps.setString(1, r.id());
                    ps.setString(2, r.jobName());
                    ps.setString(3, SqlSupport.dt(r.startedAt()));
                    ps.setString(4, SqlSupport.dt(r.finishedAt()));
                    if (r.processed() == null) ps.setNull(5, Types.INTEGER);
                    else ps.setInt(5, r.processed());
                    ps.setString(6, r.status().name());
                    ps.setString(7, r.message());
                });
    }

    @Override
    public Optional<Record> find(String id) {
        List<Record> rows = db.query("SELECT * FROM job_runs WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Record> findRecent(int limit) {
        return db.query("SELECT * FROM job_runs ORDER BY started_at DESC LIMIT ?",
                ps -> ps.setInt(1, limit), this::map);
    }

    @Override
    public List<Record> findByStatus(Status status) {
        return db.query("SELECT * FROM job_runs WHERE status = ? ORDER BY started_at DESC",
                ps -> ps.setString(1, status.name()), this::map);
    }

    private Record map(ResultSet rs) throws SQLException {
        return new Record(rs.getString("id"), rs.getString("job_name"),
                SqlSupport.parseDt(rs.getString("started_at")),
                SqlSupport.parseDt(rs.getString("finished_at")),
                SqlSupport.getNullableInt(rs, "processed"),
                Status.valueOf(rs.getString("status")),
                rs.getString("message"));
    }
}
