package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FareTest {

    @Test
    void accessors() {
        Fare f = new Fare(
                Money.of("3.50"), Money.of("5.40"), Money.of("3.50"),
                Money.of("1.00"), Money.of("2.00"), Money.of("1.00"),
                Money.of("0.50"), Money.of("15.40"), Money.of("13.90"), Money.of("2.78"));
        assertEquals(Money.of("3.50"), f.baseFare());
        assertEquals(Money.of("5.40"), f.distanceCharge());
        assertEquals(Money.of("3.50"), f.timeCharge());
        assertEquals(Money.of("1.00"), f.floorSurcharge());
        assertEquals(Money.of("2.00"), f.priorityAdjustment());
        assertEquals(Money.of("1.00"), f.couponDiscount());
        assertEquals(Money.of("0.50"), f.subsidyApplied());
        assertEquals(Money.of("15.40"), f.subtotal());
        assertEquals(Money.of("13.90"), f.total());
        assertEquals(Money.of("2.78"), f.deposit());
    }
}
