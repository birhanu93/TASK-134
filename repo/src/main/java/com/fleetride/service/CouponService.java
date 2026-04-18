package com.fleetride.service;

import com.fleetride.domain.Coupon;
import com.fleetride.domain.Money;
import com.fleetride.repository.CouponRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class CouponService {
    public static final class CouponException extends RuntimeException {
        public CouponException(String msg) { super(msg); }
    }

    private final CouponRepository repo;
    private final AuditService audit;

    public CouponService(CouponRepository repo, AuditService audit) {
        if (repo == null) throw new IllegalArgumentException("repo required");
        if (audit == null) throw new IllegalArgumentException("audit required");
        this.repo = repo;
        this.audit = audit;
    }

    public Coupon createPercent(String code, BigDecimal percent, Money minimumOrder) {
        Coupon c = Coupon.percent(code, percent, minimumOrder);
        repo.save(c);
        audit.record("system", "COUPON_CREATE", code, "PERCENT " + percent);
        return c;
    }

    public Coupon createFixed(String code, Money amount, Money minimumOrder) {
        Coupon c = Coupon.fixed(code, amount, minimumOrder);
        repo.save(c);
        audit.record("system", "COUPON_CREATE", code, "FIXED " + amount);
        return c;
    }

    public Optional<Coupon> find(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return repo.findByCode(code);
    }

    public Coupon require(String code) {
        return find(code).orElseThrow(() -> new CouponException("unknown coupon " + code));
    }

    public List<Coupon> list() { return repo.findAll(); }

    public void delete(String code) {
        repo.delete(code);
        audit.record("system", "COUPON_DELETE", code, null);
    }
}
