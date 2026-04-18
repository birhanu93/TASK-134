package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new User(null, "u", "p", Role.DISPATCHER));
        assertThrows(IllegalArgumentException.class, () -> new User("", "u", "p", Role.DISPATCHER));
        assertThrows(IllegalArgumentException.class, () -> new User("id", null, "p", Role.DISPATCHER));
        assertThrows(IllegalArgumentException.class, () -> new User("id", " ", "p", Role.DISPATCHER));
        assertThrows(IllegalArgumentException.class, () -> new User("id", "u", null, Role.DISPATCHER));
        assertThrows(IllegalArgumentException.class, () -> new User("id", "u", "p", null));
    }

    @Test
    void accessors() {
        User u = new User("id", "name", "hashed", Role.ADMINISTRATOR);
        assertEquals("id", u.id());
        assertEquals("name", u.username());
        assertEquals("hashed", u.encryptedPassword());
        assertEquals(Role.ADMINISTRATOR, u.role());
    }

    @Test
    void equalsAndHash() {
        User a = new User("id", "name", "hashed", Role.DISPATCHER);
        User b = new User("id", "other", "x", Role.ADMINISTRATOR);
        User c = new User("id2", "name", "hashed", Role.DISPATCHER);
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
