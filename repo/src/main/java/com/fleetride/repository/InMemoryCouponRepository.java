package com.fleetride.repository;

import com.fleetride.domain.Coupon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCouponRepository implements CouponRepository {
    private final Map<String, Coupon> store = new ConcurrentHashMap<>();

    @Override
    public void save(Coupon coupon) { store.put(coupon.code(), coupon); }

    @Override
    public Optional<Coupon> findByCode(String code) { return Optional.ofNullable(store.get(code)); }

    @Override
    public List<Coupon> findAll() { return new ArrayList<>(store.values()); }

    @Override
    public void delete(String code) { store.remove(code); }
}
