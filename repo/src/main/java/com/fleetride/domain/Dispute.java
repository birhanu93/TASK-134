package com.fleetride.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Dispute {
    public enum Status { OPEN, RESOLVED, REJECTED }

    private final String id;
    private final String orderId;
    private final String reason;
    private final LocalDateTime openedAt;
    private Status status;
    private String resolution;
    private LocalDateTime resolvedAt;

    public Dispute(String id, String orderId, String reason, LocalDateTime openedAt) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason required");
        if (openedAt == null) throw new IllegalArgumentException("openedAt required");
        this.id = id;
        this.orderId = orderId;
        this.reason = reason;
        this.openedAt = openedAt;
        this.status = Status.OPEN;
    }

    public String id() { return id; }
    public String orderId() { return orderId; }
    public String reason() { return reason; }
    public LocalDateTime openedAt() { return openedAt; }
    public Status status() { return status; }
    public String resolution() { return resolution; }
    public LocalDateTime resolvedAt() { return resolvedAt; }

    public void resolve(String resolution, LocalDateTime at) {
        this.status = Status.RESOLVED;
        this.resolution = resolution;
        this.resolvedAt = at;
    }

    public void reject(String resolution, LocalDateTime at) {
        this.status = Status.REJECTED;
        this.resolution = resolution;
        this.resolvedAt = at;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dispute d)) return false;
        return Objects.equals(id, d.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
