package com.fleetride.repository.sqlite;

import com.fleetride.domain.ShareLink;
import com.fleetride.repository.ShareLinkRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SqliteShareLinkRepository implements ShareLinkRepository {
    private final Database db;

    public SqliteShareLinkRepository(Database db) { this.db = db; }

    @Override
    public void save(ShareLink l) {
        db.update(
                "INSERT INTO share_links(token, resource_id, machine_id, created_at, expires_at) " +
                        "VALUES(?,?,?,?,?) ON CONFLICT(token) DO NOTHING",
                ps -> {
                    ps.setString(1, l.token());
                    ps.setString(2, l.resourceId());
                    ps.setString(3, l.machineId());
                    ps.setString(4, SqlSupport.dt(l.createdAt()));
                    ps.setString(5, SqlSupport.dt(l.expiresAt()));
                });
    }

    @Override
    public Optional<ShareLink> findByToken(String token) {
        List<ShareLink> rows = db.query("SELECT * FROM share_links WHERE token = ?",
                ps -> ps.setString(1, token),
                rs -> new ShareLink(rs.getString("token"), rs.getString("resource_id"),
                        rs.getString("machine_id"),
                        SqlSupport.parseDt(rs.getString("created_at")),
                        SqlSupport.parseDt(rs.getString("expires_at"))));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void delete(String token) {
        db.update("DELETE FROM share_links WHERE token = ?", ps -> ps.setString(1, token));
    }
}
