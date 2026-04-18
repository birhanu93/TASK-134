package com.fleetride.repository.sqlite;

import com.fleetride.domain.Attachment;
import com.fleetride.repository.AttachmentRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SqliteAttachmentRepository implements AttachmentRepository {
    private final Database db;

    public SqliteAttachmentRepository(Database db) { this.db = db; }

    @Override
    public void save(Attachment a) {
        db.update(
                "INSERT INTO attachments(id, order_id, filename, stored_path, mime_type, size_bytes, sha256, uploaded_at) " +
                        "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
                ps -> {
                    ps.setString(1, a.id());
                    ps.setString(2, a.orderId());
                    ps.setString(3, a.filename());
                    ps.setString(4, a.storedPath());
                    ps.setString(5, a.mimeType());
                    ps.setLong(6, a.sizeBytes());
                    ps.setString(7, a.sha256());
                    ps.setString(8, SqlSupport.dt(a.uploadedAt()));
                });
    }

    @Override
    public Optional<Attachment> findById(String id) {
        List<Attachment> rows = db.query("SELECT * FROM attachments WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Attachment> findByOrder(String orderId) {
        return db.query("SELECT * FROM attachments WHERE order_id = ? ORDER BY uploaded_at",
                ps -> ps.setString(1, orderId), this::map);
    }

    @Override
    public void delete(String id) {
        db.update("DELETE FROM attachments WHERE id = ?", ps -> ps.setString(1, id));
    }

    private Attachment map(ResultSet rs) throws SQLException {
        return new Attachment(rs.getString("id"), rs.getString("order_id"),
                rs.getString("filename"), rs.getString("stored_path"),
                rs.getString("mime_type"), rs.getLong("size_bytes"),
                rs.getString("sha256"), SqlSupport.parseDt(rs.getString("uploaded_at")));
    }
}
