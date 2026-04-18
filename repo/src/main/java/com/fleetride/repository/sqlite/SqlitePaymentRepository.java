package com.fleetride.repository.sqlite;

import com.fleetride.domain.Payment;
import com.fleetride.repository.PaymentRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SqlitePaymentRepository implements PaymentRepository {
    private final Database db;

    public SqlitePaymentRepository(Database db) { this.db = db; }

    @Override
    public void save(Payment p) {
        db.update(
                "INSERT INTO payments(id, order_id, tender, kind, amount_cents, recorded_at, notes) " +
                        "VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
                ps -> {
                    ps.setString(1, p.id());
                    ps.setString(2, p.orderId());
                    ps.setString(3, p.tender().name());
                    ps.setString(4, p.kind().name());
                    ps.setLong(5, SqlSupport.moneyToCents(p.amount()));
                    ps.setString(6, SqlSupport.dt(p.recordedAt()));
                    ps.setString(7, p.notes());
                });
    }

    @Override
    public Optional<Payment> findById(String id) {
        List<Payment> rows = db.query("SELECT * FROM payments WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Payment> findByOrder(String orderId) {
        return db.query("SELECT * FROM payments WHERE order_id = ? ORDER BY recorded_at",
                ps -> ps.setString(1, orderId), this::map);
    }

    @Override
    public List<Payment> findAll() {
        return db.query("SELECT * FROM payments ORDER BY recorded_at", ps -> {}, this::map);
    }

    private Payment map(ResultSet rs) throws SQLException {
        long cents = rs.getLong("amount_cents");
        return new Payment(rs.getString("id"), rs.getString("order_id"),
                Payment.Tender.valueOf(rs.getString("tender")),
                Payment.Kind.valueOf(rs.getString("kind")),
                new com.fleetride.domain.Money(BigDecimal.valueOf(cents).movePointLeft(2)),
                SqlSupport.parseDt(rs.getString("recorded_at")),
                rs.getString("notes"));
    }
}
