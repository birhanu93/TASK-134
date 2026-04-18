package com.fleetride.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class MachineIdProviderTest {

    @Test
    void rejectsNullFile() {
        assertThrows(IllegalArgumentException.class, () -> new MachineIdProvider(null));
    }

    @Test
    void generatesOnFirstCallAndPersists(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("nested/machine-id");
        MachineIdProvider p = new MachineIdProvider(f);
        String id = p.machineId();
        assertNotNull(id);
        assertTrue(Files.exists(f));
        assertEquals(id.trim(), Files.readString(f).trim());
    }

    @Test
    void cachesBetweenCalls(@TempDir Path dir) {
        MachineIdProvider p = new MachineIdProvider(dir.resolve("id"));
        assertEquals(p.machineId(), p.machineId());
    }

    @Test
    void reusesExistingIdFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("id");
        Files.writeString(f, "preexisting-id");
        MachineIdProvider p = new MachineIdProvider(f);
        assertEquals("preexisting-id", p.machineId());
    }

    @Test
    void blankFileRegenerates(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("id");
        Files.writeString(f, "   \n");
        MachineIdProvider p = new MachineIdProvider(f);
        String id = p.machineId();
        assertNotEquals("", id.trim());
    }

    @Test
    void customRandomIsDeterministicPerSeed(@TempDir Path dir) {
        SecureRandom r = new SecureRandom("seed".getBytes());
        MachineIdProvider p = new MachineIdProvider(dir.resolve("id"), r);
        assertNotNull(p.machineId());
    }
}
