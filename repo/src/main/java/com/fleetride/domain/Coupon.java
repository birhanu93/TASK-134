package com.fleetride.domain;

import java.math.BigDecimal;

public final class Coupon {
    public enum Type { PERCENT, FIXED }

    private final String code;
    private final Type type;
    private final BigDecimal percent;
    private final Money fixedAmount;
    private final Money minimumOrder;

    private Coupon(String code, Type type, BigDecimal percent, Money fixedAmount, Money minimumOrder) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code required");
        if (minimumOrder == null) throw new IllegalArgumentException("minimumOrder required");
        this.code = code;
        this.type = type;
        this.percent = percent;
        this.fixedAmount = fixedAmount;
        this.minimumOrder = minimumOrder;
    }

    public static Coupon percent(String code, BigDecimal percent, Money minimumOrder) {
        if (percent == null || percent.signum() <= 0) {
            throw new IllegalArgumentException("percent must be > 0");
        }
        return new Coupon(code, Type.PERCENT, percent, null, minimumOrder);
    }

    public static Coupon fixed(String code, Money amount, Money minimumOrder) {
        if (amount == null || amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        return new Coupon(code, Type.FIXED, null, amount, minimumOrder);
    }

    public String code() { return code; }
    public Type type() { return type; }
    public BigDecimal percent() { return percent; }
    public Money fixedAmount() { return fixedAmount; }
    public Money minimumOrder() { return minimumOrder; }
}
