package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void constructorRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Money(null));
    }

    @Test
    void ofDoubleAndString() {
        assertEquals(new Money(new BigDecimal("3.50")), Money.of(3.50));
        assertEquals(new Money(new BigDecimal("3.50")), Money.of("3.50"));
    }

    @Test
    void zeroConstant() {
        assertTrue(Money.ZERO.isZero());
        assertFalse(Money.ZERO.isNegative());
    }

    @Test
    void addAndSubtract() {
        Money a = Money.of("5.00");
        Money b = Money.of("2.00");
        assertEquals(Money.of("7.00"), a.add(b));
        assertEquals(Money.of("3.00"), a.subtract(b));
    }

    @Test
    void multiplyBigDecimalAndDouble() {
        Money a = Money.of("2.00");
        assertEquals(Money.of("5.00"), a.multiply(new BigDecimal("2.5")));
        assertEquals(Money.of("5.00"), a.multiply(2.5));
    }

    @Test
    void negativeAndComparisons() {
        Money neg = Money.of("-1.00");
        assertTrue(neg.isNegative());
        Money five = Money.of("5.00");
        Money three = Money.of("3.00");
        assertTrue(five.greaterThan(three));
        assertFalse(three.greaterThan(five));
        assertTrue(five.greaterThanOrEqual(five));
        assertFalse(three.greaterThanOrEqual(five));
        assertTrue(three.lessThan(five));
        assertFalse(five.lessThan(three));
        assertTrue(five.compareTo(three) > 0);
    }

    @Test
    void equalsAndHashCode() {
        Money a = Money.of("1.00");
        Money b = Money.of("1.00");
        Money c = Money.of("2.00");
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, "1.00");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toStringHasDollarPrefix() {
        assertEquals("$3.50", Money.of("3.50").toString());
    }

    @Test
    void amountAccessor() {
        assertEquals(new BigDecimal("3.50"), Money.of("3.50").amount());
    }
}
