package com.fleetride.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HealthServiceTest {

    @Test
    void okWhenPingSucceeds() {
        Clock clock = Clock.system();
        HealthService h = new HealthService(() -> {}, clock);
        assertEquals(HealthService.Status.OK, h.overall());
        assertEquals("reachable", h.snapshot().get("database").message());
    }

    @Test
    void downWhenPingFails() {
        HealthService h = new HealthService(() -> { throw new RuntimeException("no db"); }, Clock.system());
        assertEquals(HealthService.Status.DOWN, h.overall());
        assertEquals(HealthService.Status.DOWN, h.snapshot().get("database").status());
        assertEquals("no db", h.snapshot().get("database").message());
    }

    @Test
    void degradedBubbles() {
        AtomicReference<LocalDateTime> now = new AtomicReference<>(LocalDateTime.now());
        HealthService h = new HealthService(() -> {}, now::get) {
            @Override
            public java.util.Map<String, Check> snapshot() {
                return java.util.Map.of(
                        "database", new Check(Status.OK, "ok"),
                        "cache", new Check(Status.DEGRADED, "slow")
                );
            }
        };
        assertEquals(HealthService.Status.DEGRADED, h.overall());
    }

    @Test
    void uptimeMessageChanges() {
        AtomicReference<LocalDateTime> now = new AtomicReference<>(LocalDateTime.of(2026, 3, 27, 10, 0));
        HealthService h = new HealthService(() -> {}, now::get);
        now.set(now.get().plusMinutes(5));
        assertEquals("up 5m", h.snapshot().get("uptime").message());
    }

    @Test
    void statusAndCheckAccessors() {
        HealthService.Check c = new HealthService.Check(HealthService.Status.OK, "msg");
        assertEquals(HealthService.Status.OK, c.status());
        assertEquals("msg", c.message());
        assertEquals(3, HealthService.Status.values().length);
        assertEquals(HealthService.Status.OK, HealthService.Status.valueOf("OK"));
    }
}
