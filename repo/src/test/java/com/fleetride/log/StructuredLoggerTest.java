package com.fleetride.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuredLoggerTest {

    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    @Test
    void logsJsonLine(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("log.ndjson");
        StructuredLogger log = new StructuredLogger(file, () -> t);
        log.info("user_login", Map.of("username", "alice"));
        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"user_login\""));
        assertTrue(lines.get(0).contains("\"level\":\"INFO\""));
        assertTrue(lines.get(0).contains("\"username\":\"alice\""));
    }

    @Test
    void redactsFields(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("log.ndjson");
        StructuredLogger log = new StructuredLogger(file, () -> t);
        log.warn("auth_fail", Map.of("password", "topsecret", "note", "email a@b.co"));
        String content = Files.readString(file);
        assertFalse(content.contains("topsecret"));
        assertTrue(content.contains("***"));
    }

    @Test
    void levelCoverage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("log.ndjson");
        StructuredLogger log = new StructuredLogger(file, () -> t);
        log.debug("d", null);
        log.info("i", null);
        log.warn("w", null);
        log.error("e", null);
        assertEquals(4, Files.readAllLines(file).size());
        assertEquals(4, StructuredLogger.Level.values().length);
    }

    @Test
    void fallbackWriter(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("log.ndjson");
        StringWriter w = new StringWriter();
        StructuredLogger log = new StructuredLogger(file, () -> t, w);
        log.info("evt", Map.of("k", "v"));
        assertTrue(w.toString().contains("evt"));
    }

    @Test
    void requiredArgs(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> new StructuredLogger(null, () -> t));
        assertThrows(IllegalArgumentException.class,
                () -> new StructuredLogger(dir.resolve("f"), null));
    }

    @Test
    void rejectsBlankEvent(@TempDir Path dir) {
        StructuredLogger log = new StructuredLogger(dir.resolve("l"), () -> t);
        assertThrows(IllegalArgumentException.class, () -> log.info(null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> log.info(" ", Map.of()));
    }

    @Test
    void escapesSpecialChars(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("log.ndjson");
        StructuredLogger log = new StructuredLogger(file, () -> t);
        log.info("evt", Map.of("q", "has \"quote\" and\nnewline\tand\rCR and \u0001 control"));
        String line = Files.readString(file);
        assertTrue(line.contains("\\\""));
        assertTrue(line.contains("\\n"));
        assertTrue(line.contains("\\t"));
        assertTrue(line.contains("\\r"));
        assertTrue(line.contains("\\u0001"));
    }

    @Test
    void escapesBackslash(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("log.ndjson");
        StructuredLogger log = new StructuredLogger(file, () -> t);
        log.info("evt", Map.of("q", "a\\b"));
        assertTrue(Files.readString(file).contains("a\\\\b"));
    }

    @Test
    void nullValueProducesJsonNull(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("log.ndjson");
        StructuredLogger log = new StructuredLogger(file, () -> t);
        java.util.HashMap<String, String> m = new java.util.HashMap<>();
        m.put("x", null);
        log.info("evt", m);
        assertTrue(Files.readString(file).contains("\"x\":null"));
    }

    @Test
    void createsLogDirectoryIfMissing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested/deeper/log.ndjson");
        new StructuredLogger(file, () -> t);
        assertTrue(Files.exists(file));
    }

    @Test
    void reuseExistingFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("existing.log");
        Files.createFile(file);
        new StructuredLogger(file, () -> t);
        assertTrue(Files.exists(file));
    }

    @Test
    void rootLogFileNoParent(@TempDir Path dir) throws Exception {
        // no parent directory case
        Path file = Path.of("standalone.log");
        Files.deleteIfExists(file);
        try {
            new StructuredLogger(file, () -> t);
            assertTrue(Files.exists(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void fieldsHelperBuildsMap() {
        Map<String, String> f = StructuredLogger.fields("a", "1", "b", "2");
        assertEquals("1", f.get("a"));
        assertEquals("2", f.get("b"));
        assertThrows(IllegalArgumentException.class,
                () -> StructuredLogger.fields("only-key"));
    }

    @Test
    void logWithWriterCoverage(@TempDir Path dir) {
        Path file = dir.resolve("log.ndjson");
        Writer w = new StringWriter();
        StructuredLogger log = new StructuredLogger(file, () -> t, w);
        log.log(StructuredLogger.Level.DEBUG, "d", Map.of());
    }
}
