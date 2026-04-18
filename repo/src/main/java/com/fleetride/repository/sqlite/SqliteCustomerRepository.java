package com.fleetride.repository.sqlite;

import com.fleetride.domain.Customer;
import com.fleetride.repository.CustomerRepository;

import java.util.List;
import java.util.Optional;

public final class SqliteCustomerRepository implements CustomerRepository {
    private final Database db;

    public SqliteCustomerRepository(Database db) { this.db = db; }

    @Override
    public void save(Customer c) {
        db.update(
                "INSERT INTO customers(id, name, phone, encrypted_payment_token, subsidy_used_cents, owner_user_id, " +
                        "monthly_ride_quota, monthly_rides_used) " +
                        "VALUES(?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(id) DO UPDATE SET name=excluded.name, phone=excluded.phone, " +
                        "encrypted_payment_token=excluded.encrypted_payment_token, " +
                        "subsidy_used_cents=excluded.subsidy_used_cents, " +
                        "owner_user_id=excluded.owner_user_id, " +
                        "monthly_ride_quota=excluded.monthly_ride_quota, " +
                        "monthly_rides_used=excluded.monthly_rides_used",
                ps -> {
                    ps.setString(1, c.id());
                    ps.setString(2, c.name());
                    ps.setString(3, c.phone());
                    ps.setString(4, c.encryptedPaymentToken());
                    ps.setLong(5, SqlSupport.moneyToCents(c.subsidyUsedThisMonth()));
                    ps.setString(6, c.ownerUserId());
                    ps.setInt(7, c.monthlyRideQuota());
                    ps.setInt(8, c.monthlyRidesUsed());
                });
    }

    @Override
    public Optional<Customer> findById(String id) {
        List<Customer> rows = db.query("SELECT * FROM customers WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Customer> findAll() {
        return db.query("SELECT * FROM customers ORDER BY name", ps -> {}, this::map);
    }

    public List<Customer> findByOwner(String ownerUserId) {
        return db.query("SELECT * FROM customers WHERE owner_user_id = ? ORDER BY name",
                ps -> ps.setString(1, ownerUserId), this::map);
    }

    @Override
    public void delete(String id) {
        db.update("DELETE FROM customers WHERE id = ?", ps -> ps.setString(1, id));
    }

    private Customer map(java.sql.ResultSet rs) throws java.sql.SQLException {
        int quota = rs.getInt("monthly_ride_quota");
        if (rs.wasNull()) quota = Customer.DEFAULT_MONTHLY_RIDE_QUOTA;
        int used = rs.getInt("monthly_rides_used");
        if (rs.wasNull()) used = 0;
        return new Customer(rs.getString("id"), rs.getString("name"), rs.getString("phone"),
                rs.getString("encrypted_payment_token"),
                SqlSupport.centsToMoney(rs.getLong("subsidy_used_cents")),
                rs.getString("owner_user_id"),
                quota, used);
    }
}
