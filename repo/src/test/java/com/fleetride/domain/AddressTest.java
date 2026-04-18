package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressTest {

    @Test
    void rejectsNullLine1() {
        assertThrows(IllegalArgumentException.class, () -> new Address(null, "NY", "NY", "10001", null));
    }

    @Test
    void rejectsBlankLine1() {
        assertThrows(IllegalArgumentException.class, () -> new Address("  ", "NY", "NY", "10001", null));
    }

    @Test
    void rejectsNullCity() {
        assertThrows(IllegalArgumentException.class, () -> new Address("1 Main", null, "NY", "10001", null));
    }

    @Test
    void rejectsBlankCity() {
        assertThrows(IllegalArgumentException.class, () -> new Address("1 Main", "", "NY", "10001", null));
    }

    @Test
    void formatFull() {
        Address a = new Address("1 Main", "NY", "NY", "10001", 5);
        assertEquals("1 Main, NY, NY 10001 (Fl 5)", a.format());
        assertEquals("1 Main", a.line1());
        assertEquals("NY", a.city());
        assertEquals("NY", a.state());
        assertEquals("10001", a.zip());
        assertEquals(5, a.floor());
    }

    @Test
    void formatMinimal() {
        Address a = new Address("1 Main", "NY", null, null, null);
        assertEquals("1 Main, NY", a.format());
    }

    @Test
    void equalsAndHashCode() {
        Address a = new Address("1 Main", "NY", "NY", "10001", 5);
        Address b = new Address("1 Main", "NY", "NY", "10001", 5);
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, "str");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalsDiffersPerField() {
        Address base = new Address("1 Main", "NY", "NY", "10001", 5);
        assertNotEquals(base, new Address("2 Main", "NY", "NY", "10001", 5));
        assertNotEquals(base, new Address("1 Main", "LA", "NY", "10001", 5));
        assertNotEquals(base, new Address("1 Main", "NY", "CA", "10001", 5));
        assertNotEquals(base, new Address("1 Main", "NY", "NY", "90001", 5));
        assertNotEquals(base, new Address("1 Main", "NY", "NY", "10001", 6));
    }
}
