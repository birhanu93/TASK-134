package com.fleetride.repository;

import com.fleetride.domain.Coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    void save(Coupon coupon);
    Optional<Coupon> findByCode(String code);
    List<Coupon> findAll();
    void delete(String code);
}
