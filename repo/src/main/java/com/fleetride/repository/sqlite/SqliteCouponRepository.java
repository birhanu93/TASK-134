package com.fleetride.repository.sqlite;

import com.fleetride.domain.Coupon;
import com.fleetride.domain.Money;
import com.fleetride.repository.CouponRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public final class SqliteCouponRepository implements CouponRepository {
    private final Database db;

    public SqliteCouponRepository(Database db) { this.db = db; }

    @Override
    public void save(Coupon c) {
        db.update(
                "INSERT INTO coupons(code, type, percent_bp, fixed_cents, minimum_order_cents) " +
                        "VALUES(?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET " +
                        "type=excluded.type, percent_bp=excluded.percent_bp, " +
                        "fixed_cents=excluded.fixed_cents, minimum_order_cents=excluded.minimum_order_cents",
                ps -> {
                    ps.setString(1, c.code());
                    ps.setString(2, c.type().name());
                    if (c.type() == Coupon.Type.PERCENT) {
                        ps.setLong(3, c.percent().multiply(BigDecimal.valueOf(10000)).longValueExact());
                        ps.setNull(4, Types.BIGINT);
                    } else {
                        ps.setNull(3, Types.BIGINT);
                        ps.setLong(4, SqlSupport.moneyToCents(c.fixedAmount()));
                    }
                    ps.setLong(5, SqlSupport.moneyToCents(c.minimumOrder()));
                });
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        List<Coupon> rows = db.query("SELECT * FROM coupons WHERE code = ?",
                ps -> ps.setString(1, code), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Coupon> findAll() {
        return db.query("SELECT * FROM coupons ORDER BY code", ps -> {}, this::map);
    }

    @Override
    public void delete(String code) {
        db.update("DELETE FROM coupons WHERE code = ?", ps -> ps.setString(1, code));
    }

    private Coupon map(ResultSet rs) throws SQLException {
        Coupon.Type type = Coupon.Type.valueOf(rs.getString("type"));
        Money min = SqlSupport.centsToMoney(rs.getLong("minimum_order_cents"));
        if (type == Coupon.Type.PERCENT) {
            BigDecimal pct = BigDecimal.valueOf(rs.getLong("percent_bp"))
                    .divide(BigDecimal.valueOf(10000));
            return Coupon.percent(rs.getString("code"), pct, min);
        }
        Money fixed = SqlSupport.centsToMoney(rs.getLong("fixed_cents"));
        return Coupon.fixed(rs.getString("code"), fixed, min);
    }
}
