package com.fleetride.ui;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapEnvTest {

    @Test
    void parsesUsernameAndPassword() {
        Optional<BootstrapEnv.Credentials> creds = BootstrapEnv.parse("admin:pw");
        assertTrue(creds.isPresent());
        assertEquals("admin", creds.get().username());
        assertEquals("pw", creds.get().password());
    }

    @Test
    void passwordMayContainColons() {
        Optional<BootstrapEnv.Credentials> creds = BootstrapEnv.parse("admin:pw:with:colons");
        assertTrue(creds.isPresent());
        assertEquals("admin", creds.get().username());
        assertEquals("pw:with:colons", creds.get().password());
    }

    @Test
    void passwordMayBeEmpty() {
        // The env var provisions a user; AuthService will accept an empty password
        // if the operator asks for it. The parser only rejects malformed shapes.
        Optional<BootstrapEnv.Credentials> creds = BootstrapEnv.parse("admin:");
        assertTrue(creds.isPresent());
        assertEquals("admin", creds.get().username());
        assertEquals("", creds.get().password());
    }

    @Test
    void rejectsNull() {
        assertTrue(BootstrapEnv.parse(null).isEmpty());
    }

    @Test
    void rejectsMissingColon() {
        assertTrue(BootstrapEnv.parse("admin").isEmpty());
    }

    @Test
    void rejectsEmptyUsername() {
        assertTrue(BootstrapEnv.parse(":pw").isEmpty());
    }

    @Test
    void rejectsEmptyString() {
        assertTrue(BootstrapEnv.parse("").isEmpty());
    }
}
