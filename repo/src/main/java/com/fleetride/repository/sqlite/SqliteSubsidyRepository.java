package com.fleetride.repository.sqlite;

import com.fleetride.domain.Subsidy;
import com.fleetride.repository.SubsidyRepository;

import java.util.List;
import java.util.Optional;

public final class SqliteSubsidyRepository implements SubsidyRepository {
    private final Database db;

    public SqliteSubsidyRepository(Database db) { this.db = db; }

    @Override
    public void save(Subsidy s) {
        db.update(
                "INSERT INTO subsidies(customer_id, monthly_cap_cents) VALUES(?,?) " +
                        "ON CONFLICT(customer_id) DO UPDATE SET monthly_cap_cents = excluded.monthly_cap_cents",
                ps -> {
                    ps.setString(1, s.customerId());
                    ps.setLong(2, SqlSupport.moneyToCents(s.monthlyCap()));
                });
    }

    @Override
    public Optional<Subsidy> findByCustomer(String customerId) {
        List<Subsidy> rows = db.query("SELECT * FROM subsidies WHERE customer_id = ?",
                ps -> ps.setString(1, customerId),
                rs -> new Subsidy(rs.getString("customer_id"),
                        SqlSupport.centsToMoney(rs.getLong("monthly_cap_cents"))));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Subsidy> findAll() {
        return db.query("SELECT * FROM subsidies ORDER BY customer_id", ps -> {},
                rs -> new Subsidy(rs.getString("customer_id"),
                        SqlSupport.centsToMoney(rs.getLong("monthly_cap_cents"))));
    }

    @Override
    public void delete(String customerId) {
        db.update("DELETE FROM subsidies WHERE customer_id = ?", ps -> ps.setString(1, customerId));
    }
}
