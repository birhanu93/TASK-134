package com.fleetride.domain;

import java.time.LocalDateTime;

public final class AuditEvent {
    private final String id;
    private final String actor;
    private final String action;
    private final String target;
    private final String details;
    private final LocalDateTime at;

    public AuditEvent(String id, String actor, String action, String target, String details, LocalDateTime at) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (actor == null) throw new IllegalArgumentException("actor required");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action required");
        if (at == null) throw new IllegalArgumentException("at required");
        this.id = id;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.details = details;
        this.at = at;
    }

    public String id() { return id; }
    public String actor() { return actor; }
    public String action() { return action; }
    public String target() { return target; }
    public String details() { return details; }
    public LocalDateTime at() { return at; }
}
