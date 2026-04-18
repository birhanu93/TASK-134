package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CouponTest {

    @Test
    void percentFactoryValidates() {
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.percent("C", null, Money.of("25.00")));
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.percent("C", BigDecimal.ZERO, Money.of("25.00")));
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.percent("C", new BigDecimal("-0.1"), Money.of("25.00")));
    }

    @Test
    void fixedFactoryValidates() {
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.fixed("C", null, Money.of("25.00")));
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.fixed("C", Money.of("-1.00"), Money.of("25.00")));
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.fixed("C", Money.ZERO, Money.of("25.00")));
    }

    @Test
    void constructorRejectsBlankCode() {
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.percent(null, new BigDecimal("0.1"), Money.of("25.00")));
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.percent("", new BigDecimal("0.1"), Money.of("25.00")));
    }

    @Test
    void constructorRejectsNullMinimum() {
        assertThrows(IllegalArgumentException.class,
                () -> Coupon.percent("C", new BigDecimal("0.1"), null));
    }

    @Test
    void percentCouponAccessors() {
        Coupon c = Coupon.percent("P", new BigDecimal("0.15"), Money.of("25.00"));
        assertEquals("P", c.code());
        assertEquals(Coupon.Type.PERCENT, c.type());
        assertEquals(new BigDecimal("0.15"), c.percent());
        assertNull(c.fixedAmount());
        assertEquals(Money.of("25.00"), c.minimumOrder());
    }

    @Test
    void fixedCouponAccessors() {
        Coupon c = Coupon.fixed("F", Money.of("5.00"), Money.of("25.00"));
        assertEquals(Coupon.Type.FIXED, c.type());
        assertNull(c.percent());
        assertEquals(Money.of("5.00"), c.fixedAmount());
    }
}
