package com.fleetride.repository;

import com.fleetride.domain.AuditEvent;

import java.util.List;

public interface AuditRepository {
    void save(AuditEvent e);
    List<AuditEvent> findAll();
}
