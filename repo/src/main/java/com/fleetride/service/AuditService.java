package com.fleetride.service;

import com.fleetride.domain.AuditEvent;
import com.fleetride.repository.AuditRepository;

import java.util.List;

public final class AuditService {
    private final AuditRepository repo;
    private final IdGenerator ids;
    private final Clock clock;

    public AuditService(AuditRepository repo, IdGenerator ids, Clock clock) {
        this.repo = repo;
        this.ids = ids;
        this.clock = clock;
    }

    public void record(String actor, String action, String target, String details) {
        repo.save(new AuditEvent(ids.next(), actor, action, target, details, clock.now()));
    }

    public List<AuditEvent> all() { return repo.findAll(); }
}
