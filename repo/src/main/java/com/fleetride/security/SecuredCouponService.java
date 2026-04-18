package com.fleetride.security;

import com.fleetride.domain.Coupon;
import com.fleetride.domain.Money;
import com.fleetride.service.CouponService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class SecuredCouponService {
    private final CouponService delegate;
    private final Authorizer authz;

    public SecuredCouponService(CouponService delegate, Authorizer authz) {
        this.delegate = delegate;
        this.authz = authz;
    }

    public Coupon createPercent(String code, BigDecimal percent, Money minimumOrder) {
        authz.require(Permission.COUPON_MANAGE);
        return delegate.createPercent(code, percent, minimumOrder);
    }

    public Coupon createFixed(String code, Money amount, Money minimumOrder) {
        authz.require(Permission.COUPON_MANAGE);
        return delegate.createFixed(code, amount, minimumOrder);
    }

    public Optional<Coupon> find(String code) {
        authz.require(Permission.COUPON_READ);
        return delegate.find(code);
    }

    public List<Coupon> list() {
        authz.require(Permission.COUPON_READ);
        return delegate.list();
    }

    public void delete(String code) {
        authz.require(Permission.COUPON_MANAGE);
        delegate.delete(code);
    }
}
