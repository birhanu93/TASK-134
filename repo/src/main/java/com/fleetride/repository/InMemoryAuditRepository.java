package com.fleetride.repository;

import com.fleetride.domain.AuditEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InMemoryAuditRepository implements AuditRepository {
    private final List<AuditEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void save(AuditEvent e) { events.add(e); }

    @Override
    public List<AuditEvent> findAll() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }
}
