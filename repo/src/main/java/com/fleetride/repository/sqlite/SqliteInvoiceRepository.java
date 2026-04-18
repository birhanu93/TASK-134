package com.fleetride.repository.sqlite;

import com.fleetride.domain.Invoice;
import com.fleetride.repository.InvoiceRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SqliteInvoiceRepository implements InvoiceRepository {
    private final Database db;

    public SqliteInvoiceRepository(Database db) { this.db = db; }

    @Override
    public void save(Invoice i) {
        db.update(
                "INSERT INTO invoices(id, order_id, customer_id, amount_cents, status, issued_at, paid_at, canceled_at, notes) " +
                        "VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET " +
                        "status = excluded.status, paid_at = excluded.paid_at, " +
                        "canceled_at = excluded.canceled_at, notes = excluded.notes",
                ps -> {
                    ps.setString(1, i.id());
                    ps.setString(2, i.orderId());
                    ps.setString(3, i.customerId());
                    ps.setLong(4, SqlSupport.moneyToCents(i.amount()));
                    ps.setString(5, i.status().name());
                    ps.setString(6, SqlSupport.dt(i.issuedAt()));
                    ps.setString(7, SqlSupport.dt(i.paidAt()));
                    ps.setString(8, SqlSupport.dt(i.canceledAt()));
                    ps.setString(9, i.notes());
                });
    }

    @Override
    public Optional<Invoice> findById(String id) {
        List<Invoice> rows = db.query("SELECT * FROM invoices WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Invoice> findByOrder(String orderId) {
        return db.query("SELECT * FROM invoices WHERE order_id = ? ORDER BY issued_at",
                ps -> ps.setString(1, orderId), this::map);
    }

    @Override
    public List<Invoice> findByStatus(Invoice.Status status) {
        return db.query("SELECT * FROM invoices WHERE status = ? ORDER BY issued_at",
                ps -> ps.setString(1, status.name()), this::map);
    }

    @Override
    public List<Invoice> findAll() {
        return db.query("SELECT * FROM invoices ORDER BY issued_at", ps -> {}, this::map);
    }

    private Invoice map(ResultSet rs) throws SQLException {
        Invoice i = new Invoice(rs.getString("id"), rs.getString("order_id"),
                rs.getString("customer_id"),
                SqlSupport.centsToMoney(rs.getLong("amount_cents")),
                SqlSupport.parseDt(rs.getString("issued_at")),
                rs.getString("notes"));
        Invoice.Status status = Invoice.Status.valueOf(rs.getString("status"));
        if (status == Invoice.Status.PAID) {
            i.restorePaid(SqlSupport.parseDt(rs.getString("paid_at")));
        } else if (status == Invoice.Status.CANCELED) {
            i.restoreCanceled(SqlSupport.parseDt(rs.getString("canceled_at")));
        }
        return i;
    }
}
