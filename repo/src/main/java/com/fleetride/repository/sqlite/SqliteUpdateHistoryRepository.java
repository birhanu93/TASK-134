package com.fleetride.repository.sqlite;

import com.fleetride.repository.UpdateHistoryRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class SqliteUpdateHistoryRepository implements UpdateHistoryRepository {
    private static final String DEFAULT_VERSION = "1.0.0";

    private final Database db;

    public SqliteUpdateHistoryRepository(Database db) {
        this.db = db;
        db.update("INSERT OR IGNORE INTO update_state(id, current_version) VALUES(1, ?)",
                ps -> ps.setString(1, DEFAULT_VERSION));
    }

    @Override
    public long append(String version, Path packagePath, LocalDateTime installedAt) {
        db.update("INSERT INTO update_history(version, package_path, installed_at) VALUES(?,?,?)",
                ps -> {
                    ps.setString(1, version);
                    ps.setString(2, packagePath.toString());
                    ps.setString(3, SqlSupport.dt(installedAt));
                });
        List<Long> seq = db.query("SELECT seq FROM update_history ORDER BY seq DESC LIMIT 1",
                ps -> {}, rs -> rs.getLong("seq"));
        return seq.get(0);
    }

    @Override
    public Optional<Record> peekLatest() {
        List<Record> rows = db.query(
                "SELECT seq, version, package_path, installed_at FROM update_history " +
                        "ORDER BY seq DESC LIMIT 1",
                ps -> {}, this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Record> listAll() {
        return db.query(
                "SELECT seq, version, package_path, installed_at FROM update_history ORDER BY seq",
                ps -> {}, this::map);
    }

    @Override
    public void deleteBySeq(long seq) {
        db.update("DELETE FROM update_history WHERE seq = ?", ps -> ps.setLong(1, seq));
    }

    @Override
    public String currentVersion() {
        List<String> rows = db.query("SELECT current_version FROM update_state WHERE id = 1",
                ps -> {}, rs -> rs.getString("current_version"));
        return rows.get(0);
    }

    @Override
    public void setCurrentVersion(String version) {
        db.update("UPDATE update_state SET current_version = ? WHERE id = 1",
                ps -> ps.setString(1, version));
    }

    private Record map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Record(rs.getLong("seq"), rs.getString("version"),
                Path.of(rs.getString("package_path")),
                SqlSupport.parseDt(rs.getString("installed_at")));
    }
}
