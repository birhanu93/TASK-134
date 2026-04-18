package com.fleetride.service;

import java.util.LinkedHashMap;
import java.util.Map;

public class HealthService {
    public enum Status { OK, DEGRADED, DOWN }

    public static final class Check {
        private final Status status;
        private final String message;
        public Check(Status status, String message) {
            this.status = status;
            this.message = message;
        }
        public Status status() { return status; }
        public String message() { return message; }
    }

    private final Runnable dbPing;
    private final java.time.LocalDateTime startedAt;
    private final Clock clock;

    public HealthService(Runnable dbPing, Clock clock) {
        this.dbPing = dbPing;
        this.clock = clock;
        this.startedAt = clock.now();
    }

    public Map<String, Check> snapshot() {
        Map<String, Check> checks = new LinkedHashMap<>();
        checks.put("database", dbCheck());
        checks.put("uptime", new Check(Status.OK, uptimeMessage()));
        return checks;
    }

    private Check dbCheck() {
        try {
            dbPing.run();
            return new Check(Status.OK, "reachable");
        } catch (RuntimeException e) {
            return new Check(Status.DOWN, e.getMessage());
        }
    }

    private String uptimeMessage() {
        java.time.Duration d = java.time.Duration.between(startedAt, clock.now());
        return "up " + d.toMinutes() + "m";
    }

    public Status overall() {
        Status worst = Status.OK;
        for (Check c : snapshot().values()) {
            if (c.status() == Status.DOWN) return Status.DOWN;
            if (c.status() == Status.DEGRADED) worst = Status.DEGRADED;
        }
        return worst;
    }
}
