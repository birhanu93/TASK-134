package com.fleetride.service;

import com.fleetride.repository.InMemoryAuditRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    @Test
    void recordAndList() {
        InMemoryAuditRepository repo = new InMemoryAuditRepository();
        AtomicInteger counter = new AtomicInteger();
        IdGenerator ids = () -> "id-" + counter.incrementAndGet();
        Clock clock = () -> LocalDateTime.of(2026, 3, 27, 10, 0);
        AuditService svc = new AuditService(repo, ids, clock);
        svc.record("user", "ACTION", "target", "details");
        svc.record("user", "ACTION2", null, null);
        assertEquals(2, svc.all().size());
        assertEquals("id-1", svc.all().get(0).id());
    }
}
