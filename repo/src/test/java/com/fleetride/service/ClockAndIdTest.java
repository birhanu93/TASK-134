package com.fleetride.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClockAndIdTest {

    @Test
    void systemClockNonNull() {
        Clock c = Clock.system();
        assertNotNull(c.now());
    }

    @Test
    void idGeneratorReturnsUniqueUuid() {
        IdGenerator g = IdGenerator.uuid();
        String a = g.next();
        String b = g.next();
        assertNotEquals(a, b);
        assertNotNull(a);
    }
}
