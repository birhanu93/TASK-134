package com.fleetride.service;

import com.fleetride.domain.Coupon;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Fare;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.Subsidy;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PricingEngine {
    private final PricingConfig config;

    public PricingEngine(PricingConfig config) {
        if (config == null) throw new IllegalArgumentException("config required");
        this.config = config;
    }

    public Fare quote(Order order, Customer customer, Coupon coupon, Subsidy subsidy) {
        Money base = config.baseFare();
        Money distance = config.perMile().multiply(BigDecimal.valueOf(order.miles()));
        Money time = config.perMinute().multiply(BigDecimal.valueOf(order.durationMinutes()));
        Money floorSurcharge = computeFloorSurcharge(order);

        Money sumBeforePriority = base.add(distance).add(time).add(floorSurcharge);

        Money priorityAdj = Money.ZERO;
        Money subtotal = sumBeforePriority;
        if (order.priority() == ServicePriority.PRIORITY) {
            Money multiplied = sumBeforePriority.multiply(config.priorityMultiplier());
            priorityAdj = multiplied.subtract(sumBeforePriority);
            subtotal = multiplied;
        }

        Money couponDiscount = applyCoupon(subtotal, coupon);
        Money afterCoupon = subtotal.subtract(couponDiscount);

        Money subsidyApplied = applySubsidy(afterCoupon, customer, subsidy);
        Money total = afterCoupon.subtract(subsidyApplied);

        Money deposit = total.multiply(config.depositPercent());

        return new Fare(base, distance, time, floorSurcharge, priorityAdj,
                couponDiscount, subsidyApplied, subtotal, total, deposit);
    }

    private Money computeFloorSurcharge(Order order) {
        Money sum = Money.ZERO;
        sum = sum.add(surchargeForFloor(order.pickup().floor()));
        sum = sum.add(surchargeForFloor(order.dropoff().floor()));
        return sum;
    }

    private Money surchargeForFloor(Integer floor) {
        if (floor == null) return Money.ZERO;
        int extra = floor - config.freeFloorThreshold();
        if (extra <= 0) return Money.ZERO;
        return config.perFloorSurcharge().multiply(BigDecimal.valueOf(extra));
    }

    private Money applyCoupon(Money subtotal, Coupon coupon) {
        if (coupon == null) return Money.ZERO;
        if (subtotal.lessThan(coupon.minimumOrder())) return Money.ZERO;
        if (subtotal.lessThan(config.couponMinimumOrder())) return Money.ZERO;
        if (coupon.type() == Coupon.Type.PERCENT) {
            BigDecimal pct = coupon.percent();
            if (pct.compareTo(config.maxCouponPercent()) > 0) {
                pct = config.maxCouponPercent();
            }
            return subtotal.multiply(pct);
        }
        Money candidate = coupon.fixedAmount();
        if (candidate.greaterThan(subtotal)) {
            return subtotal;
        }
        return candidate;
    }

    private Money applySubsidy(Money amount, Customer customer, Subsidy subsidy) {
        if (subsidy == null) return Money.ZERO;
        Money used = customer.subsidyUsedThisMonth();
        Money cap = subsidy.monthlyCap();
        if (!used.lessThan(cap)) return Money.ZERO;
        Money remaining = cap.subtract(used);
        if (remaining.greaterThan(amount)) {
            return amount;
        }
        return remaining;
    }

    public Money computeCancellationFee(Order order, java.time.LocalDateTime now) {
        long minutesUntil = java.time.Duration.between(now, order.window().start()).toMinutes();
        if (minutesUntil <= config.lateCancelWindowMinutes() && minutesUntil >= -config.lateCancelWindowMinutes()) {
            return config.lateCancelFee();
        }
        return Money.ZERO;
    }

    public PricingConfig config() { return config; }

    public static BigDecimal roundPercent(BigDecimal p) {
        return p.setScale(4, RoundingMode.HALF_UP);
    }
}
