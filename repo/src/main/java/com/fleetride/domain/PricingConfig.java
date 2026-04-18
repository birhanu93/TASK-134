package com.fleetride.domain;

import java.math.BigDecimal;

public final class PricingConfig {
    public static final class InvalidPolicyException extends IllegalArgumentException {
        public InvalidPolicyException(String msg) { super(msg); }
    }

    private Money baseFare = Money.of("3.50");
    private Money perMile = Money.of("1.80");
    private Money perMinute = Money.of("0.35");
    private BigDecimal priorityMultiplier = new BigDecimal("1.25");
    private Money perFloorSurcharge = Money.of("1.00");
    private int freeFloorThreshold = 3;
    private BigDecimal depositPercent = new BigDecimal("0.20");
    private Money monthlySubsidyCap = Money.of("50.00");
    private BigDecimal maxCouponPercent = new BigDecimal("0.20");
    private Money couponMinimumOrder = Money.of("25.00");
    private Money lateCancelFee = Money.of("5.00");
    private int autoCancelMinutes = 15;
    private int lateCancelWindowMinutes = 10;
    private int disputeWindowDays = 7;
    private Money overdueFeePerSweep = Money.of("5.00");

    public Money baseFare() { return baseFare; }
    public void setBaseFare(Money v) { this.baseFare = requireNonNegativeMoney("baseFare", v); }
    public Money perMile() { return perMile; }
    public void setPerMile(Money v) { this.perMile = requireNonNegativeMoney("perMile", v); }
    public Money perMinute() { return perMinute; }
    public void setPerMinute(Money v) { this.perMinute = requireNonNegativeMoney("perMinute", v); }
    public BigDecimal priorityMultiplier() { return priorityMultiplier; }
    public void setPriorityMultiplier(BigDecimal v) {
        this.priorityMultiplier = requireInRange("priorityMultiplier", v,
                new BigDecimal("1.00"), new BigDecimal("5.00"));
    }
    public Money perFloorSurcharge() { return perFloorSurcharge; }
    public void setPerFloorSurcharge(Money v) {
        this.perFloorSurcharge = requireNonNegativeMoney("perFloorSurcharge", v);
    }
    public int freeFloorThreshold() { return freeFloorThreshold; }
    public void setFreeFloorThreshold(int v) {
        if (v < 0 || v > 200) {
            throw new InvalidPolicyException("freeFloorThreshold must be in [0, 200] (got " + v + ")");
        }
        this.freeFloorThreshold = v;
    }
    public BigDecimal depositPercent() { return depositPercent; }
    public void setDepositPercent(BigDecimal v) {
        this.depositPercent = requireInRange("depositPercent", v, BigDecimal.ZERO, BigDecimal.ONE);
    }
    public Money monthlySubsidyCap() { return monthlySubsidyCap; }
    public void setMonthlySubsidyCap(Money v) {
        this.monthlySubsidyCap = requireNonNegativeMoney("monthlySubsidyCap", v);
    }
    public BigDecimal maxCouponPercent() { return maxCouponPercent; }
    public void setMaxCouponPercent(BigDecimal v) {
        // Spec caps percent-off coupons at 20% — enforce it here so admin UI can't lift the cap.
        this.maxCouponPercent = requireInRange("maxCouponPercent", v,
                BigDecimal.ZERO, new BigDecimal("0.20"));
    }
    public Money couponMinimumOrder() { return couponMinimumOrder; }
    public void setCouponMinimumOrder(Money v) {
        this.couponMinimumOrder = requireNonNegativeMoney("couponMinimumOrder", v);
    }
    public Money lateCancelFee() { return lateCancelFee; }
    public void setLateCancelFee(Money v) {
        this.lateCancelFee = requireNonNegativeMoney("lateCancelFee", v);
    }
    public int autoCancelMinutes() { return autoCancelMinutes; }
    public void setAutoCancelMinutes(int v) {
        // Must be strictly positive; an hour is already a stretch for "wasn't accepted in time".
        requirePositiveMinutes("autoCancelMinutes", v, 60);
        this.autoCancelMinutes = v;
    }
    public int lateCancelWindowMinutes() { return lateCancelWindowMinutes; }
    public void setLateCancelWindowMinutes(int v) {
        // Window around scheduled pickup; keep under a day to avoid degenerate "always late" config.
        requirePositiveMinutes("lateCancelWindowMinutes", v, 24 * 60);
        this.lateCancelWindowMinutes = v;
    }
    public int disputeWindowDays() { return disputeWindowDays; }
    public void setDisputeWindowDays(int v) {
        if (v <= 0 || v > 90) {
            throw new InvalidPolicyException("disputeWindowDays must be in (0, 90] (got " + v + ")");
        }
        this.disputeWindowDays = v;
    }
    public Money overdueFeePerSweep() { return overdueFeePerSweep; }
    public void setOverdueFeePerSweep(Money v) {
        this.overdueFeePerSweep = requireNonNegativeMoney("overdueFeePerSweep", v);
    }

    private static Money requireNonNegativeMoney(String field, Money v) {
        if (v == null) {
            throw new InvalidPolicyException(field + " required");
        }
        if (v.isNegative()) {
            throw new InvalidPolicyException(field + " must be non-negative (got " + v + ")");
        }
        return v;
    }

    private static BigDecimal requireInRange(String field, BigDecimal v, BigDecimal min, BigDecimal max) {
        if (v == null) {
            throw new InvalidPolicyException(field + " required");
        }
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) {
            throw new InvalidPolicyException(field + " must be in [" + min.toPlainString()
                    + ", " + max.toPlainString() + "] (got " + v.toPlainString() + ")");
        }
        return v;
    }

    private static void requirePositiveMinutes(String field, int v, int max) {
        if (v <= 0) {
            throw new InvalidPolicyException(field + " must be positive (got " + v + ")");
        }
        if (v > max) {
            throw new InvalidPolicyException(field + " must be at most " + max + " minutes (got " + v + ")");
        }
    }
}
