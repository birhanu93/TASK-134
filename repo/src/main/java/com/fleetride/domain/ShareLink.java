package com.fleetride.domain;

import java.time.LocalDateTime;

public final class ShareLink {
    private final String token;
    private final String resourceId;
    private final String machineId;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;

    public ShareLink(String token, String resourceId, String machineId,
                     LocalDateTime createdAt, LocalDateTime expiresAt) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("token required");
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId required");
        if (machineId == null || machineId.isBlank()) throw new IllegalArgumentException("machineId required");
        if (createdAt == null) throw new IllegalArgumentException("createdAt required");
        if (expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        this.token = token;
        this.resourceId = resourceId;
        this.machineId = machineId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String token() { return token; }
    public String resourceId() { return resourceId; }
    public String machineId() { return machineId; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime expiresAt() { return expiresAt; }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShareLink l)) return false;
        return java.util.Objects.equals(token, l.token);
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(token); }
}
