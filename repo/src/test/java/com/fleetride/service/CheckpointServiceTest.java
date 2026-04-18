package com.fleetride.service;

import com.fleetride.repository.InMemoryCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointServiceTest {

    private CheckpointService svc;
    private InMemoryCheckpointRepository repo;

    @BeforeEach
    void setup() {
        repo = new InMemoryCheckpointRepository();
        svc = new CheckpointService(repo, () -> LocalDateTime.of(2026, 3, 27, 10, 0));
    }

    @Test
    void rejectsNullDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> new CheckpointService(null, () -> LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckpointService(new InMemoryCheckpointRepository(), null));
    }

    @Test
    void beginAndCommit() {
        svc.begin("op1", "pending-details");
        assertEquals("pending-details", svc.pending("op1").orElseThrow());
        svc.commit("op1");
        assertTrue(svc.isCompleted("op1"));
        assertTrue(svc.pending("op1").isEmpty());
    }

    @Test
    void commitWithoutBeginThrows() {
        assertThrows(CheckpointService.CheckpointException.class, () -> svc.commit("op1"));
    }

    @Test
    void commitNonPendingThrows() {
        svc.begin("op1", "x");
        svc.commit("op1");
        // after commit, status is COMMITTED; commit again → not PENDING
        assertThrows(CheckpointService.CheckpointException.class, () -> svc.commit("op1"));
    }

    @Test
    void idempotentAlreadyCompleted() {
        svc.begin("op1", "x");
        svc.commit("op1");
        assertThrows(CheckpointService.CheckpointException.class, () -> svc.begin("op1", "y"));
    }

    @Test
    void beginAfterPendingOverwrites() {
        svc.begin("op1", "first");
        svc.begin("op1", "second");
        assertEquals("second", svc.pending("op1").orElseThrow());
    }

    @Test
    void allPendingAndClear() {
        svc.begin("a", "1");
        svc.begin("b", "2");
        assertEquals(2, svc.allPending().size());
        svc.clear("a");
        assertTrue(svc.pending("a").isEmpty());
        svc.commit("b");
        svc.clear("b");
        assertFalse(svc.isCompleted("b"));
    }

    @Test
    void runIdempotentReturnsValueOnce() {
        Integer result = svc.runIdempotent("op-run", "work", () -> 42);
        assertEquals(42, result);
        assertTrue(svc.isCompleted("op-run"));
        assertThrows(CheckpointService.CheckpointException.class,
                () -> svc.runIdempotent("op-run", "work", () -> 99));
    }

    @Test
    void runIdempotentVoidCommits() {
        int[] count = {0};
        svc.runIdempotentVoid("op-void", "work", () -> count[0]++);
        assertEquals(1, count[0]);
        assertTrue(svc.isCompleted("op-void"));
    }

    @Test
    void pendingReturnsEmptyForCommitted() {
        svc.begin("op", "d");
        svc.commit("op");
        assertTrue(svc.pending("op").isEmpty());
    }

    @Test
    void pendingReturnsEmptyForUnknown() {
        assertTrue(svc.pending("nope").isEmpty());
    }

    @Test
    void isCompletedFalseForPending() {
        svc.begin("op", "d");
        assertFalse(svc.isCompleted("op"));
    }

    @Test
    void isCompletedFalseForUnknown() {
        assertFalse(svc.isCompleted("nope"));
    }

    @Test
    void checkpointRepositoryRecordAccessors() {
        var rec = new com.fleetride.repository.CheckpointRepository.Record(
                "op", "d", com.fleetride.repository.CheckpointRepository.Status.PENDING,
                LocalDateTime.of(2026, 3, 27, 10, 0));
        assertEquals("op", rec.operationId());
        assertEquals("d", rec.detail());
        assertEquals(com.fleetride.repository.CheckpointRepository.Status.PENDING, rec.status());
        assertEquals(LocalDateTime.of(2026, 3, 27, 10, 0), rec.createdAt());
        assertEquals(2, com.fleetride.repository.CheckpointRepository.Status.values().length);
    }
}
