package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubsidyTest {

    @Test
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new Subsidy(null, Money.of("50.00")));
        assertThrows(IllegalArgumentException.class, () -> new Subsidy(" ", Money.of("50.00")));
        assertThrows(IllegalArgumentException.class, () -> new Subsidy("c", null));
        assertThrows(IllegalArgumentException.class, () -> new Subsidy("c", Money.of("-1.00")));
    }

    @Test
    void accessors() {
        Subsidy s = new Subsidy("c1", Money.of("50.00"));
        assertEquals("c1", s.customerId());
        assertEquals(Money.of("50.00"), s.monthlyCap());
    }
}
